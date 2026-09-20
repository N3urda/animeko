/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvTextFieldNavigationTest {
    @Test
    fun rightLeavesSearchInputAndCenterSubmitsOnce() = runAniComposeUiTest {
        var submitted = 0
        val keyboard = RecordingKeyboardController()
        setContent {
            TvTheme {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    val focus = remember { FocusRequester() }
                    Row {
                        TvButton("返回", {}, Modifier.testTag("back"))
                        TvOutlinedTextField(
                            state = rememberTextFieldState("番剧名称"),
                            modifier = Modifier.focusRequester(focus).testTag("input"),
                        )
                        TvButton("搜索", { submitted++ }, Modifier.testTag("submit"))
                    }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                }
            }
        }
        onNodeWithTag("input").assertIsFocused()
        runOnIdle { assertEquals(0, keyboard.showRequests) }
        onNodeWithTag("input").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("submit").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        runOnIdle { assertEquals(1, submitted) }
        onNodeWithTag("submit").performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        onNodeWithTag("input").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft)
            keyUp(Key.DirectionLeft)
        }
        onNodeWithTag("back").assertIsFocused()
    }

    @Test
    fun confirmRequestsKeyboardAndDownLeavesEmailInput() = runAniComposeUiTest {
        val keyboard = RecordingKeyboardController()
        setContent {
            TvTheme {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    val focus = remember { FocusRequester() }
                    Column {
                        TvButton("返回", {}, Modifier.testTag("back"))
                        TvOutlinedTextField(
                            state = rememberTextFieldState("test@example.com"),
                            modifier = Modifier.focusRequester(focus).testTag("input"),
                        )
                        TvButton("发送验证码", {}, Modifier.testTag("send"))
                    }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                }
            }
        }
        onNodeWithTag("input").performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        runOnIdle { assertEquals(1, keyboard.showRequests) }
        onNodeWithTag("input").performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        onNodeWithTag("send").assertIsFocused()
        onNodeWithTag("send").performKeyInput { keyDown(Key.DirectionUp); keyUp(Key.DirectionUp) }
        onNodeWithTag("input").assertIsFocused()
        runOnIdle { assertEquals(1, keyboard.showRequests) }
        onNodeWithTag("input").performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        runOnIdle { assertEquals(2, keyboard.showRequests) }
        onNodeWithTag("input").performKeyInput { keyDown(Key.DirectionUp); keyUp(Key.DirectionUp) }
        onNodeWithTag("back").assertIsFocused()
    }
}

private class RecordingKeyboardController : SoftwareKeyboardController {
    var showRequests = 0
    override fun show() { showRequests++ }
    override fun hide() = Unit
}
