/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import me.him188.ani.app.ui.foundation.navigation.BackHandler

/** 可注入播放快照的控制层; 播放器与资源会话由调用方持有. */
@Composable
internal fun TvPlayerChrome(
    state: TvPlayerUiState,
    input: TvPlayerInputState,
    onInputChange: (TvPlayerInputState) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onNextEpisode: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val progressFocus = remember { FocusRequester() }
    val episodesFocus = remember { FocusRequester() }
    val sourcesFocus = remember { FocusRequester() }
    val optionsFocus = remember { FocusRequester() }
    var restoreFocus by remember { mutableStateOf(playFocus) }
    var activity by remember { mutableLongStateOf(0) }
    var waitingTooLong by remember(state.mediaKey, state.status.phase) { mutableStateOf(false) }
    var dispatchedInput by remember { mutableStateOf(input) }
    SideEffect { dispatchedInput = input }
    val changeInput by rememberUpdatedState { next: TvPlayerInputState ->
        dispatchedInput = next
        onInputChange(next)
    }
    val showRecovery = state.status.recoverable || waitingTooLong

    fun showMenu(overlay: TvPlayerOverlay, requester: FocusRequester) {
        restoreFocus = requester
        changeInput(dispatchedInput.copy(overlay = overlay, previewMillis = null))
    }
    fun preview(target: Long, key: TvPlayerKey? = null) {
        if (state.durationMillis <= 0) return
        restoreFocus = progressFocus
        changeInput(
            dispatchedInput.copy(
                previewMillis = target.coerceIn(0, state.durationMillis),
                previewOrigin = TvPlayerOverlay.Controls,
                heldKey = key,
            ),
        )
    }
    BackHandler {
        val result = tvPlayerBack(dispatchedInput)
        changeInput(result.state)
        if (result.exitPlayer) onBack()
    }
    LaunchedEffect(state.mediaKey, state.status.phase) {
        if (state.status.phase.waiting) {
            delay(15000)
            waitingTooLong = true
        }
    }
    LaunchedEffect(showRecovery) {
        if (showRecovery && dispatchedInput.overlay == TvPlayerOverlay.Hidden && dispatchedInput.previewMillis == null) {
            restoreFocus = sourcesFocus
            changeInput(dispatchedInput.copy(overlay = TvPlayerOverlay.Controls))
        }
    }
    LaunchedEffect(input.overlay, input.previewMillis != null) {
        when {
            input.previewMillis != null || input.overlay == TvPlayerOverlay.Hidden -> rootFocus.requestFocus()
            input.overlay == TvPlayerOverlay.Controls -> restoreFocus.requestFocus()
        }
    }
    LaunchedEffect(input.overlay, input.previewMillis, activity, state.isPlaying, showRecovery) {
        if (input.overlay == TvPlayerOverlay.Controls && input.previewMillis == null && state.isPlaying && !showRecovery) {
            delay(5000)
            changeInput(dispatchedInput.copy(overlay = TvPlayerOverlay.Hidden))
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag("tv-player")
            .onPreviewKeyEvent { event ->
                activity++
                val key = when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> TvPlayerKey.Confirm
                    Key.DirectionLeft -> TvPlayerKey.Left
                    Key.DirectionRight -> TvPlayerKey.Right
                    Key.DirectionUp -> TvPlayerKey.Up
                    Key.DirectionDown -> TvPlayerKey.Down
                    Key.Menu -> TvPlayerKey.Menu
                    else -> return@onPreviewKeyEvent false
                }
                val native = event.nativeKeyEvent
                val before = dispatchedInput
                val result = tvPlayerInput(
                    before, key, event.type == KeyEventType.KeyDown, native.repeatCount,
                    native.eventTime - native.downTime, state.positionMillis, state.durationMillis,
                )
                if (before.overlay == TvPlayerOverlay.Hidden && before.previewMillis == null) restoreFocus = playFocus
                changeInput(result.state)
                if (result.togglePlayback) onTogglePlayback()
                result.seekToMillis?.let(onSeek)
                result.consumed
            }
            .focusRequester(rootFocus).focusable(),
    ) {
        content()
        if (input.previewMillis != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = .9f)).padding(horizontal = 40.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("跳转至 ${tvTime(input.previewMillis)}", fontSize = 26.sp)
                TvPlayerProgressTrack(state.positionMillis, state.durationMillis, input.previewMillis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("当前 ${tvTime(state.positionMillis)}", fontSize = 18.sp)
                    Text(tvTime(state.durationMillis), fontSize = 18.sp)
                }
                Text("左右调整 · 长按加速 · 确认跳转 · 返回取消", fontSize = 18.sp)
            }
        } else if (input.overlay == TvPlayerOverlay.Controls) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = .9f)).padding(horizontal = 40.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "${state.title} · ${state.episodeTitle}", fontSize = 23.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (state.sourceName.isNotBlank()) {
                    Text(state.sourceName, color = Color(0xFFBFC8D8), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.status.phase != TvPlayerPhase.Playing) {
                    Text(
                        state.status.message + if (waitingTooLong) " · 等待较久，可重试或换源" else "",
                        color = if (state.status.recoverable) Color(0xFFFFB4AB) else Color(0xFFC4D7FF),
                        fontSize = 18.sp, modifier = Modifier.testTag("tv-player-status"),
                    )
                }
                Button(
                    onClick = { preview(state.positionMillis) },
                    modifier = Modifier.fillMaxWidth().focusRequester(progressFocus).testTag("tv-player-progress")
                        .onPreviewKeyEvent { event ->
                            if (state.durationMillis <= 0 || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val direction = when (event.key) {
                                Key.DirectionLeft -> -1
                                Key.DirectionRight -> 1
                                else -> return@onPreviewKeyEvent false
                            }
                            preview(
                                state.positionMillis + direction * 10000L,
                                if (direction < 0) TvPlayerKey.Left else TvPlayerKey.Right,
                            )
                            true
                        },
                    enabled = state.durationMillis > 0,
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                ) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tvTime(state.positionMillis), fontSize = 17.sp)
                            Text(
                                if (state.durationMillis > 0) "左右调整进度 · ${tvTime(state.durationMillis)}" else "时长未知 · 暂不可跳转",
                                fontSize = 17.sp,
                            )
                        }
                        TvPlayerProgressTrack(state.positionMillis, state.durationMillis)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        when {
                            state.status.phase == TvPlayerPhase.Ended -> "重新播放"
                            state.playWhenReady -> "暂停"
                            else -> "播放"
                        }, onTogglePlayback, Modifier.focusRequester(playFocus).testTag("tv-play-pause"),
                    )
                    TvButton("选集", { showMenu(TvPlayerOverlay.Episodes, episodesFocus) }, Modifier.focusRequester(episodesFocus).testTag("tv-player-episodes"))
                    TvButton("下一集", onNextEpisode, enabled = state.hasNextEpisode)
                    TvButton("换源", { showMenu(TvPlayerOverlay.Sources, sourcesFocus) }, Modifier.focusRequester(sourcesFocus).testTag("tv-player-sources"))
                    TvButton("更多", { showMenu(TvPlayerOverlay.Options, optionsFocus) }, Modifier.focusRequester(optionsFocus).testTag("tv-player-options"))
                    if (showRecovery) TvButton("重试", onRetry, Modifier.testTag("tv-player-retry"))
                }
            }
        } else if (input.overlay == TvPlayerOverlay.Hidden && state.status.phase != TvPlayerPhase.Playing) {
            Column(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .78f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(state.status.message, fontSize = 24.sp, modifier = Modifier.testTag("tv-player-status"))
                Text(
                    when (state.status.phase) {
                        TvPlayerPhase.Paused -> "按确认键继续播放 · 上下键打开菜单"
                        TvPlayerPhase.Ended -> "按确认键重新播放 · 上下键选集"
                        else -> "上下键打开菜单，可换源或重试"
                    }, fontSize = 17.sp, color = Color(0xFFBFC8D8),
                )
            }
        }
    }
}

