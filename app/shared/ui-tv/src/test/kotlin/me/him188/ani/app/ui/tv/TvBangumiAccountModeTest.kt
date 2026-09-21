/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import me.him188.ani.app.ui.oauth.AuthState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TvBangumiAccountModeTest {
    @Test
    fun refreshingWhileAwaitingAuthorizationPreservesRegistrationMode() {
        var mode = tvBangumiIsRegister(AuthState.NoAniAccount, null)
        assertEquals(true, mode)
        mode = tvBangumiIsRegister(AuthState.AwaitingResult, mode)
        assertEquals(true, mode)
        assertEquals(true, tvBangumiIsRegister(AuthState.AwaitingResult, mode))
    }

    @Test
    fun accountModeStaysUnavailableUntilTheSessionIsKnownAndPreservesBinding() {
        assertNull(tvBangumiIsRegister(null, null))
        assertNull(tvBangumiIsRegister(AuthState.AwaitingResult, null))
        val mode = tvBangumiIsRegister(AuthState.LoggedInAni(false), null)
        assertEquals(false, mode)
        assertEquals(false, tvBangumiIsRegister(AuthState.AwaitingResult, mode))
    }
}
