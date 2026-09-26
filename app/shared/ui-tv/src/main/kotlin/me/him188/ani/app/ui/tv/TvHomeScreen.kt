/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.ContinueWatchingStatus
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.ui.main.ExplorationPageViewModel

@Composable
fun TvHomeScreen(
    onSubject: (Int) -> Unit,
    onSearch: () -> Unit,
    onCollections: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchTag: (String) -> Unit = {},
) {
    val vm = viewModel { ExplorationPageViewModel(recommendationPagingConfig = tvCataloguePagingConfig) }
    val followed = vm.explorationPageState.followedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()
    val trending = vm.explorationPageState.trendingSubjectInfoPager
    val recommendations = vm.explorationPageState.recommendationPager.collectAsLazyPagingItemsWithLifecycle()
    val discovery = viewModel { TvHomeDiscoveryViewModel() }
    val details by discovery.details.collectAsStateWithLifecycle()
    val schedule by discovery.schedule.collectAsStateWithLifecycle()
    val preference = rememberTvNsfwMode()
    LifecycleResumeEffect(discovery) {
        discovery.refreshDate()
        onPauseOrDispose { }
    }
    TvHomeCatalogue(
        followed, trending, recommendations, onSubject, onSearch, onCollections, onHistory, onSettings, onLogin, modifier,
        details, schedule, preference, discovery::selectSubject, discovery::retrySchedule, onSearchTag,
    )
}

