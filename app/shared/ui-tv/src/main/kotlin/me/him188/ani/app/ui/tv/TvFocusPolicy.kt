/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

internal fun tvFocusReplacement(requested: String, keys: List<String>, oldIndex: Int, fallback: String): String =
    requested.takeIf { it in keys } ?: keys.getOrNull(oldIndex.coerceIn(0, keys.lastIndex.coerceAtLeast(0))) ?: fallback

internal data class TvPagingFocusSnapshot(
    val itemCount: Int,
    val targetPresent: Boolean,
    val appendLoading: Boolean,
    val hasMore: Boolean,
    val hasError: Boolean,
)

internal enum class TvPagingFocusAction { FOUND, REQUEST_PAGE, WAIT, FALLBACK }

/** 一个焦点恢复周期最多请求两页; UI 同时以三秒限制等待总时间. */
internal class TvPagingFocusBudget {
    private var hints = 0
    private var hintedItemCount = -1

    fun next(snapshot: TvPagingFocusSnapshot): TvPagingFocusAction = when {
        snapshot.targetPresent -> TvPagingFocusAction.FOUND
        snapshot.hasError || !snapshot.hasMore || snapshot.itemCount == 0 -> TvPagingFocusAction.FALLBACK
        snapshot.appendLoading -> TvPagingFocusAction.WAIT
        hints >= 2 -> TvPagingFocusAction.FALLBACK
        snapshot.itemCount == hintedItemCount -> TvPagingFocusAction.WAIT
        else -> {
            hints++
            hintedItemCount = snapshot.itemCount
            TvPagingFocusAction.REQUEST_PAGE
        }
    }
}

internal fun tvHomeInitialFocus(
    followedKey: String?, trendingKey: String?, followedReady: Boolean, trendingReady: Boolean,
): String? = when {
    followedKey != null -> followedKey
    !followedReady -> null
    trendingKey != null -> trendingKey
    !trendingReady -> null
    else -> "home-search"
}
