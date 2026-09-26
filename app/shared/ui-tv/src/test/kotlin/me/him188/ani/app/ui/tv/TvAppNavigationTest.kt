/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.NavRoutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TvAppNavigationTest {
    @Test
    fun switchingTabsKeepsEachStackAndDoesNotAddRoutes() {
        val navigator = AniNavigator()
        val state = navigation(navigator)
        state.selectTab(TvAppTab.Search)
        navigator.navigateSubjectDetails(123, null)
        val searchStack = state.activeBackStack
        repeat(20) {
            state.selectTab(TvAppTab.History)
            state.selectTab(TvAppTab.Search)
        }
        assertSame(searchStack, state.activeBackStack)
        assertEquals(listOf(TvAppTab.Search.rootRoute, NavRoutes.SubjectDetail(123)), state.activeBackStack)
        assertEquals(listOf(TvAppTab.History.rootRoute), state.backStacks.getValue(TvAppTab.History))
        assertSame(state.activeBackStack, navigator.backStack)
    }

    @Test
    fun backPopsTheCurrentTabThenReturnsToHome() {
        val navigator = AniNavigator()
        val state = navigation(navigator)
        state.selectTab(TvAppTab.Search)
        navigator.navigateSubjectDetails(123, null)
        state.popBackStack()
        assertEquals(TvAppTab.Search, state.activeTab)
        assertEquals(TvAppTab.Search.rootRoute, state.currentRoute)
        state.popBackStack()
        assertEquals(TvAppTab.Home, state.activeTab)
        state.popBackStack()
        assertEquals(listOf(TvAppTab.Home.rootRoute), state.activeBackStack)
    }

    @Test
    fun goHomeReturnsToTheHomeRootEvenWhenItHasDetails() {
        val navigator = AniNavigator()
        val state = navigation(navigator)
        navigator.navigateSubjectDetails(456, null)
        state.selectTab(TvAppTab.History)
        state.goHome()
        assertEquals(listOf(TvAppTab.Home.rootRoute), state.activeBackStack)
        assertEquals(listOf(TvAppTab.History.rootRoute), state.backStacks.getValue(TvAppTab.History))
    }

    @Test
    fun loginFromCollectionsReturnsToCollections() {
        val navigator = AniNavigator()
        val state = navigation(navigator)
        state.selectTab(TvAppTab.Collections)
        navigator.navigateLogin()
        navigator.navigateEmailLoginVerify()
        navigator.navigateBangumiAuthorize()
        state.completeLogin()
        assertEquals(TvAppTab.Collections, state.activeTab)
        assertEquals(listOf(TvAppTab.Collections.rootRoute), state.activeBackStack)
    }

    @Test
    fun loginFromAccountReturnsHomeWithoutReplacingAccountRoot() {
        val navigator = AniNavigator()
        val state = navigation(navigator)
        state.selectTab(TvAppTab.Account)
        navigator.navigateBangumiAuthorize()
        state.completeLogin()
        assertEquals(TvAppTab.Home, state.activeTab)
        assertEquals(listOf(TvAppTab.Account.rootRoute), state.backStacks.getValue(TvAppTab.Account))
    }

    @Test
    fun identicalSubjectRoutesHaveDistinctOwnersAcrossTabs() {
        val route = NavRoutes.SubjectDetail(123)
        assertNotEquals(tvAppEntryContentKey(TvAppTab.Home, route), tvAppEntryContentKey(TvAppTab.Search, route))
        assertEquals(tvAppEntryContentKey(TvAppTab.Search, route), tvAppEntryContentKey(TvAppTab.Search, route.copy()))
    }

    @Test
    fun restoredActiveTabBindsNavigatorToItsSavedStack() {
        val navigator = AniNavigator()
        val stacks = TvAppTab.entries.associateWith { mutableStateListOf(it.rootRoute) }
        stacks.getValue(TvAppTab.History).add(NavRoutes.EpisodeDetail(123, 456))
        val state = TvAppNavigation(navigator, stacks, mutableStateOf(TvAppTab.History))
        assertSame(stacks.getValue(TvAppTab.History), navigator.backStack)
        assertEquals(NavRoutes.EpisodeDetail(123, 456), state.currentRoute)
    }

    private fun navigation(navigator: AniNavigator): TvAppNavigation = TvAppNavigation(
        navigator,
        TvAppTab.entries.associateWith { mutableStateListOf(it.rootRoute) },
        mutableStateOf(TvAppTab.Home),
    )
}