@Composable
internal fun TvHomeCatalogue(
    followed: LazyPagingItems<FollowedSubjectInfo>,
    trending: LazyPagingItems<TrendingSubjectInfo>,
    recommendations: LazyPagingItems<RecommendedItemInfo>,
    onSubject: (Int) -> Unit,
    onSearch: () -> Unit,
    onCollections: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    details: TvHomeSubjectDetails = TvHomeSubjectDetails(),
    schedule: TvHomeScheduleState = TvHomeScheduleState(loading = false),
    preference: NsfwMode = NsfwMode.DISPLAY,
    onPreviewSubject: (Int?) -> Unit = {},
    onRetrySchedule: () -> Unit = {},
    onSearchTag: (String) -> Unit = {},
) {
    val focus = rememberTvFocusState("home-awaiting-content")
    val outer = rememberLazyGridState()
    val trendingRow = rememberLazyListState()
    val followedRow = rememberLazyListState()
    val scheduleRow = rememberLazyListState()
    val genreRow = rememberLazyListState()
    var classifications by rememberSaveable { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }
    LaunchedEffect(details.info) {
        details.info?.let { classifications = classifications + (it.subjectId to it.nsfw) }
    }
    val trendingMapper = remember(classifications, preference) {
        { item: TrendingSubjectInfo ->
            TvPoster(item.bangumiId, item.nameCn, item.imageLarge, "热门番剧",
                nsfwMode = if (classifications[item.bangumiId] == true) preference else NsfwMode.DISPLAY)
        }
    }
    val trendingEntries = trending.tvRailPosters(trendingMapper)
    val followedEntries = followed.tvRailPosters {
        TvPoster(it.subjectInfo.subjectId, it.subjectInfo.displayName, it.subjectInfo.imageLarge, tvHomeProgressLabel(it.subjectProgressInfo.continueWatchingStatus), nsfwMode = it.nsfwMode)
    }
    val recommendedMapper = remember(classifications, preference) {
        { item: RecommendedItemInfo ->
            when (item) {
                is RecommendedSubjectInfo -> TvPoster(item.bangumiId, item.nameCn, item.imageLarge, "发现好番",
                    nsfwMode = if (classifications[item.bangumiId] == true) preference else NsfwMode.DISPLAY)
            }
        }
    }
    val recommendedEntries = recommendations.tvRailPosters(recommendedMapper)
    val scheduleEntries = remember(schedule.items, classifications, preference) {
        schedule.items.mapNotNull {
            val mode = when {
                classifications[it.subjectId] == true -> preference
                classifications[it.subjectId] == null && preference != NsfwMode.DISPLAY -> NsfwMode.BLUR
                else -> NsfwMode.DISPLAY
            }
            if (mode == NsfwMode.HIDE) null else TvPoster(it.subjectId, it.title, it.imageUrl, it.subtitle, mode)
        }
    }
    val previewCandidates = remember(followedEntries, trendingEntries, recommendedEntries, scheduleEntries) {
        followedEntries.map { "followed:${it.poster.id}" to it.poster } +
            trendingEntries.map { "trending:${it.poster.id}" to it.poster } +
            recommendedEntries.map { "recommended:${it.poster.id}" to it.poster } +
            scheduleEntries.map { "schedule:${it.id}" to it }
    }
    var previewKey by rememberSaveable { mutableStateOf<String?>(null) }
    val current = previewCandidates.firstOrNull { it.first == focus.requestedKey }
        ?: previewCandidates.firstOrNull { it.first == previewKey } ?: previewCandidates.firstOrNull()
    LaunchedEffect(current?.first) { previewKey = current?.first }
    val preview = current?.second
    LaunchedEffect(preview?.id, onPreviewSubject) { onPreviewSubject(preview?.id) }
    val previewSource = when (current?.first?.substringBefore(':')) {
        "followed" -> "继续追番  ·  ${preview?.subtitle.orEmpty()}"
        "trending" -> "热门番剧  ·  大家正在关注"
        "recommended" -> "推荐番剧  ·  发现更多故事"
        "schedule" -> "今日更新  ·  ${preview?.subtitle.orEmpty()}"
        else -> ""
    }
    TvSetHomeInitialFocus(
        focus,
        followedEntries.firstOrNull()?.poster?.id?.let { "followed:$it" },
        trendingEntries.firstOrNull()?.poster?.id?.let { "trending:$it" },
        followed.loadState.refresh !is LoadState.Loading,
        trending.loadState.refresh !is LoadState.Loading,
    )
    val hasFollowed = followedEntries.isNotEmpty()
    val followedError = followed.loadState.refresh is LoadState.Error || followed.loadState.append is LoadState.Error
    val trendingIndex = if (hasFollowed) 1 else 0
    val scheduleIndex = trendingIndex + 1
    val genresIndex = scheduleIndex + 1
    val followedErrorIndex = genresIndex + 1
    val recommendedHeadingIndex = followedErrorIndex + if (!hasFollowed && followedError) 1 else 0
    val recommendedStartIndex = recommendedHeadingIndex + 1
    val overviewGroups = buildList {
        if (hasFollowed) add("followed:")
        addAll(listOf("trending:", "schedule:", "genre:"))
        if (!hasFollowed && followedError) add("followed:")
    }
    val recommendationPreviousKey = if (!hasFollowed && followedError) "followed:retry" else "genre:${tvHomeGenres.first()}"
    TvHomeRailFocus(focus, "trending:", trendingEntries, trending, outer, trendingIndex, trendingRow)
    TvHomeRailFocus(focus, "followed:", followedEntries, followed, outer, if (hasFollowed) 0 else followedErrorIndex, followedRow)
    val recommendationError = recommendations.loadState.refresh is LoadState.Error || recommendations.loadState.append is LoadState.Error
    val recommendationLoading = recommendations.loadState.refresh is LoadState.Loading
    TvHomePagingFocus(
        focus, "recommended:", recommendedEntries, recommendations,
        refreshWhenEmpty = true, fallbackKey = "home-recommendations",
        scrollToItem = { outer.scrollToItem(recommendedStartIndex + it) },
        isItemVisible = { key -> outer.layoutInfo.visibleItemsInfo.any { it.key == key } },
    )
    LaunchedEffect(focus.requestedKey, recommendationLoading, recommendedHeadingIndex) {
        if (focus.requestedKey.startsWith("recommended:") && recommendedEntries.isEmpty() && recommendationLoading) {
            outer.scrollToItem(recommendedHeadingIndex)
        }
    }
    TvLazyFocusGroup(
        focus, "schedule:", scheduleEntries.map { "schedule:${it.id}" } + if (schedule.failed) listOf("schedule:retry") else emptyList(),
        !schedule.loading, "home-overview",
        scrollToItem = { outer.scrollToItem(scheduleIndex); scheduleRow.scrollToItem(it) },
        isItemVisible = { key -> scheduleRow.layoutInfo.visibleItemsInfo.any { it.key == key } },
    )
    TvLazyFocusGroup(
        focus, "genre:", tvHomeGenres.map { "genre:$it" }, true, "home-overview",
        scrollToItem = { outer.scrollToItem(genresIndex); genreRow.scrollToItem(it) },
        isItemVisible = { key -> genreRow.layoutInfo.visibleItemsInfo.any { it.key == key } },
    )
    val overviewKey = followedEntries.firstOrNull()?.poster?.id?.let { "followed:$it" }
        ?: trendingEntries.firstOrNull()?.poster?.id?.let { "trending:$it" }
        ?: scheduleEntries.firstOrNull()?.id?.let { "schedule:$it" } ?: "genre:${tvHomeGenres.first()}"
    TvCatalogueHomeLayout(
        onSearch, onCollections, onHistory, onSettings, onLogin, focus, modifier,
        onOverview = { focus.requestFocus(overviewKey) },
        onRecommendations = { focus.requestFocus("recommended:") },
        backdrop = { TvHomeBackdrop(preview, details, preference, Modifier.fillMaxSize()) },
        preview = { compact -> TvHomePreview(preview, previewSource, details, preference, compact) },
    ) { compact ->
        val columns = if (compact) 3 else 4
        var pendingDownFrom by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(pendingDownFrom, recommendedEntries, recommendationError, focus.requestedKey,
            recommendations.loadState.append.endOfPaginationReached, columns) {
            val origin = pendingDownFrom ?: return@LaunchedEffect
            if (focus.requestedKey != "recommended:$origin") {
                pendingDownFrom = null
                return@LaunchedEffect
            }
            val index = recommendedEntries.indexOfFirst { it.poster.id == origin }
            if (index < 0) {
                pendingDownFrom = null
                return@LaunchedEffect
            }
            val next = recommendedEntries.getOrNull(index + columns)
                ?: recommendedEntries.lastOrNull()?.takeIf { index / columns < recommendedEntries.lastIndex / columns }
            when {
                next != null -> { pendingDownFrom = null; focus.requestFocus("recommended:${next.poster.id}") }
                recommendationError -> { pendingDownFrom = null; focus.requestFocus("recommended:retry") }
                recommendations.loadState.append.endOfPaginationReached -> pendingDownFrom = null
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().testTag("tv-home-grid").onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || focus.requestedKey.startsWith("recommended:")) false
                else tvHomeOverviewNavigation(focus, event.key, overviewGroups)
            },
            state = outer,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 20.dp),
        ) {
            if (hasFollowed) item(key = "followed:", span = { GridItemSpan(maxLineSpan) }) {
                TvPosterRail("继续追番", followed, followedEntries, followedRow, focus, "followed:", onSubject, "", compact)
            }
            item(key = "trending:", span = { GridItemSpan(maxLineSpan) }) {
                TvPosterRail("热门番剧", trending, trendingEntries, trendingRow, focus, "trending:", onSubject, "暂无热门番剧", compact)
            }
            item(key = "schedule:", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvHomeSectionHeading("今日更新", "播出时间表 · 以实际片源为准")
                    if (scheduleEntries.isEmpty()) Text(
                        when { schedule.failed -> "时间表加载失败，请重试。"; schedule.loading -> "正在加载今日更新…"; else -> "今天暂无更新安排" },
                        fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(state = scheduleRow, contentPadding = PaddingValues(6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(scheduleEntries, key = { "schedule:${it.id}" }) { poster ->
                            TvHomePosterCard(
                                poster, { onSubject(poster.id) }, Modifier.tvFocusTarget("schedule:${poster.id}", focus), compact,
                                classificationPending = classifications[poster.id] == null && preference != NsfwMode.DISPLAY,
                                onCheckContent = { onSubject(poster.id) },
                            )
                        }
                        if (schedule.failed) item(key = "schedule:retry") {
                            TvButton("重试时间表", { focus.requestFocus("schedule:"); onRetrySchedule() },
                                Modifier.tvFocusTarget("schedule:retry", focus).testTag("tv-home-schedule-retry"))
                        }
                    }
                }
            }
            item(key = "genre:", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvHomeSectionHeading("按类型找番", "选择分类后，可继续添加设定与年份条件")
                    LazyRow(state = genreRow, contentPadding = PaddingValues(6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(tvHomeGenres, key = { "genre:$it" }) { tag ->
                            TvButton(tag, { onSearchTag(tag) }, Modifier.width(112.dp).tvFocusTarget("genre:$tag", focus).testTag("tv-home-genre-$tag"))
                        }
                    }
                }
            }
            if (!hasFollowed && followedError) item(key = "followed:", span = { GridItemSpan(maxLineSpan) }) {
                TvPosterRail("追番内容加载失败", followed, followedEntries, followedRow, focus, "followed:", onSubject, "", compact)
            }
            item(key = "recommended-heading", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvHomeSectionHeading("推荐番剧", "上下浏览更多番剧")
                    if (recommendedEntries.isEmpty()) Text(
                        when {
                            recommendationError -> "加载失败，请检查网络后重试。"
                            recommendationLoading -> "正在加载推荐…"
                            else -> "暂无推荐番剧"
                        }, fontSize = 20.sp,
                    )
                }
            }
            itemsIndexed(recommendedEntries, key = { _, entry -> "recommended:${entry.poster.id}" }) { index, entry ->
                recommendations[entry.pagingIndex]
                TvHomePosterCard(
                    entry.poster, { onSubject(entry.poster.id) },
                    Modifier.fillMaxWidth().tvFocusTarget("recommended:${entry.poster.id}", focus)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) false else {
                                val target = when (event.key) {
                                    Key.DirectionLeft -> if (index % columns == 0) "home-recommendations" else "recommended:${recommendedEntries[index - 1].poster.id}"
                                    Key.DirectionRight -> if (index % columns < columns - 1) recommendedEntries.getOrNull(index + 1)?.let { "recommended:${it.poster.id}" } else null
                                    Key.DirectionUp -> if (index >= columns) "recommended:${recommendedEntries[index - columns].poster.id}" else recommendationPreviousKey
                                    Key.DirectionDown -> when {
                                        index + columns < recommendedEntries.size -> "recommended:${recommendedEntries[index + columns].poster.id}"
                                        index / columns < recommendedEntries.lastIndex / columns -> "recommended:${recommendedEntries.last().poster.id}"
                                        recommendationError -> "recommended:retry"
                                        else -> {
                                            if (!recommendations.loadState.append.endOfPaginationReached) {
                                                pendingDownFrom = entry.poster.id
                                                recommendations[recommendedEntries.last().pagingIndex]
                                            }
                                            null
                                        }
                                    }
                                    else -> return@onPreviewKeyEvent false
                                }
                                if (target != null) focus.requestFocus(target)
                                true
                            }
                        }, compact = compact,
                )
            }
            if (recommendationError) item(key = "recommended:retry", span = { GridItemSpan(maxLineSpan) }) {
                TvButton("重试", {
                    val target = recommendedEntries.lastOrNull()?.poster?.id?.let { "recommended:$it" } ?: "recommended:"
                    focus.requestFocus(target)
                    if (recommendedEntries.isNotEmpty()) focus.nodes[target]?.requestFocus()
                    recommendations.retry()
                }, Modifier.tvFocusTarget("recommended:retry", focus).testTag("tv-home-recommended-retry")
                    .tvHomeRecommendationFooterKeys(focus, recommendedEntries, recommendationPreviousKey))
            } else if (recommendedEntries.isEmpty() && !recommendationLoading) item(key = "recommended:refresh", span = { GridItemSpan(maxLineSpan) }) {
                TvButton("刷新推荐", { focus.requestFocus("recommended:"); recommendations.refresh() },
                    Modifier.tvFocusTarget("recommended:refresh", focus).testTag("tv-home-recommended-refresh")
                        .tvHomeRecommendationFooterKeys(focus, recommendedEntries, recommendationPreviousKey))
            }
            if (recommendations.loadState.append is LoadState.Loading && recommendedEntries.isNotEmpty()) {
                item(key = "recommended:loading", span = { GridItemSpan(maxLineSpan) }) { Text("正在加载更多…", fontSize = 20.sp) }
            }
        }
    }
}

