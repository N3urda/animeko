/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvAppShellTest {
    @Test
    fun leftBoundaryTargetsSelectedTabAndRightRestoresRecentContent() = runAniComposeUiTest {
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(960.dp, 540.dp)) {
                    TvAppShell("home-history", {}) {
                        val focus = rememberTvFocusState("entry:1")
                        TvPage("观看历史", null, focusState = focus) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ShellEntry("entry:1", focus)
                                ShellEntry("entry:2", focus)
                            }
                        }
                    }
                }
            }
        }
        onNodeWithTag("entry:1").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("entry:2").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
        }
        onNodeWithTag("home-history").assertIsSelected().assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
        }
        onNodeWithTag("entry:2").assertIsFocused()
    }

    @Test
    fun leftMovesInsideContentBeforeEnteringSidebar() = runAniComposeUiTest {
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(960.dp, 540.dp)) {
                    TvAppShell("home-search", {}) {
                        val focus = rememberTvFocusState("result:2")
                        TvPage("搜索", null, focusState = focus) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                ShellEntry("result:1", focus)
                                ShellEntry("result:2", focus)
                            }
                        }
                    }
                }
            }
        }
        onNodeWithTag("result:2").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
        }
        onNodeWithTag("result:1").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
        }
        onNodeWithTag("home-search").assertIsFocused()
    }

    @Test
    fun asynchronousResultUpdateDoesNotTakeFocusFromSidebar() = runAniComposeUiTest {
        var ids by mutableStateOf(listOf(1, 2))
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(960.dp, 540.dp)) {
                    TvAppShell("home-search", {}) {
                        val focus = rememberTvFocusState("result:2")
                        TvPage("搜索", null, focusState = focus) {
                            TvStaticFocusGroup(focus, "result:", ids.map { "result:$it" })
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ids.forEach { id -> key(id) { ShellEntry("result:$id", focus) } }
                            }
                        }
                    }
                }
            }
        }
        onNodeWithTag("result:2").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
        }
        onNodeWithTag("home-search").assertIsFocused()
        runOnIdle { ids = listOf(3, 2, 1, 4) }
        onNodeWithTag("home-search").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
        }
        onNodeWithTag("result:2").assertIsFocused()
    }

    @Test
    fun sidebarRoundTripUsesNeighbourWhenPreviousResultWasRemoved() = runAniComposeUiTest {
        var ids by mutableStateOf(listOf(1, 2, 3, 4))
        setContent {
            TvTheme {
                TvAppShell("home-search", {}) {
                    val focus = rememberTvFocusState("result:2")
                    TvPage("搜索", null, focusState = focus) {
                        TvStaticFocusGroup(focus, "result:", ids.map { "result:$it" })
                        Column { ids.forEach { id -> key(id) { ShellEntry("result:$id", focus) } } }
                    }
                }
            }
        }
        onNodeWithTag("result:2").assertIsFocused().performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        onNodeWithTag("home-search").assertIsFocused()
        runOnIdle { ids = listOf(1, 3, 4) }
        onNodeWithTag("home-search").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("result:3").assertIsFocused()
    }

    @Test
    fun homeWithoutPriorContentFocusEntersOverviewFromSidebar() = runAniComposeUiTest {
        setContent {
            TvTheme {
                val shell = remember { TvAppShellState() }
                TvAppShell("home-overview", {}, state = shell) {
                    val focus = rememberTvFocusState("home-awaiting-content")
                    TvCatalogueHomeLayout({}, {}, {}, {}, {}, focus,
                        onOverview = { focus.requestFocus("genre:科幻") },
                        onRecommendations = { focus.requestFocus("genre:科幻") },
                    ) { ShellEntry("genre:科幻", focus) }
                    TvSetHomeInitialFocus(focus, null, null, true, true)
                }
            }
        }
        onNodeWithTag("home-search").assertIsFocused().performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("genre:科幻").assertIsFocused()
    }

    @Test
    fun switchingTabsKeepsSidebarAndUpdatesSelectedDestination() = runAniComposeUiTest {
        var destination by mutableStateOf("home-search")
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(960.dp, 540.dp)) {
                    TvAppShell(destination, { destination = it }) {
                        key(destination) {
                            val focus = rememberTvFocusState("content:$destination")
                            TvPage(destination, null, focusState = focus) {
                                ShellEntry("content:$destination", focus)
                            }
                        }
                    }
                }
            }
        }
        onNodeWithTag("content:home-search").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
        }
        onNodeWithTag("home-search").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("home-collections").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("home-history").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter)
        }
        onNodeWithTag("tv-app-sidebar").assertIsDisplayed()
        onNodeWithTag("home-history").assertIsSelected()
        onNodeWithTag("home-search").assertIsNotSelected()
        onNodeWithTag("content:home-history").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
        }
        onNodeWithTag("home-history").assertIsFocused()
    }

    @Test
    fun returningToTabRestoresContentInsteadOfSidebarDestinationUsedToLeaveIt() = runAniComposeUiTest {
        lateinit var navigation: TvAppNavigation
        setContent {
            TvTheme {
                val navigator = remember { AniNavigator() }
                val state = rememberTvAppNavigation(navigator)
                SideEffect { navigation = state }
                val selectedKey = when (state.activeTab) {
                    TvAppTab.Home -> "home-overview"
                    TvAppTab.Search -> "home-search"
                    TvAppTab.Collections -> "home-collections"
                    TvAppTab.History -> "home-history"
                    TvAppTab.Settings -> "home-settings"
                    TvAppTab.Account -> "home-login"
                }
                Box(Modifier.requiredSize(960.dp, 540.dp)) {
                    TvAppShell(selectedKey, { target ->
                        val tab = when (target) {
                            "home-search" -> TvAppTab.Search
                            "home-collections" -> TvAppTab.Collections
                            "home-history" -> TvAppTab.History
                            "home-settings" -> TvAppTab.Settings
                            "home-login" -> TvAppTab.Account
                            else -> TvAppTab.Home
                        }
                        state.selectTab(tab)
                    }) {
                        TvAppNavDisplay(state) {
                            val page = state.activeTab.name
                            val focus = rememberTvFocusState("$page:first")
                            TvPage(page, null, focusState = focus) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ShellEntry("$page:first", focus)
                                    ShellEntry("$page:second", focus)
                                }
                            }
                        }
                    }
                }
            }
        }
        runOnIdle { navigation.selectTab(TvAppTab.Search) }
        onNodeWithTag("Search:first").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("Search:second").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
        }
        onNodeWithTag("home-search").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("home-collections").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("home-history").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter)
        }
        onNodeWithTag("History:first").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
        }
        onNodeWithTag("home-history").assertIsSelected().assertIsFocused().performKeyInput {
            keyDown(Key.DirectionUp); keyUp(Key.DirectionUp)
        }
        onNodeWithTag("home-collections").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionUp); keyUp(Key.DirectionUp)
        }
        onNodeWithTag("home-search").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter)
        }
        onNodeWithTag("home-search").assertIsSelected()
        onNodeWithTag("Search:second").assertIsFocused()
        runOnIdle { assertEquals(TvAppTab.Search, navigation.activeTab) }
    }

    @Test
    fun fullscreenPlayerUsesEntireFrameAndHidesSidebar() = runAniComposeUiTest {
        var fullscreen by mutableStateOf(false)
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(960.dp, 540.dp).testTag("frame")) {
                    TvAppShell("home-history", {}, fullscreen = fullscreen) {
                        if (fullscreen) {
                            Box(Modifier.fillMaxSize().testTag("player"))
                        } else {
                            val focus = rememberTvFocusState("play")
                            TvPage("观看历史", null, focusState = focus) {
                                TvButton("播放", { fullscreen = true }, Modifier.tvFocusTarget("play", focus).testTag("play"))
                            }
                        }
                    }
                }
            }
        }
        onNodeWithTag("tv-app-sidebar").assertIsDisplayed()
        val frame = onNodeWithTag("frame").getUnclippedBoundsInRoot()
        assertTrue(onNodeWithTag("tv-app-content").getUnclippedBoundsInRoot().left > frame.left)
        onNodeWithTag("play").performTvClick()
        onNodeWithTag("tv-app-sidebar").assertDoesNotExist()
        assertEquals(frame, onNodeWithTag("player").getUnclippedBoundsInRoot())
        assertEquals(frame, onNodeWithTag("tv-app-content").getUnclippedBoundsInRoot())
    }

    @Test
    fun compactSidebarKeepsEveryTabVisibleAndReachable() = runAniComposeUiTest {
        val tabs = listOf("home-overview", "home-recommendations", "home-search", "home-collections", "home-history", "home-settings", "home-login")
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(720.dp, 405.dp).testTag("frame")) {
                    TvAppShell("home-overview", {}) {
                        val focus = rememberTvFocusState("entry:1")
                        TvPage("首页", null, focusState = focus) { ShellEntry("entry:1", focus) }
                    }
                }
            }
        }
        val frame = onNodeWithTag("frame").getUnclippedBoundsInRoot()
        tabs.forEach { tag ->
            val bounds = onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$tag 应完整显示在电视视口内", bounds.top >= frame.top && bounds.bottom <= frame.bottom)
        }
        onNodeWithTag("entry:1").performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        tabs.forEachIndexed { index, tag ->
            onNodeWithTag(tag).assertIsFocused()
            if (index < tabs.lastIndex) {
                onNodeWithTag(tag).performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
            }
        }
        onNodeWithTag("home-login").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("entry:1").assertIsFocused()
    }

    @Test
    fun editingArrowKeysMoveCursorAndBackEnablesSidebarNavigation() = runAniComposeUiTest {
        lateinit var field: TextFieldState
        val keyboard = ShellKeyboardController()
        setContent {
            TvTheme {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    Box(Modifier.requiredSize(960.dp, 540.dp)) {
                        TvAppShell("home-search", {}) {
                            val focus = rememberTvFocusState("search-input")
                            val text = rememberTextFieldState("abcdef")
                            SideEffect { field = text }
                            TvPage("搜索", null, focusState = focus) {
                                TvOutlinedTextField(text, Modifier.tvFocusTarget("search-input", focus).testTag("input"))
                            }
                        }
                    }
                }
            }
        }
        onNodeWithTag("input").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter)
        }
        runOnIdle { assertEquals(1, keyboard.showRequests) }
        onNodeWithTag("input").performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        onNodeWithTag("input").assertIsFocused()
        runOnIdle { assertEquals(5, field.selection.start) }
        onNodeWithTag("input").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("input").assertIsFocused()
        runOnIdle { assertEquals(6, field.selection.start) }
        onNodeWithTag("input").performKeyInput { keyDown(Key.Back); keyUp(Key.Back) }
        onNodeWithTag("input").performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        onNodeWithTag("home-search").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
        }
        onNodeWithTag("input").assertIsFocused()
        runOnIdle { assertEquals(1, keyboard.showRequests) }
    }
}

@Composable
private fun ShellEntry(key: String, focus: TvFocusState) {
    TvButton(key, {}, Modifier.tvFocusTarget(key, focus).testTag(key))
}

private class ShellKeyboardController : SoftwareKeyboardController {
    var showRequests = 0
    override fun show() { showRequests++ }
    override fun hide() {}
}
