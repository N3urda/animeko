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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import me.him188.ani.app.ui.subject.details.SubjectDetailsViewModel
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

@Composable
fun TvSubjectScreen(
    subjectId: Int,
    onEpisode: (Int, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = viewModel(key = "tv-subject-$subjectId") { SubjectDetailsViewModel(subjectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val auth by vm.authState.collectAsStateWithLifecycle()
    val focus = rememberTvFocusState("subject:")
    LaunchedEffect(vm) { if (vm.state.value == null) vm.reload() }
    TvPage("番剧详情", onBack, modifier, focusState = focus) {
        when (val result = state) {
            null, is SubjectDetailsUIState.Placeholder -> {
                TvStaticFocusGroup(focus, "subject:", emptyList(), ready = false)
                TvMessage("正在加载番剧…")
            }
            is SubjectDetailsUIState.Err -> {
                TvStaticFocusGroup(focus, "subject:", listOf("subject:retry"))
                TvMessage("详情加载失败", "请检查网络后重试。", "重试", vm::reload,
                    actionModifier = Modifier.tvFocusTarget("subject:retry", focus))
            }
            is SubjectDetailsUIState.Ok -> TvSubjectContent(
                result.value,
                canCollect = auth.isSessionValid == true,
                onEpisode = { onEpisode(subjectId, it) },
            )
        }
    }
}

@Composable
private fun TvSubjectContent(
    state: SubjectDetailsState,
    canCollect: Boolean,
    onEpisode: (Int) -> Unit,
) {
    val presentation by state.presentation.collectAsStateWithLifecycle()
    val collectionState = state.editableSubjectCollectionTypeState
    val collection by collectionState.presentationFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focus = LocalTvFocusState.current
    val grid = rememberLazyGridState()
    val nsfwMode = rememberTvNsfwMode()
    var collectionError by remember { mutableStateOf(false) }
    var expandedSummary by rememberSaveable { mutableStateOf(false) }
    val episodes = presentation.episodeListUiState
    val info = state.info
    var revealed by rememberSaveable(state.subjectId, nsfwMode) { mutableStateOf(false) }
    if (info?.nsfw == true && nsfwMode != NsfwMode.DISPLAY && !revealed) {
        TvStaticFocusGroup(focus, "subject:", if (nsfwMode == NsfwMode.BLUR) listOf("subject:reveal") else emptyList())
        TvMessage(
            "内容已按偏好隐藏",
            actionLabel = if (nsfwMode == NsfwMode.BLUR) "临时显示内容" else null,
            onAction = { revealed = true },
            actionModifier = Modifier.tvFocusTarget("subject:reveal", focus),
        )
        return
    }
    val playId = state.subjectProgressState.episodeIdToPlay
        ?: episodes.mainEpisodes.firstOrNull { it.isBroadcast }?.episodeId
    val targets = buildList {
        if (playId != null) add("subject:play" to 0)
        if (info != null) add("subject:summary" to 0)
        if (canCollect && !collection.isSetSelfCollectionTypeWorking) add("subject:collection" to 0)
        episodes.mainEpisodes.forEachIndexed { index, episode -> add("subject:episode-${episode.episodeId}" to index + 2) }
        val otherStart = 3 + episodes.mainEpisodes.size
        episodes.otherEpisodes.forEachIndexed { index, episode -> add("subject:episode-${episode.episodeId}" to otherStart + index) }
    }
    TvLazyFocusGroup(
        focus, "subject:", targets.map { it.first }, !episodes.isPlaceholder, "page-back",
        scrollToItem = { grid.scrollToItem(targets[it].second) },
        isItemVisible = { key ->
            val index = targets.firstOrNull { it.first == key }?.second
            grid.layoutInfo.visibleItemsInfo.any { it.index == index }
        },
    )
    LazyVerticalGrid(
        GridCells.Adaptive(220.dp),
        Modifier.fillMaxSize().focusRestorer(),
        state = grid,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "info", span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (info != null && (!info.nsfw || nsfwMode == NsfwMode.DISPLAY || revealed)) {
                    AsyncImage(info.imageLarge, null, Modifier.size(156.dp, 218.dp), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(info?.displayName ?: presentation.displayName, fontSize = 30.sp)
                    if (info != null) {
                        Text("评分 ${info.ratingInfo.score}  ·  ${info.airDate}", fontSize = 18.sp)
                        Text(
                            info.summary.ifBlank { "暂无简介" },
                            fontSize = 18.sp,
                            maxLines = if (expandedSummary) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TvButton(if (expandedSummary) "收起简介" else "展开简介", { expandedSummary = !expandedSummary }, Modifier.tvFocusTarget("subject:summary", focus))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (playId != null) TvButton("继续 / 开始观看", { onEpisode(playId) }, Modifier.tvFocusTarget("subject:play", focus))
                        TvButton(
                            when {
                                !canCollect -> "登录后可收藏"
                                collection.isSetSelfCollectionTypeWorking -> "正在保存…"
                                state.selfCollected -> "取消收藏 · ${tvCollectionLabel(state.selfCollectionType)}"
                                else -> "加入在看"
                            },
                            onClick = {
                                scope.launch {
                                    val target = if (state.selfCollected) UnifiedCollectionType.NOT_COLLECTED else UnifiedCollectionType.DOING
                                    collectionError = collectionState.setSelfCollectionType(target) != null
                                }
                            },
                            enabled = canCollect && !collection.isSetSelfCollectionTypeWorking,
                            modifier = Modifier.tvFocusTarget("subject:collection", focus),
                        )
                    }
                    if (collectionError) Text("收藏操作失败，请重试。", color = MaterialTheme.colorScheme.error, fontSize = 18.sp)
                }
            }
        }
        item(key = "episodes-title", span = { GridItemSpan(maxLineSpan) }) { Text("选择剧集", fontSize = 24.sp) }
        if (episodes.isPlaceholder) {
            item(key = "episodes-loading", span = { GridItemSpan(maxLineSpan) }) { TvMessage("正在加载剧集…") }
        } else if (episodes.mainEpisodes.isEmpty() && episodes.otherEpisodes.isEmpty()) {
            item(key = "episodes-empty", span = { GridItemSpan(maxLineSpan) }) { TvMessage("暂时没有剧集信息") }
        }
        items(episodes.mainEpisodes, key = { "episode-${it.episodeId}" }) { episode ->
            TvEpisodeCard(episode, { onEpisode(episode.episodeId) }, Modifier.tvFocusTarget("subject:episode-${episode.episodeId}", focus))
        }
        if (episodes.otherEpisodes.isNotEmpty()) {
            item(key = "special-title", span = { GridItemSpan(maxLineSpan) }) { Text("特别篇 / 其他", fontSize = 24.sp) }
            items(episodes.otherEpisodes, key = { "episode-${it.episodeId}" }) { episode ->
                TvEpisodeCard(episode, { onEpisode(episode.episodeId) }, Modifier.tvFocusTarget("subject:episode-${episode.episodeId}", focus))
            }
        }
    }
}

@Composable
internal fun TvEpisodeCard(episode: EpisodeListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().testTag("tv-episode-${episode.episodeId}")) {
        Column(Modifier.fillMaxWidth().height(116.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val status = when {
                episode.isDoneOrDropped -> " · 已看"
                !episode.isBroadcast -> " · 未播出"
                episode.playProgress != null -> " · ${(episode.playProgress!! * 100).toInt()}%"
                else -> ""
            }
            Text("${episode.ep ?: episode.sort}$status", fontSize = 20.sp, maxLines = 1)
            Text(episode.nameCn.ifBlank { episode.name }, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
