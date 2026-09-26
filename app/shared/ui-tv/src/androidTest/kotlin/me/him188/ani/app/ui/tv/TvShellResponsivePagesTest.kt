/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.user.UserRepository.SendOtpResult
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.login.EmailLoginUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 568dp 内容区对应 720dp 电视窗口保留全局侧栏后的可用宽度. */
@OptIn(ExperimentalTestApi::class)
class TvShellResponsivePagesTest {
    @Test
    fun compactSearchKeepsReadableInputAndRemoteAccessToFilters() = runAniComposeUiTest {
        var filters = 0
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(568.dp, 405.dp).testTag("page-viewport")) {
                    val focus = rememberTvFocusState("search-input")
                    TvPage("搜索番剧", {}, focusState = focus) {
                        TvSearchControls(rememberTextFieldState("葬送的芙莉莲"), true, {}, { filters++ }, focus)
                    }
                }
            }
        }
        onNodeWithTag("tv-search-input").assertIsFocused().assertWidthIsAtLeast(240.dp)
            .performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-search-submit").assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-search-filters").assertIsFocused().assertIsDisplayed().performTvClick()
        val viewport = onNodeWithTag("page-viewport").getUnclippedBoundsInRoot()
        val filtersBounds = onNodeWithTag("tv-search-filters").getUnclippedBoundsInRoot()
        assertTrue(filtersBounds.right <= viewport.right)
        runOnIdle { assertEquals(1, filters) }
    }

    @Test
    fun compactSettingsKeepsContentReadableAndCategoryRoundTrip() = runAniComposeUiTest {
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(568.dp, 405.dp)) {
                    var category by remember { mutableStateOf("播放") }
                    TvSettingsLayout(category, { category = it },
                        listOf(TvSettingsItem("option", "播放失败自动换源", "当前资源无法播放时尝试其他资源", onClick = {})), {})
                }
            }
        }
        onNodeWithTag("tv-settings-播放").assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-settings-row-option").assertIsFocused().assertWidthIsAtLeast(360.dp)
            .performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        onNodeWithTag("tv-settings-播放").assertIsFocused()
    }

    @Test
    fun compactLoginKeepsEmailAndCodeActionsVisible() = runAniComposeUiTest {
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(568.dp, 405.dp).testTag("page-viewport")) {
                    TvLoginForm(EmailLoginUiState.Mode.LOGIN, {}, { SendOtpResult.InvalidOtp }, {}, {}, onBangumi = {})
                }
            }
        }
        onNodeWithTag("tv-login-bangumi").assertIsDisplayed()
        onNodeWithTag("tv-login-email").assertIsFocused().assertWidthIsAtLeast(300.dp)
            .performTextReplacement("viewer@example.com")
        onNodeWithTag("tv-login-send").assertIsDisplayed().performTvClick()
        onNodeWithTag("tv-login-otp").assertIsFocused().assertIsDisplayed()
            .performTextReplacement("123456")
        onNodeWithTag("tv-login-submit").assertIsDisplayed()
        val viewport = onNodeWithTag("page-viewport").getUnclippedBoundsInRoot()
        val submit = onNodeWithTag("tv-login-submit").getUnclippedBoundsInRoot()
        assertTrue(submit.right <= viewport.right && submit.bottom <= viewport.bottom)
    }

    @Test
    fun compactAuthorizationKeepsQrVisibleWhileRemoteReachesCancel() = runAniComposeUiTest {
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(568.dp, 405.dp).testTag("page-viewport")) {
                    TvBangumiAuthorizeContent(
                        TvOAuthRequestState.Awaiting("https://bgm.tv/oauth/authorize?client_id=test&state=sample"),
                        false, {}, {}, {},
                    )
                }
            }
        }
        onNodeWithTag("tv-oauth-start").assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        onNodeWithTag("tv-oauth-browser").assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        onNodeWithTag("tv-oauth-back").assertIsFocused().assertIsDisplayed()
        onNodeWithTag("tv-oauth-qr").assertIsDisplayed().assertWidthIsAtLeast(176.dp)
        val viewport = onNodeWithTag("page-viewport").getUnclippedBoundsInRoot()
        val qr = onNodeWithTag("tv-oauth-qr").getUnclippedBoundsInRoot()
        assertTrue(qr.left >= viewport.left && qr.right <= viewport.right && qr.top >= viewport.top && qr.bottom <= viewport.bottom)
    }
}
