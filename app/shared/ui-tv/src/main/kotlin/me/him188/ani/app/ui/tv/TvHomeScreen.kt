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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.models.preference.NsfwMode
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
) {
    val vm = viewModel { ExplorationPageViewModel() }
    val followed = vm.explorationPageState.followedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()
    val trending = vm.explorationPageState.trendingSubjectInfoPager
    val focus = rememberTvFocusState("home-awaiting-content")
    val outer = rememberLazyListState()
    val trendingRow = rememberLazyListState()
    val followedRow = rememberLazyListState()
    val trendingEntries = trending.tvRailPosters { TvPoster(it.bangumiId, it.nameCn, it.imageLarge) }
    val followedEntries = followed.tvRailPosters {
        TvPoster(it.subjectInfo.subjectId, it.subjectInfo.displayName, it.subjectInfo.imageLarge, nsfwMode = it.nsfwMode)
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
    TvHomeRailFocus(focus, "trending:", trendingEntries, trending, outer, if (hasFollowed) 1 else 0, trendingRow)
    TvHomeRailFocus(focus, "followed:", followedEntries, followed, outer, if (hasFollowed) 0 else 1, followedRow)
    TvCatalogueHomeLayout(onSearch, onCollections, onHistory, onSettings, onLogin, focus, modifier) { compact ->
        LazyColumn(
            Modifier.fillMaxSize().focusRestorer(), state = outer,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            if (hasFollowed) item(key = "followed:") {
                TvPosterRail("继续追番", followed, followedEntries, followedRow, focus, "followed:", onSubject, "", compact)
            }
            item(key = "trending:") {
                TvPosterRail("热门番剧", trending, trendingEntries, trendingRow, focus, "trending:", onSubject, "暂无热门番剧", compact)
            }
            if (!hasFollowed && followedError) item(key = "followed:") {
                TvPosterRail("追番内容加载失败", followed, followedEntries, followedRow, focus, "followed:", onSubject, "", compact)
            }
        }
    }
}

/** 导航下方的实际可用高度决定海报布局, 让当前卡片和标题完整可见. */
@Composable
internal fun TvCatalogueHomeLayout(
    onSearch: () -> Unit,
    onCollections: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    focus: TvFocusState,
    modifier: Modifier = Modifier,
    content: @Composable (compact: Boolean) -> Unit,
) {
    TvPage(title = "Animeko TV", onBack = null, modifier = modifier, focusState = focus) {
        TvCatalogueHomeNavigation(onSearch, onCollections, onHistory, onSettings, onLogin, focus)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("tv-home-content-viewport")) {
            content(maxHeight < 350.dp)
        }
    }
}

@Composable
internal fun TvCatalogueHomeNavigation(
    onSearch: () -> Unit,
    onCollections: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    focus: TvFocusState,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(
            Triple("搜索", "home-search", onSearch),
            Triple("我的收藏", "home-collections", onCollections),
            Triple("观看历史", "home-history", onHistory),
            Triple("设置", "home-settings", onSettings),
            Triple("账号", "home-login", onLogin),
        ).forEach { (label, key, action) ->
            TvButton(label, action, Modifier.weight(1f).tvFocusTarget(key, focus).testTag(key))
        }
    }
}

private data class TvRailPoster(val pagingIndex: Int, val poster: TvPoster)

private fun <T : Any> LazyPagingItems<T>.tvRailPosters(poster: (T) -> TvPoster): List<TvRailPoster> =
    itemSnapshotList.items.mapIndexedNotNull { index, item ->
        val display = poster(item)
        if (display.nsfwMode == NsfwMode.HIDE) null
        else TvRailPoster(itemSnapshotList.placeholdersBefore + index, display)
    }

@Composable
private fun <T : Any> TvHomeRailFocus(
    focus: TvFocusState, group: String, entries: List<TvRailPoster>, pager: LazyPagingItems<T>,
    outer: LazyListState, outerIndex: Int, row: LazyListState,
) {
    val error = pager.loadState.refresh is LoadState.Error || pager.loadState.append is LoadState.Error
    val keys = entries.map { "$group${it.poster.id}" } + if (error) listOf("${group}retry") else emptyList()
    TvLazyFocusGroup(
        focus, group, keys, pager.loadState.refresh !is LoadState.Loading, "home-search",
        scrollToItem = {
            outer.scrollToItem(outerIndex)
            snapshotFlow { outer.layoutInfo.visibleItemsInfo.any { item -> item.key == group } }.first { it }
            row.scrollToItem(it)
        },
        isItemVisible = { key -> row.layoutInfo.visibleItemsInfo.any { it.key == key } },
    )
}

@Composable
private fun <T : Any> TvPosterRail(
    title: String, pager: LazyPagingItems<T>, entries: List<TvRailPoster>, row: LazyListState,
    focus: TvFocusState, group: String, onSubject: (Int) -> Unit, emptyTitle: String, compact: Boolean,
) {
    val error = pager.loadState.refresh is LoadState.Error || pager.loadState.append is LoadState.Error
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 22.sp)
        if (entries.isEmpty() && !error) {
            TvMessage(if (pager.loadState.refresh is LoadState.Loading) "正在加载…" else emptyTitle)
        }
        LazyRow(
            Modifier.fillMaxWidth().focusRestorer(), state = row,
            contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(entries, key = { "$group${it.poster.id}" }) { entry ->
                pager[entry.pagingIndex]
                TvPosterCard(
                    entry.poster, { onSubject(entry.poster.id) },
                    Modifier.tvFocusTarget("$group${entry.poster.id}", focus), compact = compact,
                )
            }
            if (error) item(key = "${group}retry") {
                TvButton("重试", { focus.requestFocus(group); pager.retry() }, Modifier.tvFocusTarget("${group}retry", focus))
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
