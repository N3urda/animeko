/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvBangumiAuthorizeTest {
    @Test
    fun remoteStartsAuthorizationAndCanReturnWithoutOpeningABrowser() = runAniComposeUiTest {
        var starts = 0; var backs = 0; var browsers = 0
        setContent { TvTheme { TvBangumiAuthorizeContent(TvOAuthRequestState.Idle, false, { starts++ }, { browsers++ }, { backs++ }) } }
        onNodeWithTag("tv-oauth-start").assertIsFocused().performTvClick()
        onNodeWithTag("tv-oauth-back").performTvClick()
        runOnIdle { assertEquals(1, starts); assertEquals(1, backs); assertEquals(0, browsers) }
    }

    @Test
    fun activeRequestShowsQrAndBrowserFailureKeepsScanAvailable() = runAniComposeUiTest {
        val url = "https://bgm.tv/oauth/authorize?client_id=test&state=sample"
        var opened: String? = null
        setContent { TvTheme { TvBangumiAuthorizeContent(TvOAuthRequestState.Awaiting(url), false, {}, { opened = it }, {}, browserError = true) } }
        onNodeWithTag("tv-oauth-qr").assertIsDisplayed()
        onNodeWithText("电视未能打开浏览器，请继续用手机扫码。").assertIsDisplayed()
        onNodeWithTag("tv-oauth-browser").performTvClick()
        runOnIdle { assertEquals(url, opened) }
    }

    @Test
    fun expiredRequestHasNoQrAndOffersRemoteRetry() = runAniComposeUiTest {
        var starts = 0
        setContent { TvTheme { TvBangumiAuthorizeContent(TvOAuthRequestState.Expired, true, { starts++ }, {}, {}) } }
        onNodeWithTag("tv-oauth-qr").assertDoesNotExist()
        onNodeWithText("二维码已超时，请重新获取。").assertIsDisplayed()
        onNodeWithTag("tv-oauth-start").performTvClick()
        runOnIdle { assertEquals(1, starts) }
    }
}
