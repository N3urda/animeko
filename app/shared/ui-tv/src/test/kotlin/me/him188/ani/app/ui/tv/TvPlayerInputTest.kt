/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TvPlayerInputTest {
    @Test fun firstConfirmOnlyShowsControlsAndConsumesRelease() {
        val down = tvPlayerInput(TvPlayerInputState(), TvPlayerKey.Confirm, true, 0, 0, 5000, 60000)
        assertEquals(TvPlayerOverlay.Controls, down.state.overlay)
        assertNull(down.seekToMillis)
        val up = tvPlayerInput(down.state, TvPlayerKey.Confirm, false, 0, 20, 5000, 60000)
        assertTrue(up.consumed)
        assertNull(up.seekToMillis)
    }
    @Test fun seekPreviewDoesNotSeekUntilOneConfirmPress() {
        val preview = tvPlayerInput(TvPlayerInputState(), TvPlayerKey.Right, true, 0, 0, 5000, 60000)
        assertEquals(15000L, preview.state.previewMillis)
        assertNull(preview.seekToMillis)
        val confirm = tvPlayerInput(preview.state, TvPlayerKey.Confirm, true, 0, 0, 5000, 60000)
        assertEquals(15000L, confirm.seekToMillis)
        val repeated = tvPlayerInput(confirm.state, TvPlayerKey.Confirm, true, 2, 600, 5000, 60000)
        assertNull(repeated.seekToMillis)
        assertTrue(repeated.consumed)
    }
    @Test fun seekingClampsAndAcceleratesOnlyAfterHalfASecond() {
        var state = TvPlayerInputState(previewMillis = 1000)
        state = tvPlayerInput(state, TvPlayerKey.Left, true, 0, 0, 1000, 100000).state
        assertEquals(0L, state.previewMillis)
        state = tvPlayerInput(state, TvPlayerKey.Right, true, 1, 200, 1000, 100000).state
        assertEquals(10000L, state.previewMillis)
        state = tvPlayerInput(state, TvPlayerKey.Right, true, 2, 600, 1000, 100000).state
        assertEquals(40000L, state.previewMillis)
        state = tvPlayerInput(state, TvPlayerKey.Right, true, 3, 700, 1000, 45000).state
        assertEquals(45000L, state.previewMillis)
    }
    @Test fun backCancelsPreviewThenExitsHiddenPlayer() {
        val preview = tvPlayerInput(TvPlayerInputState(), TvPlayerKey.Right, true, 0, 0, 5000, 60000).state
        val cancel = tvPlayerBack(preview)
        assertNull(cancel.state.previewMillis)
        assertFalse(cancel.exitPlayer)
        assertEquals(TvPlayerOverlay.Hidden, cancel.state.overlay)
        assertTrue(tvPlayerBack(cancel.state).exitPlayer)
    }
    @Test fun backClosesMenuBeforeControls() {
        val menu = TvPlayerInputState(overlay = TvPlayerOverlay.Sources)
        val controls = tvPlayerBack(menu)
        assertEquals(TvPlayerOverlay.Controls, controls.state.overlay)
        assertFalse(controls.exitPlayer)
        assertEquals(TvPlayerOverlay.Hidden, tvPlayerBack(controls.state).state.overlay)
    }
    @Test fun visibleControlsAndMenusKeepDirectionalNavigation() {
        for (overlay in listOf(TvPlayerOverlay.Controls, TvPlayerOverlay.Sources, TvPlayerOverlay.Episodes)) {
            val result = tvPlayerInput(TvPlayerInputState(overlay), TvPlayerKey.Right, true, 0, 0, 1000, 2000)
            assertFalse(result.consumed)
        }
    }
    @Test fun unknownDurationDoesNotCreateSeekPreview() {
        val result = tvPlayerInput(TvPlayerInputState(), TvPlayerKey.Right, true, 0, 0, 1000, 0)
        assertNull(result.state.previewMillis)
        assertNull(result.seekToMillis)
        assertEquals(TvPlayerOverlay.Controls, result.state.overlay)
    }
    @Test fun mediaChangeDropsUncommittedPreview() {
        val state = TvPlayerInputState(previewMillis = 50000, heldKey = TvPlayerKey.Right)
        assertEquals(TvPlayerInputState(), state.mediaChanged())
    }
}
