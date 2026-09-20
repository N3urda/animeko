/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvComponentInteractionTest {
    @Test
    fun selectedCategoryRemainsSelectedWhenFocusMovesToAnotherAction() = runAniComposeUiTest {
        setContent {
            TvTheme {
                val first = remember { FocusRequester() }
                Row {
                    TvButton("播放", {}, Modifier.focusRequester(first).testTag("current"), selected = true)
                    TvButton("订阅", {}, Modifier.testTag("other"))
                }
                LaunchedEffect(Unit) { first.requestFocus() }
            }
        }
        onNodeWithTag("current").assertIsSelected().assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        onNodeWithTag("other").assertIsFocused().assertIsNotSelected()
        onNodeWithTag("current").assertIsSelected()
    }
}
