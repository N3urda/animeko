/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceUiModeTest {
    @Test
    fun explicitStandardOverridesTelevisionHardware() {
        assertFalse(DeviceUiMode.Standard.useTelevisionUi(televisionUiMode = true, leanback = true, televisionLauncher = true))
    }

    @Test
    fun explicitTelevisionSupportsUnidentifiedBoxes() {
        assertTrue(DeviceUiMode.Television.useTelevisionUi(false, false, false))
    }

    @Test
    fun automaticModeRecognizesEachTelevisionSignal() {
        assertTrue(DeviceUiMode.Auto.useTelevisionUi(true, false, false))
        assertTrue(DeviceUiMode.Auto.useTelevisionUi(false, true, false))
        assertTrue(DeviceUiMode.Auto.useTelevisionUi(false, false, true))
    }

    @Test
    fun automaticModePreservesPhoneAndTabletUi() {
        assertFalse(DeviceUiMode.Auto.useTelevisionUi(false, false, false))
    }
}
