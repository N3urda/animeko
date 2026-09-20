/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvHomeInitialFocusTest {
    @Test
    fun followedContentWinsAfterItFinishesLoading() = runAniComposeUiTest {
        var followed by mutableStateOf<String?>(null)
        var ready by mutableStateOf(false)
        setContent { TvTheme { HomeFocusHarness(followed, ready) } }
        runOnIdle { followed = "followed:8"; ready = true }
        onNodeWithTag("followed:8").assertIsFocused()
    }

    @Test
    fun manuallyFocusedSearchIsNotStolenByLateContent() = runAniComposeUiTest {
        var followed by mutableStateOf<String?>(null)
        var ready by mutableStateOf(false)
        setContent { TvTheme { HomeFocusHarness(followed, ready) } }
        onNodeWithTag("home-search").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        onNodeWithTag("home-search").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        onNodeWithTag("trending:1").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }
        onNodeWithTag("home-search").assertIsFocused()
        runOnIdle { followed = "followed:8"; ready = true }
        onNodeWithTag("home-search").assertIsFocused()
    }

    @Test
    fun returningKeepsSavedTrendingFocusAheadOfHomePriority() = runAniComposeUiTest {
        var visible by mutableStateOf(true)
        setContent {
            TvTheme {
                val holder = rememberSaveableStateHolder()
                if (visible) holder.SaveableStateProvider("home") {
                    HomeFocusHarness("followed:8", true, onOpen = { visible = false })
                } else TvButton("返回", { visible = true }, Modifier.testTag("return"))
            }
        }
        onNodeWithTag("followed:8").assertIsFocused()
        onNodeWithTag("trending:1").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        onNodeWithTag("trending:1").performTvClick()
        onNodeWithTag("return").performTvClick()
        onNodeWithTag("trending:1").assertIsFocused()
    }
}

@Composable
private fun HomeFocusHarness(followed: String?, followedReady: Boolean, onOpen: () -> Unit = {}) {
    val focus = rememberTvFocusState("home-awaiting-content")
    TvSetHomeInitialFocus(focus, followed, "trending:1", followedReady, true)
    TvStaticFocusGroup(focus, "followed:", listOfNotNull(followed), followedReady, "home-search")
    TvStaticFocusGroup(focus, "trending:", listOf("trending:1"), true, "home-search")
    TvPage("首页", null, focusState = focus) {
        TvButton("搜索", {}, Modifier.tvFocusTarget("home-search", focus).testTag("home-search"))
        if (followed != null) TvButton("继续追番", onOpen, Modifier.tvFocusTarget(followed, focus).testTag(followed))
        TvButton("热门", onOpen, Modifier.tvFocusTarget("trending:1", focus).testTag("trending:1"))
    }
}