/** 首页的可用高度决定介绍区与卡片尺寸. */
@Composable
internal fun TvCatalogueHomeLayout(
    onSearch: () -> Unit,
    onCollections: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    focus: TvFocusState,
    modifier: Modifier = Modifier,
    onRecommendations: (() -> Unit)? = null,
    onOverview: (() -> Unit)? = null,
    backdrop: @Composable () -> Unit = {},
    preview: @Composable (Boolean) -> Unit = {},
    content: @Composable (compact: Boolean) -> Unit,
) {
    val shell = LocalTvShell.current
    if (shell == null) {
        val standaloneShell = remember { TvAppShellState() }
        TvAppShell(
            selectedKey = standaloneShell.homeAnchor,
            onSelect = { key ->
                when (key) {
                    "home-overview", "home-recommendations" -> standaloneShell.openHomeAnchor(key)
                    "home-search" -> onSearch()
                    "home-collections" -> onCollections()
                    "home-history" -> onHistory()
                    "home-settings" -> onSettings()
                    "home-login" -> onLogin()
                }
            }, modifier = modifier, state = standaloneShell,
        ) {
            TvCatalogueHomeBody(focus, onOverview, onRecommendations, backdrop, preview, content)
        }
    } else {
        TvCatalogueHomeBody(focus, onOverview, onRecommendations, backdrop, preview, content, modifier)
    }
}

