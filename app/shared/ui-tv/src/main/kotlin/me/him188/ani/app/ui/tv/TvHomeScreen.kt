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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
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
) {
    val vm = viewModel { ExplorationPageViewModel() }
    val followed = vm.explorationPageState.followedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()
    val trending = vm.explorationPageState.trendingSubjectInfoPager
    val recommendations = vm.explorationPageState.recommendationPager.collectAsLazyPagingItemsWithLifecycle()
    TvHomeCatalogue(followed, trending, recommendations, onSubject, onSearch, onCollections, onHistory, onSettings, onLogin, modifier)
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
) {
    val focus = rememberTvFocusState("home-awaiting-content")
    val outer = rememberLazyListState()
    val trendingRow = rememberLazyListState()
    val followedRow = rememberLazyListState()
    val recommendedRow = rememberLazyListState()
    val trendingEntries = trending.tvRailPosters { TvPoster(it.bangumiId, it.nameCn, it.imageLarge) }
    val followedEntries = followed.tvRailPosters {
        TvPoster(it.subjectInfo.subjectId, it.subjectInfo.displayName, it.subjectInfo.imageLarge, nsfwMode = it.nsfwMode)
    }
    val recommendedEntries = recommendations.tvRailPosters {
        when (it) {
            is RecommendedSubjectInfo -> TvPoster(it.bangumiId, it.nameCn, it.imageLarge)
        }
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
    val recommendedIndex = trendingIndex + 1
    TvHomeRailFocus(focus, "trending:", trendingEntries, trending, outer, trendingIndex, trendingRow)
    TvHomeRailFocus(focus, "followed:", followedEntries, followed, outer, if (hasFollowed) 0 else recommendedIndex + 1, followedRow)
    TvHomeRailFocus(
        focus, "recommended:", recommendedEntries, recommendations, outer, recommendedIndex, recommendedRow,
        refreshWhenEmpty = true, fallbackKey = "home-recommendations",
    )
    val awaitingRecommendations = recommendedEntries.isEmpty() && recommendations.loadState.refresh is LoadState.Loading
    LaunchedEffect(focus.requestedKey, awaitingRecommendations, recommendedIndex) {
        if (focus.requestedKey.startsWith("recommended:") && awaitingRecommendations) {
            outer.scrollToItem(recommendedIndex)
        }
    }
    TvCatalogueHomeLayout(
        onSearch, onCollections, onHistory, onSettings, onLogin, focus, modifier,
        onRecommendations = { focus.requestFocus("recommended:") },
    ) { compact ->
        LazyColumn(
            Modifier.fillMaxSize(), state = outer,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            if (hasFollowed) item(key = "followed:") {
                TvPosterRail("继续追番", followed, followedEntries, followedRow, focus, "followed:", onSubject, "", compact)
            }
            item(key = "trending:") {
                TvPosterRail("热门番剧", trending, trendingEntries, trendingRow, focus, "trending:", onSubject, "暂无热门番剧", compact)
            }
            item(key = "recommended:") {
                TvPosterRail(
                    "推荐番剧", recommendations, recommendedEntries, recommendedRow, focus, "recommended:",
                    onSubject, "暂无推荐番剧", compact, refreshWhenEmpty = true, loadingTitle = "正在加载推荐…",
                )
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
    onRecommendations: (() -> Unit)? = null,
    content: @Composable (compact: Boolean) -> Unit,
) {
    TvPage(title = "Animeko TV", onBack = null, modifier = modifier, focusState = focus) {
        TvCatalogueHomeNavigation(onSearch, onCollections, onHistory, onSettings, onLogin, focus, onRecommendations)
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
    onRecommendations: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val navigation = listOf(
            Triple("搜索", "home-search", onSearch),
            Triple(if (onRecommendations == null) "我的收藏" else "收藏", "home-collections", onCollections),
            Triple(if (onRecommendations == null) "观看历史" else "历史", "home-history", onHistory),
            Triple("设置", "home-settings", onSettings),
            Triple("账号", "home-login", onLogin),
        ) + if (onRecommendations != null) listOf(Triple("推荐", "home-recommendations", onRecommendations)) else emptyList()
        navigation.forEach { (label, key, action) ->
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
    refreshWhenEmpty: Boolean = false, fallbackKey: String = "home-search",
) {
    val error = pager.loadState.refresh is LoadState.Error || pager.loadState.append is LoadState.Error
    val ready = entries.isNotEmpty() || pager.loadState.refresh !is LoadState.Loading
    val keys = entries.map { "$group${it.poster.id}" } + when {
        error -> listOf("${group}retry")
        refreshWhenEmpty && ready && entries.isEmpty() -> listOf("${group}refresh")
        else -> emptyList()
    }
    val requestedId = focus.requestedKey.takeIf { it.startsWith(group) }?.removePrefix(group)?.toIntOrNull()
    val needsTargetPage = requestedId != null && entries.none { it.poster.id == requestedId } &&
        pager.itemCount > 0 && !pager.loadState.append.endOfPaginationReached && !error
    var recoveryFinished by remember(pager, requestedId, needsTargetPage) { mutableStateOf(false) }
    TvLazyFocusGroup(
        focus, group, keys, ready && (!needsTargetPage || recoveryFinished), fallbackKey,
        scrollToItem = {
            outer.scrollToItem(outerIndex)
            snapshotFlow { outer.layoutInfo.visibleItemsInfo.any { item -> item.key == group } }.first { it }
            row.scrollToItem(it)
        },
        isItemVisible = { key -> row.layoutInfo.visibleItemsInfo.any { it.key == key } },
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 22.sp)
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