@Composable
private fun TvPlayerProgressTrack(positionMillis: Long, durationMillis: Long, previewMillis: Long? = null) {
    val progress = if (durationMillis > 0) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f
    val preview = previewMillis?.takeIf { durationMillis > 0 }?.let { (it.toFloat() / durationMillis).coerceIn(0f, 1f) }
    Canvas(
        Modifier.fillMaxWidth().height(12.dp).testTag("tv-player-progress-track").semantics {
            progressBarRangeInfo = if (durationMillis > 0) ProgressBarRangeInfo(progress, 0f..1f) else ProgressBarRangeInfo.Indeterminate
        },
    ) {
        val inset = 5.dp.toPx()
        val y = size.height / 2
        val width = (size.width - inset * 2).coerceAtLeast(0f)
        drawLine(Color(0xFF526075), Offset(inset, y), Offset(inset + width, y), 4.dp.toPx(), StrokeCap.Round)
        drawLine(Color(0xFF8AB6FF), Offset(inset, y), Offset(inset + width * progress, y), 4.dp.toPx(), StrokeCap.Round)
        drawCircle(Color(0xFF8AB6FF), 4.dp.toPx(), Offset(inset + width * progress, y))
        if (preview != null) drawCircle(Color.White, 6.dp.toPx(), Offset(inset + width * preview, y))
    }
}
