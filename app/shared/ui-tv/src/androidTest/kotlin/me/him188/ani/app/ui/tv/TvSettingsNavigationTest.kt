/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvSettingsNavigationTest {
    @Test
    fun categoriesSelectOnFocusAndLeftReturnsToTheActiveCategory() = runAniComposeUiTest {
        setContent {
            TvTheme {
                var category by remember { mutableStateOf("播放") }
                TvSettingsLayout(
                    category = category,
                    onCategorySelected = { category = it },
                    items = (1..12).map { TvSettingsItem("row-$it", "$category 选项 $it", onClick = {}) },
                    onBack = {},
                )
            }
        }
        onNodeWithTag("tv-settings-播放").assertIsFocused().assertIsSelected()
            .performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        onNodeWithTag("tv-settings-订阅").assertIsFocused().assertIsSelected()
            .performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-settings-row-row-1").assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        onNodeWithTag("tv-settings-row-row-2").assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        onNodeWithTag("tv-settings-订阅").assertIsFocused().assertIsSelected()
            .performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-settings-row-row-2").assertIsFocused()
    }

    @Test
    fun changingCategoryRestoresEachCategoriesLastItem() = runAniComposeUiTest {
        setContent {
            TvTheme {
                var category by remember { mutableStateOf("播放") }
                TvSettingsLayout(
                    category = category,
                    onCategorySelected = { category = it },
                    items = (1..4).map { TvSettingsItem("row-$it", "$category 选项 $it", onClick = {}) },
                    onBack = {},
                )
            }
        }
        onNodeWithTag("tv-settings-播放").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-settings-row-row-1").performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        onNodeWithTag("tv-settings-row-row-2").performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        onNodeWithTag("tv-settings-播放").performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        onNodeWithTag("tv-settings-订阅").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-settings-row-row-1").assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        onNodeWithTag("tv-settings-订阅").performKeyInput { keyDown(Key.DirectionUp); keyUp(Key.DirectionUp) }
        onNodeWithTag("tv-settings-播放").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-settings-row-row-2").assertIsFocused()
    }
    @Test
    fun scrollingDownThenReturningFromCategoriesKeepsTheLastItem() = runAniComposeUiTest {
        setContent {
            TvTheme {
                TvSettingsLayout(
                    category = "播放", onCategorySelected = {},
                    items = (1..20).map { TvSettingsItem("row-$it", "播放选项 $it", "说明 $it", onClick = {}) },
                    onBack = {},
                )
            }
        }
        onNodeWithTag("tv-settings-播放").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        repeat(8) { index ->
            onNodeWithTag("tv-settings-row-row-${index + 1}").assertIsFocused()
                .performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        }
        onNodeWithTag("tv-settings-row-row-9").assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        onNodeWithTag("tv-settings-播放").assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-settings-row-row-9").assertIsFocused()
    }

    @Test
    fun lastCategoryIsReachableInAShortViewport() = runAniComposeUiTest {
        setContent {
            TvTheme {
                var category by remember { mutableStateOf("播放") }
                Box(Modifier.height(360.dp)) {
                    TvSettingsLayout(category, { category = it }, emptyList(), onBack = {})
                }
            }
        }
        listOf("播放", "订阅", "数据源", "存储", "账号").forEach { title ->
            onNodeWithTag("tv-settings-$title").assertIsFocused()
                .performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        }
        onNodeWithTag("tv-settings-界面").assertIsFocused().assertIsDisplayed().assertIsSelected()
    }

}
