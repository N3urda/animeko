/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import me.him188.ani.app.domain.foundation.VersionExpiryService
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.navigation.rememberAniBackStack
import me.him188.ani.app.platform.DeviceUiMode
import me.him188.ani.app.platform.navigation.LocalBrowserNavigator
import me.him188.ani.app.ui.main.AniAppViewModel
import org.koin.mp.KoinPlatform

@Composable
fun TvAppContent(
    navigator: AniNavigator,
    deviceUiMode: DeviceUiMode,
    onDeviceUiMode: (DeviceUiMode) -> Unit,
) {
    val app = viewModel<AniAppViewModel>()
    val backStack = rememberAniBackStack(NavRoutes.Main(MainScreenPage.Exploration))
    navigator.setBackStack(backStack)
    val expiry = remember { KoinPlatform.getKoin().get<VersionExpiryService>() }
    val expired by expiry.state.collectAsStateWithLifecycle()
    TvTheme {
        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalBrowserNavigator provides app.browserNavigator,
        ) {
            if (expired != null) {
                TvPage("需要更新 Animeko", null) {
                    TvMessage("当前版本已停止服务", "请安装最新的电视测试版。最新服务端版本：${expired?.latestVersion ?: "未知"}。现有设置和观看记录保留。")
                }
                return@CompositionLocalProvider
            }
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = navigator::popBackStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                // 电视页面直接切换, 避免同时合成进出两页的大面积过渡.
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                entryProvider = { route ->
                    NavEntry(route) {
                        val back: () -> Unit = navigator::popBackStack
                        val subject: (Int) -> Unit = { navigator.navigateSubjectDetails(it, null) }
                        val episode: (Int, Int) -> Unit = { s, e -> navigator.navigateEpisodeDetails(s, e) }
                        when (route) {
                            is NavRoutes.Main -> if (route.initialPage == MainScreenPage.Collection) {
                                TvCollectionsScreen(subject, navigator::navigateLogin, back)
                            } else {
                                TvHomeScreen(
                                    onSubject = subject,
                                    onSearch = { navigator.navigateSubjectSearch() },
                                    onCollections = { navigator.navigateMain(MainScreenPage.Collection) },
                                    onHistory = navigator::navigatePlaybackHistory,
                                    onSettings = { navigator.navigateSettings() },
                                    onLogin = navigator::navigateLogin,
                                    onSearchTag = { navigator.navigateSubjectSearch(it) },
                                )
                            }
                            is NavRoutes.SubjectSearch -> TvSearchScreen(subject, back, initialKeyword = route.keyword, initialTags = route.tags)
                            is NavRoutes.SubjectDetail -> TvSubjectScreen(route.subjectId, episode, back)
                            is NavRoutes.EpisodeDetail -> TvPlayerScreen(route.subjectId, route.episodeId, back)
                            NavRoutes.PlaybackHistory -> TvHistoryScreen(episode, back, onLogin = navigator::navigateLogin)
                            is NavRoutes.Settings, NavRoutes.Caches -> TvSettingsScreen(deviceUiMode, onDeviceUiMode, navigator::navigateLogin, back, navigator::navigateBangumiAuthorize)
                            NavRoutes.EmailLoginStart, NavRoutes.EmailLoginVerify -> TvLoginScreen(
                                onSuccess = { navigator.popBackOrNavigateToMain(MainScreenPage.Exploration) },
                                onBack = back,
                                onBangumi = navigator::navigateBangumiAuthorize,
                            )
                            NavRoutes.BangumiAuthorize -> TvBangumiAuthorizeScreen(
                                onSuccess = {
                                    navigator.popBackStack(NavRoutes.BangumiAuthorize, true)
                                    navigator.popBackStack(NavRoutes.EmailLoginVerify, true)
                                    navigator.popBackStack(NavRoutes.EmailLoginStart, true)
                                },
                                onBack = back,
                            )
                            else -> TvPage("电视端功能", back) {
                                TvMessage("此功能请在手机端操作", "电视端支持邮箱或 Bangumi 登录、收藏、搜索、播放和数据源设置。")
                                TvButton("邮箱登录", navigator::navigateLogin)
                            }
                        }
                    }
                },
            )
        }
    }
}
