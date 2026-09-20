/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction

/** 通过遥控器确认键激活目标, 与 TV Material 的按键交互保持一致. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.performTvClick(): SemanticsNodeInteraction {
    performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    assertIsFocused()
    return performKeyInput {
        keyDown(Key.DirectionCenter)
        keyUp(Key.DirectionCenter)
    }
}