@Composable
private fun TvCatalogueHomeBody(
    focus: TvFocusState,
    onOverview: (() -> Unit)?,
    onRecommendations: (() -> Unit)?,
    backdrop: @Composable () -> Unit,
    preview: @Composable (Boolean) -> Unit,
    content: @Composable (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalTvFocusState provides focus) {
        TvBindShellPage(focus, onOverview, onRecommendations)
        TvRestorePageFocus(focus)
        BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val compact = maxHeight < 460.dp
            backdrop()
            Column(
                Modifier.fillMaxSize().onPreviewKeyEvent { focus.onNavigationKey(it); false }
                    .padding(start = if (compact) 12.dp else 16.dp, end = if (compact) 16.dp else 24.dp,
                        top = if (compact) 12.dp else 16.dp, bottom = if (compact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                preview(compact)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("tv-home-content-viewport")) { content(compact) }
            }
        }
    }
}

internal data class TvRailPoster(val pagingIndex: Int, val poster: TvPoster)

@Composable
internal fun <T : Any> LazyPagingItems<T>.tvRailPosters(poster: (T) -> TvPoster): List<TvRailPoster> {
    val snapshot = itemSnapshotList
    return remember(snapshot, poster) {
        snapshot.items.mapIndexedNotNull { index, item ->
            val display = poster(item)
            if (display.nsfwMode == NsfwMode.HIDE) null
            else TvRailPoster(snapshot.placeholdersBefore + index, display)
        }
    }
}

