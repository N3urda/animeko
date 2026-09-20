/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme as MobileMaterialTheme
import androidx.compose.material3.LocalContentColor as MobileLocalContentColor
import androidx.compose.material3.darkColorScheme as mobileDarkColorScheme

/** 电视界面的固定深色主题, 为焦点状态提供足够的明度对比. */
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    val background = Color(0xFF0C101B)
    val surface = Color(0xFF192231)
    val primary = Color(0xFFC4D7FF)
    MobileMaterialTheme(
        colorScheme = mobileDarkColorScheme(
            background = background,
            surface = surface,
            surfaceVariant = Color(0xFF202D40),
            onSurface = Color(0xFFF1F4FA),
            onSurfaceVariant = Color(0xFFBAC6D8),
            primary = primary,
        ),
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = background,
                surface = surface,
                surfaceVariant = Color(0xFF202D40),
                onSurface = Color(0xFFF1F4FA),
                onSurfaceVariant = Color(0xFFBAC6D8),
                primary = primary,
                onPrimary = Color(0xFF112745),
                primaryContainer = Color(0xFF26466D),
                onPrimaryContainer = Color.White,
            ),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                MobileLocalContentColor provides MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}
