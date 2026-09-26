/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvAppNavigationStateTest {
    @Test
    fun tabChangesRetainSaveableStateAndViewModelInstances() = runAniComposeUiTest {
        lateinit var state: TvAppNavigation
        val instances = mutableMapOf<String, NavigationCounterViewModel>()
        setContent { NavigationHarness(instances, onReady = { state = it }) }
        repeat(2) { onNodeWithTag("increase").performTvClick() }
        runOnIdle { state.selectTab(TvAppTab.Search) }
        onNodeWithTag("increase").performTvClick()
        runOnIdle { state.selectTab(TvAppTab.History) }
        repeat(3) { onNodeWithTag("increase").performTvClick() }
        repeat(3) {
            runOnIdle { state.selectTab(TvAppTab.Home) }
            onNodeWithTag("counter").assertTextEquals("2 / 2")
            runOnIdle { state.selectTab(TvAppTab.Search) }
            onNodeWithTag("counter").assertTextEquals("1 / 1")
            runOnIdle { state.selectTab(TvAppTab.History) }
            onNodeWithTag("counter").assertTextEquals("3 / 3")
        }
        runOnIdle {
            assertEquals(3, instances.size)
            assertFalse(instances.values.any { it.cleared })
            assertEquals(6, state.backStacks.values.sumOf { it.size })
        }
    }

    @Test
    fun identicalDetailsInTwoTabsHaveIndependentSavedStateAndViewModels() = runAniComposeUiTest {
        lateinit var state: TvAppNavigation
        lateinit var navigator: AniNavigator
        val instances = mutableMapOf<String, NavigationCounterViewModel>()
        setContent { NavigationHarness(instances, onReady = { state = it }, onNavigator = { navigator = it }) }
        runOnIdle { navigator.navigateSubjectDetails(123, null) }
        repeat(2) { onNodeWithTag("increase").performTvClick() }
        runOnIdle {
            state.selectTab(TvAppTab.Search)
            navigator.navigateSubjectDetails(123, null)
        }
        onNodeWithTag("increase").performTvClick()
        runOnIdle { state.selectTab(TvAppTab.Home) }
        onNodeWithTag("counter").assertTextEquals("2 / 2")
        runOnIdle { state.selectTab(TvAppTab.Search) }
        onNodeWithTag("counter").assertTextEquals("1 / 1")
        runOnIdle {
            val route = NavRoutes.SubjectDetail(123)
            val home = instances.getValue(tvAppEntryContentKey(TvAppTab.Home, route))
            val search = instances.getValue(tvAppEntryContentKey(TvAppTab.Search, route))
            assertNotSame(home, search)
            assertFalse(home.cleared)
            assertFalse(search.cleared)
        }
    }

    @Test
    fun returningFromDetailsAndAnotherTabRestoresTheFocusedCard() = runAniComposeUiTest {
        lateinit var state: TvAppNavigation
        setContent { NavigationHarness(mutableMapOf(), onReady = { state = it }) }
        onNodeWithTag("second-card").performTvClick()
        onNodeWithTag("tv-back").performTvClick()
        onNodeWithTag("second-card").assertIsFocused()
        runOnIdle { state.selectTab(TvAppTab.History) }
        runOnIdle { state.selectTab(TvAppTab.Home) }
        onNodeWithTag("second-card").assertIsFocused()
    }

    @Test
    fun systemBackPopsDetailsThenGoesHomeAndFinallyPassesToTheHost() = runAniComposeUiTest {
        lateinit var state: TvAppNavigation
        lateinit var navigator: AniNavigator
        lateinit var dispatcher: OnBackPressedDispatcher
        var hostBackCount = 0
        setContent {
            val currentDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SideEffect { dispatcher = currentDispatcher }
            BackHandler { hostBackCount++ }
            NavigationHarness(mutableMapOf(), onReady = { state = it }, onNavigator = { navigator = it })
        }
        runOnIdle {
            state.selectTab(TvAppTab.Search)
            navigator.navigateSubjectDetails(123, null)
        }
        runOnIdle { dispatcher.onBackPressed() }
        runOnIdle {
            assertEquals(TvAppTab.Search, state.activeTab)
            assertEquals(TvAppTab.Search.rootRoute, state.currentRoute)
            assertEquals(0, hostBackCount)
        }
        runOnIdle { dispatcher.onBackPressed() }
        runOnIdle {
            assertEquals(TvAppTab.Home, state.activeTab)
            assertEquals(0, hostBackCount)
        }
        runOnIdle { dispatcher.onBackPressed() }
        runOnIdle { assertEquals(1, hostBackCount) }
    }
}

private class NavigationCounterViewModel : ViewModel() {
    var count by mutableIntStateOf(0)
    var cleared = false
        private set

    override fun onCleared() {
        cleared = true
    }
}

@Composable
private fun NavigationHarness(
    instances: MutableMap<String, NavigationCounterViewModel>,
    onReady: (TvAppNavigation) -> Unit,
    onNavigator: (AniNavigator) -> Unit = {},
) {
    val navigator = remember { AniNavigator() }
    val state = rememberTvAppNavigation(navigator)
    SideEffect {
        onReady(state)
        onNavigator(navigator)
    }
    TvTheme {
        TvAppNavDisplay(state) { route ->
            val ownerKey = tvAppEntryContentKey(state.activeTab, route)
            val model = viewModel { NavigationCounterViewModel().also { instances[ownerKey] = it } }
            var savedCount by rememberSaveable { mutableIntStateOf(0) }
            val focus = rememberTvFocusState(if (route is NavRoutes.SubjectDetail) "page-back" else "first-card")
            TvPage("导航测试", state::popBackStack, focusState = focus) {
                Text("$savedCount / ${model.count}", Modifier.testTag("counter"))
                TvButton("增加", { savedCount++; model.count++ }, Modifier.tvFocusTarget("increase", focus).testTag("increase"))
                if (route !is NavRoutes.SubjectDetail) {
                    TvButton("第一张", { navigator.navigateSubjectDetails(11, null) }, Modifier.tvFocusTarget("first-card", focus).testTag("first-card"))
                    TvButton("第二张", { navigator.navigateSubjectDetails(22, null) }, Modifier.tvFocusTarget("second-card", focus).testTag("second-card"))
                }
            }
        }
    }
}