@Composable
private fun <T : Any> TvHomeRailFocus(
    focus: TvFocusState, group: String, entries: List<TvRailPoster>, pager: LazyPagingItems<T>,
    outer: LazyGridState, outerIndex: Int, row: LazyListState,
    refreshWhenEmpty: Boolean = false, fallbackKey: String = "home-search",
) {
    TvHomePagingFocus(
        focus, group, entries, pager, refreshWhenEmpty, fallbackKey,
        scrollToItem = {
            outer.scrollToItem(outerIndex)
            snapshotFlow { outer.layoutInfo.visibleItemsInfo.any { item -> item.key == group } }.first { it }
            row.scrollToItem(it)
        },
        isItemVisible = { key -> row.layoutInfo.visibleItemsInfo.any { it.key == key } },
    )
}

@Composable
private fun <T : Any> TvHomePagingFocus(
    focus: TvFocusState, group: String, entries: List<TvRailPoster>, pager: LazyPagingItems<T>,
    refreshWhenEmpty: Boolean = false, fallbackKey: String = "home-search",
    scrollToItem: suspend (Int) -> Unit,
    isItemVisible: (String) -> Boolean,
) {
    val error = pager.loadState.refresh is LoadState.Error || pager.loadState.append is LoadState.Error
    val ready = entries.isNotEmpty() || pager.loadState.refresh !is LoadState.Loading
    val keys = remember(entries, group, error, refreshWhenEmpty, ready) {
        entries.map { "$group${it.poster.id}" } + when {
            error -> listOf("${group}retry")
            refreshWhenEmpty && ready && entries.isEmpty() -> listOf("${group}refresh")
            else -> emptyList()
        }
    }
    val requestedId = focus.requestedKey.takeIf { it.startsWith(group) }?.removePrefix(group)?.toIntOrNull()
    val needsTargetPage = requestedId != null && entries.none { it.poster.id == requestedId } &&
        pager.itemCount > 0 && !pager.loadState.append.endOfPaginationReached && !error
    var recoveryFinished by remember(pager, requestedId, needsTargetPage) { mutableStateOf(false) }
    TvLazyFocusGroup(
        focus, group, keys, ready && (!needsTargetPage || recoveryFinished), fallbackKey,
        scrollToItem = scrollToItem,
        isItemVisible = isItemVisible,
    )
    LaunchedEffect(pager, requestedId, needsTargetPage) {
        if (needsTargetPage) {
            val budget = TvPagingFocusBudget()
            withTimeoutOrNull(3_000) {
                snapshotFlow {
                    TvPagingFocusSnapshot(
                        itemCount = pager.itemCount,
                        targetPresent = entries.any { it.poster.id == requestedId },
                        appendLoading = pager.loadState.append is LoadState.Loading,
                        hasMore = !pager.loadState.append.endOfPaginationReached,
                        hasError = pager.loadState.append is LoadState.Error || pager.loadState.refresh is LoadState.Error,
                    )
                }.first { page ->
                    when (budget.next(page)) {
                        TvPagingFocusAction.FOUND, TvPagingFocusAction.FALLBACK -> true
                        TvPagingFocusAction.REQUEST_PAGE -> { pager[page.itemCount - 1]; false }
                        TvPagingFocusAction.WAIT -> false
                    }
                }
            }
        }
        recoveryFinished = true
    }
}

