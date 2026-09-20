/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvCatalogueSummaryTest {
    @Test
    fun remoteScrollsSummaryAndCloseRestoresExpandButton() = runAniComposeUiTest {
        setContent {
            TvTheme {
                val focus = rememberTvFocusState("subject:summary")
                TvPage("番剧详情", {}, focusState = focus) {
                    TvCatalogueSummaryButton("完整番剧标题", (1..60).joinToString("\n") { "第 $it 段番剧简介与故事介绍。" })
                }
            }
        }
        onNodeWithTag("tv-subject-summary").assertIsFocused().performTvClick()
        onNodeWithTag("tv-dialog-close").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        waitForIdle()
        val scrolled = onNodeWithTag("tv-dialog-message").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("向下按键应滚动长简介", scrolled > 0)
        onNodeWithTag("tv-dialog-close").performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }
        waitForIdle()
        val returned = onNodeWithTag("tv-dialog-message").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("向上按键应回到前面的简介", returned < scrolled)
        onNodeWithTag("tv-dialog-close").performTvClick()
        onNodeWithTag("tv-dialog-close").assertDoesNotExist()
        onNodeWithTag("tv-subject-summary").assertIsFocused()
    }
}
