/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvOAuthRequestTest {
    @Test
    fun realAuthorizationUrlWaitsForSuccessfulSessionResult() = runTest {
        val result = CompletableDeferred<Boolean>()
        val request = TvOAuthRequest(backgroundScope, { show -> show("https://bgm.tv/oauth/authorize?state=test"); result.await() }, {})
        request.start(); runCurrent()
        assertEquals(TvOAuthRequestState.Awaiting("https://bgm.tv/oauth/authorize?state=test"), request.state.value)
        result.complete(true); runCurrent()
        assertEquals(TvOAuthRequestState.Success, request.state.value)
    }

    @Test
    fun refreshingInvalidatesOldUrlAndLateCallback() = runTest {
        val callbacks = mutableListOf<(String) -> Unit>()
        val request = TvOAuthRequest(backgroundScope, { show -> callbacks += show; awaitCancellation() }, {})
        request.start(); runCurrent()
        assertEquals(1, callbacks.size)
        callbacks[0]("https://example.org/old")
        request.start(); runCurrent()
        assertEquals(TvOAuthRequestState.Loading, request.state.value)
        callbacks[0]("https://example.org/stale")
        assertEquals(TvOAuthRequestState.Loading, request.state.value)
        assertEquals(2, callbacks.size)
        callbacks[1]("https://example.org/new")
        assertEquals(TvOAuthRequestState.Awaiting("https://example.org/new"), request.state.value)
        request.cancel(); runCurrent()
        callbacks[1]("https://example.org/late")
        assertEquals(TvOAuthRequestState.Idle, request.state.value)
    }

    @Test
    fun timeoutRemovesQrAndStopsUnderlyingAuthorization() = runTest {
        var cancelled = 0
        val request = TvOAuthRequest(backgroundScope, { show -> show("https://example.org/auth"); awaitCancellation() }, { cancelled++ }, timeoutMillis = 1000)
        request.start(); runCurrent()
        advanceTimeBy(1001); runCurrent()
        assertEquals(TvOAuthRequestState.Expired, request.state.value)
        assertTrue(cancelled > 0)
    }

    @Test
    fun failureIsRetryableAndDoesNotClaimSuccess() = runTest {
        var attempt = 0
        val request = TvOAuthRequest(backgroundScope, { if (++attempt == 1) error("offline") else true }, {})
        request.start(); runCurrent()
        assertEquals(TvOAuthRequestState.Failed, request.state.value)
        request.start(); runCurrent()
        assertEquals(TvOAuthRequestState.Success, request.state.value)
    }
}
