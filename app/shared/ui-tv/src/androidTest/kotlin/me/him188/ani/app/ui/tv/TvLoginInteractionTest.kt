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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.data.repository.user.UserRepository.SendOtpResult
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.login.EmailLoginUiState
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvLoginInteractionTest {
    @Test
    fun sendFailureDoesNotClaimOtpWasSentAndKeepsRetryAvailable() = runAniComposeUiTest {
        var requests = 0
        setContent {
            TvTheme {
                Box(Modifier.height(540.dp)) {
                    TvLoginForm(
                        mode = EmailLoginUiState.Mode.LOGIN,
                        onSend = { requests++; error("offline") },
                        onSubmit = { SendOtpResult.InvalidOtp },
                        onSuccess = {}, onBack = {},
                    )
                }
            }
        }
        onNodeWithTag("tv-login-email").assertIsFocused().performTextReplacement("test@example.com")
        onNodeWithTag("tv-login-send").performTvClick()
        onNodeWithTag("tv-login-status").assertIsDisplayed()
        onNodeWithText("验证码发送失败，请检查邮箱和网络后重试。").assertIsDisplayed()
        onNodeWithTag("tv-login-otp").assertIsNotEnabled()
        onNodeWithTag("tv-login-send").assertIsFocused()
        runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun successfulSendFocusesCodeAndSuccessfulSubmitReturnsOnce() = runAniComposeUiTest {
        var returned = 0
        setContent {
            TvTheme {
                Box(Modifier.height(540.dp)) {
                    TvLoginForm(
                        mode = EmailLoginUiState.Mode.LOGIN,
                        onSend = {}, onSubmit = { SendOtpResult.Success(SelfInfo(Uuid.random(), "测试", "test@example.com", false, null, null)) },
                        onSuccess = { returned++ }, onBack = {},
                    )
                }
            }
        }
        onNodeWithTag("tv-login-email").performTextReplacement("test@example.com")
        onNodeWithTag("tv-login-send").performTvClick()
        onNodeWithTag("tv-login-otp").assertIsFocused().performTextReplacement("123456")
        onNodeWithTag("tv-login-submit").assertIsDisplayed().performTvClick()
        runOnIdle { assertEquals(1, returned) }
    }
    @Test
    fun keyboardSizedViewportKeepsEmailSendAndFailureReadable() = runAniComposeUiTest {
        setContent {
            TvTheme {
                Box(Modifier.height(240.dp)) {
                    TvLoginForm(
                        mode = EmailLoginUiState.Mode.LOGIN,
                        onSend = { error("offline") }, onSubmit = { SendOtpResult.InvalidOtp },
                        onSuccess = {}, onBack = {},
                    )
                }
            }
        }
        onNodeWithTag("tv-login-email").assertIsFocused().assertHeightIsAtLeast(56.dp)
            .performTextReplacement("test@example.com")
        onNodeWithTag("tv-login-send").assertHeightIsAtLeast(48.dp).performTvClick()
        onNodeWithText("验证码发送失败，请检查邮箱和网络后重试。").assertIsDisplayed()
        onNodeWithTag("tv-login-email").assertHeightIsAtLeast(56.dp)
        onNodeWithTag("tv-login-send").assertIsFocused().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun extremelyShortViewportKeepsTheFocusedEmailAndOtpFullyVisible() = runAniComposeUiTest {
        var viewport by mutableStateOf(405.dp)
        setContent {
            TvTheme {
                Box(Modifier.height(viewport).testTag("login-viewport")) {
                    TvLoginForm(
                        mode = EmailLoginUiState.Mode.LOGIN,
                        onSend = {}, onSubmit = { SendOtpResult.InvalidOtp },
                        onSuccess = {}, onBack = {},
                    )
                }
            }
        }
        onNodeWithTag("tv-login-email").assertIsFocused().performTextReplacement("test@example.com")
        fun assertFullyVisible(tag: String) {
            val viewportBounds = onNodeWithTag("login-viewport").getUnclippedBoundsInRoot()
            val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag must fit inside the visible viewport", bounds.top >= viewportBounds.top && bounds.bottom <= viewportBounds.bottom)
        }
        runOnIdle { viewport = 105.dp }
        onNodeWithTag("tv-login-email").assertIsFocused().assertHeightIsAtLeast(56.dp)
        assertFullyVisible("tv-login-email")
        assertFullyVisible("tv-login-send")
        onNodeWithTag("tv-login-send").assertHeightIsAtLeast(48.dp).performTvClick()
        onNodeWithTag("tv-login-otp").assertIsFocused().assertHeightIsAtLeast(56.dp)
            .performTextReplacement("123456")
        onNodeWithTag("tv-login-submit").assertHeightIsAtLeast(48.dp)
        assertFullyVisible("tv-login-otp")
        assertFullyVisible("tv-login-submit")
        runOnIdle { viewport = 405.dp }
        onNodeWithTag("tv-login-otp").assertIsFocused().assertHeightIsAtLeast(56.dp)
    }

}
