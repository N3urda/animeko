/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import kotlinx.serialization.Serializable

/** Device presentation is independent of the operating system and window dimensions. */
@Serializable
enum class DeviceUiMode {
    Auto,
    Television,
    Standard;

    fun useTelevisionUi(
        televisionUiMode: Boolean,
        leanback: Boolean,
        televisionLauncher: Boolean,
    ): Boolean = when (this) {
        Auto -> televisionUiMode || leanback || televisionLauncher
        Television -> true
        Standard -> false
    }
}
