/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.data.repository.user.UserRepository.SendOtpResult
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.login.EmailLoginUiState
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvLoginTabRestorationTest {
    @Test
    fun leavingAccountTabAfterSendingCodePreservesSubmissionAndResendDeadline() = runAniComposeUiTest {
        lateinit var navigation: TvAppNavigation
        lateinit var owner: LoginRequestTestOwner
        val submittedCodes = mutableListOf<String>()
        setContent {
            val navigator = remember { AniNavigator() }
            val state = rememberTvAppNavigation(navigator)
            SideEffect { navigation = state }
            TvTheme {
                TvAppNavDisplay(state) { route ->
                    if (route == NavRoutes.EmailLoginStart) {
                        val requestOwner = viewModel { LoginRequestTestOwner() }
                        SideEffect { owner = requestOwner }
                        key(requestOwner) {
                            TvLoginForm(
                                mode = EmailLoginUiState.Mode.LOGIN,
                                onSend = { requestOwner.sendCount++ },
                                onSubmit = { code -> submittedCodes += code; SendOtpResult.InvalidOtp },
                                onSuccess = {}, onBack = state::popBackStack,
                            )
                        }
                    } else {
                        TvPage("首页", null) { TvMessage("首页内容") }
                    }
                }
            }
        }
        runOnIdle { navigation.selectTab(TvAppTab.Account) }
        onNodeWithTag("tv-login-email").performTextReplacement("viewer@example.com")
        onNodeWithTag("tv-login-send").performTvClick()
        onNodeWithTag("tv-login-otp").assertIsEnabled().performTextReplacement("123456")
        onNodeWithTag("tv-login-submit").assertIsEnabled()
        runOnIdle { navigation.selectTab(TvAppTab.Home) }
        onNodeWithTag("tv-login-otp").assertDoesNotExist()
        runOnIdle { navigation.selectTab(TvAppTab.Account) }
        onNodeWithTag("tv-login-email").assertTextContains("viewer@example.com")
        onNodeWithTag("tv-login-otp").assertIsEnabled().assertTextContains("123456")
        onNodeWithTag("tv-login-send").assertIsNotEnabled()
        onNodeWithTag("tv-login-submit").assertIsEnabled().performTvClick()
        runOnIdle {
            assertEquals(1, owner.sendCount)
            assertEquals(listOf("123456"), submittedCodes)
        }
        onNodeWithTag("tv-login-email").performTextReplacement("another@example.com")
        onNodeWithTag("tv-login-otp").assertIsNotEnabled()
        onNodeWithTag("tv-login-submit").assertIsNotEnabled()
    }
}

private class LoginRequestTestOwner : ViewModel() {
    var sendCount = 0
}
