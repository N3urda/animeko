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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
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
    val nsfwMode = rememberTvNsfwMode()
    var collectionError by remember { mutableStateOf(false) }
    val episodes = presentation.episodeListUiState
    val info = state.info
    var revealed by rememberSaveable(state.subjectId, nsfwMode) { mutableStateOf(false) }
    if (info == null) {
        TvStaticFocusGroup(focus, "subject:", emptyList(), ready = false)
        TvMessage("正在加载番剧…")
        return
    }
    if (info.nsfw && nsfwMode != NsfwMode.DISPLAY && !revealed) {
        TvStaticFocusGroup(focus, "subject:", if (nsfwMode == NsfwMode.BLUR) listOf("subject:reveal") else emptyList())
        TvMessage(
            "内容已按偏好隐藏",
            actionLabel = if (nsfwMode == NsfwMode.BLUR) "临时显示内容" else null,
            onAction = { revealed = true },
            actionModifier = Modifier.tvFocusTarget("subject:reveal", focus),
        )
        return
    }
    val preferredEpisodeId = state.subjectProgressState.episodeIdToPlay
    val playEpisode = remember(episodes, preferredEpisodeId) {
        tvCataloguePlaybackEpisode(episodes, preferredEpisodeId)
    }
    val headerFocusKeys = buildList {
        if (playEpisode != null) add("subject:play")
        if (canCollect && !collection.isSetSelfCollectionTypeWorking) add("subject:collection")
        if (info.summary.isNotBlank()) add("subject:summary")
    }
    TvCatalogueEpisodeGrid(episodes, playEpisode?.episodeId, headerFocusKeys, onEpisode) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                AsyncImage(
                    info.imageLarge, null,
                    Modifier.width(156.dp).aspectRatio(2f / 3f),
                    contentScale = ContentScale.Fit,
                    crossfade = false,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(info.displayName, fontSize = 28.sp, lineHeight = 36.sp)
                    Text(
                        buildList {
                            add(if (info.ratingInfo.total > 0) "评分 ${info.ratingInfo.score}" else "暂无评分")
                            if (info.airDate.isValid) add("${info.airDate} 开播")
                        }.joinToString(" · "),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (playEpisode != null) {
                            TvButton(
                                tvCataloguePlaybackLabel(playEpisode), { onEpisode(playEpisode.episodeId) },
                                Modifier.tvFocusTarget("subject:play", focus).testTag("tv-subject-play"),
                            )
                        } else Text(if (episodes.isPlaceholder) "正在读取播放进度…" else "暂无已播出剧集", fontSize = 18.sp)
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
                    if (info.summary.isNotBlank()) TvCatalogueSummaryButton(info.displayName, info.summary)
                    if (collectionError) Text("收藏操作失败，请重试。", color = MaterialTheme.colorScheme.error, fontSize = 18.sp)
                }
            }

        }
    }
}

@Composable
internal fun TvCatalogueSummaryButton(title: String, summary: String) {
    val focus = LocalTvFocusState.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    TvButton(
        "展开简介", { expanded = true },
        Modifier.tvFocusTarget("subject:summary", focus).testTag("tv-subject-summary"),
    )
    if (expanded) TvScrollableTextDialog(title, summary) {
        expanded = false
        focus.requestFocus("subject:summary")
    }
}

@Composable
internal fun TvEpisodeCard(episode: EpisodeListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val target = modifier.fillMaxWidth().testTag("tv-episode-${episode.episodeId}")
    if (episode.isBroadcast) {
        Card(onClick = onClick, modifier = target, border = tvCardBorder(), scale = tvCardScale()) {
            TvEpisodeCardContent(episode)
        }
    } else {
        Box(
            target.focusProperties { canFocus = false }.semantics { disabled() }
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        ) {
            TvEpisodeCardContent(episode)
        }
    }
}

@Composable
private fun TvEpisodeCardContent(episode: EpisodeListItem) {
    Column(Modifier.fillMaxWidth().height(116.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val status = when {
            !episode.isBroadcast -> "未播出"
            episode.collectionType == UnifiedCollectionType.DROPPED -> "已弃看"
            episode.collectionType == UnifiedCollectionType.DONE -> "已看"
            episode.playProgress != null -> "已播 ${(episode.playProgress!!.coerceIn(0f, 1f) * 100).toInt()}%"
            else -> "可播放"
        }
        Text("${tvCatalogueEpisodeLabel(episode)} · $status", fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            episode.nameCn.ifBlank { episode.name }.ifBlank { "暂无剧集标题" },
            fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
