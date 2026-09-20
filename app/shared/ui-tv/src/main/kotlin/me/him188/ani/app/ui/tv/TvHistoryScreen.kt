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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.models.player.playProgress
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.playback.PlaybackHistoryViewModel
import me.him188.ani.app.ui.user.SelfInfoStateProducer

@Composable
fun TvHistoryScreen(
    onEpisode: (Int, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLogin: (() -> Unit)? = null,
) {
    val vm = viewModel { PlaybackHistoryViewModel() }
    val selfInfo by remember { SelfInfoStateProducer().flow }.collectAsStateWithLifecycle()
    val histories by vm.stateFlow.collectAsStateWithLifecycle()
    val pending by vm.pendingOpsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focus = rememberTvFocusState("history:")
    val metadata = viewModel { TvHistoryMetadataViewModel() }
    val subjectStates by metadata.states.collectAsStateWithLifecycle()
    val nsfwMode = rememberTvNsfwMode()
    val list = rememberLazyListState()
    val subjectIds = histories.mapNotNull { it.subjectId }.toSet()
    LaunchedEffect(subjectIds) { metadata.load(subjectIds) }
    val visible = histories.filterNot { history ->
        history.isDeleted || (nsfwMode == NsfwMode.HIDE &&
            (subjectStates[history.subjectId] as? TvHistorySubjectState.Ready)?.nsfw == true)
    }.sortedByDescending { it.updatedAtMillis }
    val focusTargets = visible.mapIndexedNotNull { index, history ->
        when (subjectStates[history.subjectId]) {
            is TvHistorySubjectState.Ready, TvHistorySubjectState.Failed -> "history:${history.episodeId}" to index
            else -> null
        }
    }
    val checking = visible.any { it.subjectId != null &&
        (subjectStates[it.subjectId] == null || subjectStates[it.subjectId] == TvHistorySubjectState.Loading) }
    val requestedRecord = visible.firstOrNull { "history:${it.episodeId}" == focus.requestedKey }
    val requestedClassification = subjectStates[requestedRecord?.subjectId]
    val waitingForRequested = requestedRecord?.subjectId != null &&
        (requestedClassification == null || requestedClassification == TvHistorySubjectState.Loading)
    TvLazyFocusGroup(
        focus, "history:", focusTargets.map { it.first }, !waitingForRequested && (focusTargets.isNotEmpty() || !checking), "page-back",
        scrollToItem = { list.scrollToItem(focusTargets[it].second) },
        isItemVisible = { key -> list.layoutInfo.visibleItemsInfo.any { it.key == key } },
    )
    var syncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf(false) }
    var synced by remember { mutableStateOf(false) }
    fun sync() {
        if (syncing || selfInfo.isSessionValid != true) return
        syncing = true
        syncError = false
        synced = false
        scope.launch {
            try {
                vm.syncOnce()
                synced = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                syncError = true
            } finally {
                syncing = false
            }
        }
    }
    TvPage(
        "观看历史", onBack, modifier, focusState = focus,
        actions = {
            when (selfInfo.isSessionValid) {
                true -> TvButton(if (syncing) "正在同步…" else "同步", ::sync,
                    Modifier.tvFocusTarget("history-sync", focus), enabled = !syncing)
                false -> TvButton("登录后同步", { onLogin?.invoke() },
                    Modifier.tvFocusTarget("history-sync", focus), enabled = onLogin != null)
                null -> TvButton("正在检查账号…", {}, enabled = false)
            }
        },
    ) {
        if (pending.isNotEmpty()) Text("${pending.size} 条本地更新等待同步", fontSize = 18.sp)
        if (selfInfo.isSessionValid == false) Text("当前显示本地记录，登录后可同步到其他设备。", fontSize = 18.sp)
        if (syncError) TvMessage(
            "同步失败，本地记录仍可使用", actionLabel = "重试",
            onAction = { focus.requestFocus("history:"); sync() },
            actionModifier = Modifier.tvFocusTarget("history-retry", focus),
        )
        if (synced) Text("历史记录已同步", fontSize = 18.sp)
        if (visible.isEmpty()) {
            TvMessage("没有可显示的观看记录", "播放番剧后，可在这里继续观看。")
        } else {
            LazyColumn(
                Modifier.fillMaxSize().focusRestorer(), state = list,
                contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(visible, key = { _, history -> "history:${history.episodeId}" }) { _, history ->
                    TvHistoryRecord(
                        history, subjectStates[history.subjectId], nsfwMode,
                        onRetry = { history.subjectId?.let(metadata::retry) },
                        onPlay = { history.subjectId?.let { onEpisode(it, history.episodeId) } },
                        targetModifier = Modifier.tvFocusTarget("history:${history.episodeId}", focus),
                    )
                }
            }
        }
    }
}

internal fun tvPlaybackTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    val minutes = seconds / 60
    return "${minutes}:${(seconds % 60).toString().padStart(2, '0')}"
}


@Composable
internal fun TvHistoryCard(
    history: EpisodeHistory,
    nsfwMode: NsfwMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nsfwMode == NsfwMode.HIDE) return
    var revealed by rememberSaveable(history.episodeId, nsfwMode) { mutableStateOf(false) }
    val masked = nsfwMode == NsfwMode.BLUR && !revealed
    Card(
        onClick = { if (masked) revealed = true else onClick() },
        modifier = modifier.fillMaxWidth().testTag("tv-history-${history.episodeId}"),
        border = tvCardBorder(),
        scale = tvCardScale(),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (masked) "内容已遮盖" else history.subjectName ?: "条目 ${history.subjectId}",
                        fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (masked) "按确认键临时显示" else history.episodeName?.takeIf { it.isNotBlank() }
                            ?: "剧集 ${history.episodeSort ?: history.episodeId}",
                        fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!masked) Column(Modifier.width(190.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("继续观看", fontSize = 20.sp)
                    Text(
                        history.durationMillis?.takeIf { it > 0 }?.let {
                            "${tvPlaybackTime(history.positionMillis.coerceIn(0, it))} / ${tvPlaybackTime(it)}"
                        } ?: "已播放 ${tvPlaybackTime(history.positionMillis)}",
                        fontSize = 18.sp,
                    )
                }
            }
            if (!masked) history.playProgress?.let { progress ->
                Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}


@Composable
internal fun TvHistoryRecord(
    history: EpisodeHistory,
    classification: TvHistorySubjectState?,
    preference: NsfwMode,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    targetModifier: Modifier = Modifier,
) {
    when (classification) {
        is TvHistorySubjectState.Ready -> TvHistoryCard(
            history, if (classification.nsfw) preference else NsfwMode.DISPLAY, onPlay, targetModifier,
        )
        TvHistorySubjectState.Failed -> TvMessage(
            "条目信息读取失败", "确认内容偏好后才能打开此记录。", "重试", onRetry,
            actionModifier = targetModifier,
        )
        else -> TvMessage(if (history.subjectId == null) "旧记录缺少条目信息，请通过搜索打开" else "正在检查条目信息…")
    }
}
