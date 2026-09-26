/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.json.Json
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.navigation.rememberAniBackStack

internal enum class TvAppTab(val rootRoute: NavRoutes) {
    Home(NavRoutes.Main(MainScreenPage.Exploration)),
    Search(NavRoutes.SubjectSearch()),
    Collections(NavRoutes.Main(MainScreenPage.Collection)),
    History(NavRoutes.PlaybackHistory),
    Settings(NavRoutes.Settings()),
    Account(NavRoutes.EmailLoginStart),
}

/** 各入口独立保存页面栈, 标签切换保留搜索条件、列表位置与条目详情. */
@Stable
internal class TvAppNavigation(
    private val navigator: AniNavigator,
    internal val backStacks: Map<TvAppTab, SnapshotStateList<NavRoutes>>,
    activeTabState: MutableState<TvAppTab>,
) {
    var activeTab: TvAppTab by activeTabState
        private set

    val activeBackStack: SnapshotStateList<NavRoutes> get() = backStacks.getValue(activeTab)
    val currentRoute: NavRoutes get() = activeBackStack.last()

    init {
        navigator.setBackStack(activeBackStack)
    }

    fun selectTab(tab: TvAppTab) {
        activeTab = tab
        navigator.setBackStack(activeBackStack)
    }

    fun goHome() {
        selectTab(TvAppTab.Home)
        navigator.popBackStack(TvAppTab.Home.rootRoute, inclusive = false)
    }

    fun popBackStack() {
        if (activeBackStack.size > 1) {
            navigator.popBackStack()
        } else if (activeTab != TvAppTab.Home) {
            goHome()
        }
    }

    /** 登录结束后回到发起登录的页面, 账号入口完成登录后回到首页. */
    fun completeLogin() {
        while (activeBackStack.size > 1 && currentRoute.isTvLoginRoute()) {
            navigator.popBackStack()
        }
        if (activeTab == TvAppTab.Account && activeBackStack.size == 1) goHome()
    }
}

private fun NavRoutes.isTvLoginRoute(): Boolean =
    this == NavRoutes.EmailLoginStart || this == NavRoutes.EmailLoginVerify || this == NavRoutes.BangumiAuthorize

@Composable
internal fun rememberTvAppNavigation(navigator: AniNavigator): TvAppNavigation {
    val activeTab = rememberSaveable { mutableStateOf(TvAppTab.Home) }
    val backStacks = TvAppTab.entries.associateWith { rememberAniBackStack(it.rootRoute) }
    return remember(navigator) { TvAppNavigation(navigator, backStacks, activeTab) }
}

/** 同一条目可以分别存在于不同入口的页面栈中, 状态所有者由入口和路由共同标识. */
internal fun tvAppEntryContentKey(tab: TvAppTab, route: NavRoutes): String =
    "${tab.name}:${Json.encodeToString(NavRoutes.serializer(), route)}"

@Composable
internal fun TvAppNavDisplay(
    state: TvAppNavigation,
    modifier: Modifier = Modifier,
    entryProvider: @Composable (NavRoutes) -> Unit,
) {
    val allEntries = state.backStacks.flatMap { (tab, stack) ->
        stack.map { route ->
            NavEntry(route, contentKey = tvAppEntryContentKey(tab, route)) { entryProvider(it) }
        }
    }
    // 装饰器同时持有全部入口的 key, 只有当前入口的页面参与布局与交互.
    val decoratedEntries = rememberDecoratedNavEntries(
        entries = allEntries,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
    )
    val activeKeys = state.activeBackStack.map { tvAppEntryContentKey(state.activeTab, it) }.toSet()
    NavDisplay(
        entries = decoratedEntries.filter { it.contentKey in activeKeys },
        modifier = modifier,
        onBack = state::popBackStack,
        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
    )
    BackHandler(enabled = state.activeTab != TvAppTab.Home && state.activeBackStack.size == 1) {
        state.goHome()
    }
}