@Composable
private fun <T : Any> TvPosterRail(
    title: String, pager: LazyPagingItems<T>, entries: List<TvRailPoster>, row: LazyListState,
    focus: TvFocusState, group: String, onSubject: (Int) -> Unit, emptyTitle: String, compact: Boolean,
    refreshWhenEmpty: Boolean = false, loadingTitle: String = "正在加载…",
) {
    val error = pager.loadState.refresh is LoadState.Error || pager.loadState.append is LoadState.Error
    val loading = pager.loadState.refresh is LoadState.Loading
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TvHomeSectionHeading(title, when (group) {
            "followed:" -> "收藏与观看进度"
            "trending:" -> "当前热门 · 左右查看更多"
            else -> "发现更多喜欢的番剧"
        })
        if (entries.isEmpty()) {
            Text(
                when {
                    error -> "加载失败，请检查网络后重试。"
                    loading -> loadingTitle
                    else -> emptyTitle
                },
                fontSize = 20.sp,
            )
        }
        LazyRow(
            Modifier.fillMaxWidth(), state = row,
            contentPadding = PaddingValues(6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { "$group${it.poster.id}" }) { entry ->
                pager[entry.pagingIndex]
                TvHomePosterCard(
                    entry.poster, { onSubject(entry.poster.id) },
                    Modifier.tvFocusTarget("$group${entry.poster.id}", focus), compact = compact,
                )
            }
            if (error) item(key = "${group}retry") {
                TvButton(
                    "重试", {
                        val target = entries.lastOrNull()?.poster?.id?.let { "$group$it" } ?: group
                        focus.requestFocus(target)
                        // 先交接实际焦点, 让重试节点在加载状态更新时可以安全移出布局.
                        if (entries.isNotEmpty()) focus.nodes[target]?.requestFocus()
                        pager.retry()
                    },
                    Modifier.tvFocusTarget("${group}retry", focus).testTag("tv-home-${group.removeSuffix(":")}-retry"),
                )
            } else if (refreshWhenEmpty && entries.isEmpty() && !loading) item(key = "${group}refresh") {
                TvButton(
                    "刷新推荐", { focus.requestFocus(group); pager.refresh() },
                    Modifier.tvFocusTarget("${group}refresh", focus).testTag("tv-home-recommended-refresh"),
                )
            }
            if (pager.loadState.append is LoadState.Loading && entries.isNotEmpty()) item(key = "${group}loading") {
                Text("正在加载更多…", fontSize = 20.sp)
            }
        }
    }
}


@Composable
internal fun TvSetHomeInitialFocus(
    focus: TvFocusState,
    followedKey: String?,
    trendingKey: String?,
    followedReady: Boolean,
    trendingReady: Boolean,
) {
    val target = tvHomeInitialFocus(followedKey, trendingKey, followedReady, trendingReady)
    LaunchedEffect(target) { focus.requestInitialFocus(target ?: "home-search", provisional = target == null) }
}

internal val tvHomeGenres = listOf("科幻", "喜剧", "恋爱", "奇幻", "悬疑", "冒险", "日常", "运动", "音乐", "历史")

internal fun tvHomeProgressLabel(status: ContinueWatchingStatus): String = when (status) {
    ContinueWatchingStatus.Start -> "开始观看"
    ContinueWatchingStatus.Done -> "已看完"
    is ContinueWatchingStatus.NotOnAir -> "尚未开播"
    is ContinueWatchingStatus.Continue -> "继续第 ${status.episodeEp ?: status.episodeSort ?: "?"} 话"
    is ContinueWatchingStatus.Watched -> "已看第 ${status.episodeEp ?: status.episodeSort ?: "?"} 话"
}

@Composable
private fun TvHomeSectionHeading(title: String, caption: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, fontSize = 20.sp, lineHeight = 25.sp)
        Text(caption, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
