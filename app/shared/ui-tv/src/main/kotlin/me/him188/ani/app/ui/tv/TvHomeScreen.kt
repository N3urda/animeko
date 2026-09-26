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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    val outer = rememberLazyListState()
    val trendingRow = rememberLazyListState()
    val followedRow = rememberLazyListState()
    val recommendedRow = rememberLazyListState()
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
    val recommendedIndex = trendingIndex + 1
    TvHomeRailFocus(focus, "trending:", trendingEntries, trending, outer, trendingIndex, trendingRow)
    TvHomeRailFocus(focus, "followed:", followedEntries, followed, outer, if (hasFollowed) 0 else recommendedIndex + 3, followedRow)
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
    val scheduleIndex = recommendedIndex + 1
    val genresIndex = scheduleIndex + 1
    TvLazyFocusGroup(
        focus, "schedule:", scheduleEntries.map { "schedule:${it.id}" } + if (schedule.failed) listOf("schedule:retry") else emptyList(),
        !schedule.loading, "home-search",
        scrollToItem = { outer.scrollToItem(scheduleIndex); scheduleRow.scrollToItem(it) },
        isItemVisible = { key -> scheduleRow.layoutInfo.visibleItemsInfo.any { it.key == key } },
    )
    TvLazyFocusGroup(
        focus, "genre:", tvHomeGenres.map { "genre:$it" }, true, "home-search",
        scrollToItem = { outer.scrollToItem(genresIndex); genreRow.scrollToItem(it) },
        isItemVisible = { key -> genreRow.layoutInfo.visibleItemsInfo.any { it.key == key } },
    )
    TvCatalogueHomeLayout(
        onSearch, onCollections, onHistory, onSettings, onLogin, focus, modifier,
        onRecommendations = { focus.requestFocus("recommended:") },
        preview = { compact -> TvHomePreview(preview, previewSource, details, preference, compact) },
    ) { compact ->
        LazyColumn(
            Modifier.fillMaxSize(), state = outer,
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            item(key = "schedule:") {
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
            item(key = "genre:") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvHomeSectionHeading("按类型找番", "选择分类后，可继续添加设定与年份条件")
                    LazyRow(state = genreRow, contentPadding = PaddingValues(6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(tvHomeGenres, key = { "genre:$it" }) { tag ->
                            TvButton(tag, { onSearchTag(tag) }, Modifier.width(112.dp).tvFocusTarget("genre:$tag", focus).testTag("tv-home-genre-$tag"))
                        }
                    }
                }
            }
            if (!hasFollowed && followedError) item(key = "followed:") {
                TvPosterRail("追番内容加载失败", followed, followedEntries, followedRow, focus, "followed:", onSubject, "", compact)
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
    preview: @Composable (Boolean) -> Unit = {},
    content: @Composable (compact: Boolean) -> Unit,
) {
    CompositionLocalProvider(LocalTvFocusState provides focus) {
        TvRestorePageFocus(focus)
        BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val compact = maxHeight < 460.dp
            Column(
                Modifier.fillMaxSize().onPreviewKeyEvent { focus.onNavigationKey(it); false }
                    .padding(horizontal = if (maxWidth < 800.dp) 28.dp else 40.dp, vertical = if (compact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Animeko", Modifier.width(if (compact) 84.dp else 100.dp), fontSize = if (compact) 20.sp else 24.sp,
                        color = MaterialTheme.colorScheme.primary)
                    TvCatalogueHomeNavigation(onSearch, onCollections, onHistory, onSettings, onLogin, focus, onRecommendations)
                }
                preview(compact)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("tv-home-content-viewport")) { content(compact) }
            }
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    outer: LazyListState, outerIndex: Int, row: LazyListState,
    refreshWhenEmpty: Boolean = false, fallbackKey: String = "home-search",
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
