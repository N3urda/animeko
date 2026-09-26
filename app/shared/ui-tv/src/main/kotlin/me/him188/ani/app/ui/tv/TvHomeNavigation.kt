/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** 概览栏按内容顺序上下移动, 首项左移进入首页侧栏. */
internal fun tvHomeOverviewNavigation(focus: TvFocusState, key: Key, groups: List<String>): Boolean {
    val groupIndex = groups.indexOfFirst { focus.requestedKey.startsWith(it) }
    if (groupIndex < 0) return false
    val currentKeys = focus.groups[groups[groupIndex]]?.keys.orEmpty()
    val target = when (key) {
        Key.DirectionLeft -> if (focus.requestedKey == currentKeys.firstOrNull()) "home-overview" else return false
        Key.DirectionUp -> groups.take(groupIndex).asReversed().firstNotNullOfOrNull { focus.groups[it]?.keys?.firstOrNull() }
            ?: "home-overview"
        Key.DirectionDown -> groups.drop(groupIndex + 1).firstNotNullOfOrNull { focus.groups[it]?.keys?.firstOrNull() }
            ?: "recommended:"
        else -> return false
    }
    focus.requestFocus(target)
    return true
}

internal fun Modifier.tvHomeRecommendationFooterKeys(
    focus: TvFocusState,
    entries: List<TvRailPoster>,
    previousKey: String,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else {
            when (event.key) {
                Key.DirectionLeft -> focus.requestFocus("home-recommendations")
                Key.DirectionUp -> focus.requestFocus(entries.lastOrNull()?.poster?.id?.let { "recommended:$it" } ?: previousKey)
                Key.DirectionRight, Key.DirectionDown -> Unit
                else -> return@onPreviewKeyEvent false
            }
            true
        }
    }
