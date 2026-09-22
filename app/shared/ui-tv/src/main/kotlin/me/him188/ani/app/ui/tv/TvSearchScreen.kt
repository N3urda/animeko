/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.ui.exploration.search.SearchPageIntent
import me.him188.ani.app.ui.exploration.search.SubjectPreviewItemInfo
import me.him188.ani.app.ui.main.SearchViewModel
import me.him188.ani.app.ui.search.collectItemsWithLifecycle

@Composable
fun TvSearchScreen(
    onSubject: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialKeyword: String? = null,
    initialTags: List<String>? = null,
) {
    val vm = viewModel(key = "tv-search-${initialKeyword.orEmpty()}-${initialTags.orEmpty()}") {
        SearchViewModel(
            SubjectSearchQuery(keywords = initialKeyword.orEmpty(), tags = initialTags),
            pagingConfig = tvCataloguePagingConfig,
            includePreviewDetails = false,
        )
    }
    val state by vm.searchPageState.collectAsStateWithLifecycle()
    val results = state.searchState.collectItemsWithLifecycle()
    val text = rememberTextFieldState(initialKeyword.orEmpty())
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = rememberTvFocusState("search-input")
    val pager by state.searchState.pagerFlow.collectAsStateWithLifecycle()
    var submittedPager by remember { mutableStateOf<Any?>(null) }
    var awaitingSearch by remember { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    fun submit(query: SubjectSearchQuery) {
        keyboard?.hide()
        submittedPager = pager
        awaitingSearch = tvSearchHasConstraints(query)
        focus.requestFocus(if (awaitingSearch) "search-result:" else "search-input")
        vm.onSearchPageIntent(SearchPageIntent.UpdateQuery(query, submit = true))
    }
    fun search() = submit(state.query.copy(keywords = text.text.toString().trim()))
    LaunchedEffect(vm) { vm.onSearchPageIntent(SearchPageIntent.StartInitialSearch) }
    val resultReady = pager != null && (!awaitingSearch || pager !== submittedPager) &&
        results.loadState.refresh !is LoadState.Loading
    LaunchedEffect(resultReady) { if (resultReady) awaitingSearch = false }
    TvPage("搜索番剧", onBack, modifier, focusState = focus) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TvOutlinedTextField(
                state = text,
                modifier = Modifier.weight(1f).tvFocusTarget("search-input", focus).testTag("tv-search-input"),
                label = { Text("番剧名称", fontSize = 18.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onKeyboardAction = { search() },
            )
            TvButton("搜索", ::search, Modifier.tvFocusTarget("search-submit", focus).testTag("tv-search-submit"), enabled = tvSearchHasConstraints(state.query.copy(keywords = text.text.toString())))
            TvButton("筛选 · 类型 / 设定", { keyboard?.hide(); showFilters = true },
                Modifier.tvFocusTarget("search-filters", focus).testTag("tv-search-filters"))
        }
        if (state.query.hasFilters()) Text(tvSearchFilterSummary(state.query), fontSize = 14.sp, maxLines = 1)
        if (state.hasActiveSearch && tvSearchHasConstraints(state.query)) {
            if (awaitingSearch && pager === submittedPager) {
                TvStaticFocusGroup(focus, "search-result:", emptyList(), ready = false, fallbackKey = "search-input")
                TvMessage("正在搜索…", "可返回输入框修改关键词。")
            } else {
                TvCatalogueSearchResults(results, resultReady, focus, onSubject)
            }
        } else {
            TvMessage("输入名称，或按类型和设定筛选", "可不填名称，直接选择筛选条件并应用。")
        }
    }
    if (showFilters) TvSearchFilterDialog(
        initialQuery = state.query.copy(keywords = text.text.toString().trim()),
        seasons = state.seasons,
        onApply = { showFilters = false; submit(it) },
        onDismiss = { showFilters = false; focus.requestFocus("search-filters") },
    )
}

@Composable
internal fun TvCatalogueSearchResults(
    results: LazyPagingItems<SubjectPreviewItemInfo>,
    ready: Boolean,
    focus: TvFocusState,
    onSubject: (Int) -> Unit,
) {
    val empty = ready && results.itemCount == 0 && results.loadState.refresh !is LoadState.Error
    if (empty) {
        TvStaticFocusGroup(focus, "search-result:", listOf("search-result:edit"), fallbackKey = "search-input")
        TvMessage(
            "没有找到匹配的番剧", "试试番剧简称、原名或更少的关键词。", "修改关键词",
            onAction = { focus.requestFocus("search-input") },
            actionModifier = Modifier.tvFocusTarget("search-result:edit", focus),
        )
    } else {
        TvPosterGrid(
            results,
            key = { it.subjectId },
            poster = {
                TvPoster(
                    it.subjectId,
                    it.title,
                    it.imageUrl,
                    if (it.rating.total > 0) "评分 ${it.rating.score}" else "暂无评分",
                    nsfwMode = if (it.hide) NsfwMode.HIDE else it.nsfwMode,
                )
            },
            onSubject = onSubject,
            emptyTitle = "没有可显示的番剧，请修改关键词或检查内容偏好",
            focusState = focus,
            focusGroup = "search-result:",
            fallbackKey = "search-input",
            ready = ready,
        )
    }
}
