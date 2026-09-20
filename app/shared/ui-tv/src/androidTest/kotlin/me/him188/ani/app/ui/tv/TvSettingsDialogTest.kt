/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvSettingsDialogTest {
    @Test
    fun longMessageKeepsCancelAndConfirmVisibleWithCancelFocused() = runAniComposeUiTest {
        var confirmed = 0
        setContent {
            TvTheme {
                var open by remember { mutableStateOf(false) }
                val focus = rememberTvFocusState("delete")
                TvPage("存储", {}, focusState = focus) {
                    TvButton("删除缓存", { open = true }, Modifier.tvFocusTarget("delete", focus).testTag("delete"))
                    if (open) TvConfirmDialog(
                        "删除缓存", "删除此剧集的本地视频缓存？观看记录会保留。\n".repeat(35),
                        onConfirm = { confirmed++; open = false }, onDismiss = { open = false },
                    )
                }
            }
        }
        onNodeWithTag("delete").performTvClick()
        onNodeWithText("取消").assertIsDisplayed().assertIsFocused()
        onNodeWithText("确认").assertIsDisplayed()
        onNodeWithText("取消").performTvClick()
        onNodeWithTag("delete").assertIsFocused()
        runOnIdle { assertEquals(0, confirmed) }
    }
}
