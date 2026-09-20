/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

enum class TvPlayerOverlay { Hidden, Controls, Sources, Episodes, Options, Subtitles, Audio, Speed }
enum class TvPlayerKey { Confirm, Left, Right, Up, Down, Menu }

data class TvPlayerInputState(
    val overlay: TvPlayerOverlay = TvPlayerOverlay.Hidden,
    val previewMillis: Long? = null,
    val previewOrigin: TvPlayerOverlay = TvPlayerOverlay.Hidden,
    val heldKey: TvPlayerKey? = null,
) {
    fun mediaChanged() = TvPlayerInputState()
}

data class TvPlayerInputResult(
    val state: TvPlayerInputState,
    val consumed: Boolean = true,
    val seekToMillis: Long? = null,
    val exitPlayer: Boolean = false,
    val togglePlayback: Boolean = false,
)

/** 预览只改变目标时间，确认时才提交一次 seek；菜单把方向键交还焦点系统。 */
fun tvPlayerInput(
    state: TvPlayerInputState,
    key: TvPlayerKey,
    down: Boolean,
    repeatCount: Int,
    heldMillis: Long,
    positionMillis: Long,
    durationMillis: Long,
): TvPlayerInputResult {
    if (!down) {
        return TvPlayerInputResult(state.copy(heldKey = null), consumed = state.heldKey == key)
    }
    if (repeatCount > 0 && state.heldKey == key && key == TvPlayerKey.Confirm) {
        return TvPlayerInputResult(state)
    }
    if (state.previewMillis != null) {
        if (key == TvPlayerKey.Confirm) {
            return TvPlayerInputResult(
                state.copy(overlay = TvPlayerOverlay.Controls, previewMillis = null, heldKey = key),
                seekToMillis = state.previewMillis.takeIf { durationMillis > 0 }?.coerceIn(0, durationMillis),
            )
        }
        val direction = when (key) { TvPlayerKey.Left -> -1; TvPlayerKey.Right -> 1; else -> 0 }
        val step = if (repeatCount > 0 && heldMillis >= 500) 30000L else 10000L
        return TvPlayerInputResult(state.copy(previewMillis = (state.previewMillis + direction * step).coerceIn(0, durationMillis.coerceAtLeast(0)), heldKey = key))
    }
    if (state.overlay != TvPlayerOverlay.Hidden) return TvPlayerInputResult(state, consumed = false)
    if ((key == TvPlayerKey.Left || key == TvPlayerKey.Right) && durationMillis > 0) {
        val target = positionMillis + if (key == TvPlayerKey.Left) -10000 else 10000
        return TvPlayerInputResult(state.copy(previewMillis = target.coerceIn(0, durationMillis), previewOrigin = state.overlay, heldKey = key))
    }
    return TvPlayerInputResult(
        state.copy(overlay = TvPlayerOverlay.Controls, heldKey = key),
        togglePlayback = key == TvPlayerKey.Confirm,
    )
}

fun tvPlayerBack(state: TvPlayerInputState): TvPlayerInputResult = when {
    state.previewMillis != null -> TvPlayerInputResult(state.copy(overlay = state.previewOrigin, previewMillis = null, heldKey = null))
    state.overlay == TvPlayerOverlay.Hidden -> TvPlayerInputResult(state, exitPlayer = true)
    state.overlay == TvPlayerOverlay.Controls -> TvPlayerInputResult(state.copy(overlay = TvPlayerOverlay.Hidden, heldKey = null))
    state.overlay in listOf(TvPlayerOverlay.Subtitles, TvPlayerOverlay.Audio, TvPlayerOverlay.Speed) ->
        TvPlayerInputResult(state.copy(overlay = TvPlayerOverlay.Options, heldKey = null))
    else -> TvPlayerInputResult(state.copy(overlay = TvPlayerOverlay.Controls, heldKey = null))
}
