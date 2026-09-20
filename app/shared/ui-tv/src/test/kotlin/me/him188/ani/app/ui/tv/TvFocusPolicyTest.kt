/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TvFocusPolicyTest {
    @Test
    fun removedItemUsesPreviousPositionAndClampsToLastItem() {
        assertEquals("11", tvFocusReplacement("22", listOf("33", "11"), 1, "back"))
        assertEquals("33", tvFocusReplacement("22", listOf("33"), 8, "back"))
        assertEquals("back", tvFocusReplacement("22", emptyList(), 8, "back"))
    }

    @Test
    fun existingBusinessIdTakesPriorityOverPreviousPosition() {
        assertEquals("35", tvFocusReplacement("35", listOf("35", "11"), 34, "back"))
    }

    @Test
    fun disappearedIdStopsAfterTwoAdditionalPageHints() {
        val budget = TvPagingFocusBudget()
        assertEquals(TvPagingFocusAction.REQUEST_PAGE, budget.next(page(30)))
        assertEquals(TvPagingFocusAction.WAIT, budget.next(page(30, loading = true)))
        assertEquals(TvPagingFocusAction.REQUEST_PAGE, budget.next(page(60)))
        assertEquals(TvPagingFocusAction.WAIT, budget.next(page(60, loading = true)))
        assertEquals(TvPagingFocusAction.FALLBACK, budget.next(page(90)))
        assertEquals(TvPagingFocusAction.FALLBACK, budget.next(page(120)))
    }

    @Test
    fun targetOnNextPageStopsFurtherRequests() {
        val budget = TvPagingFocusBudget()
        assertEquals(TvPagingFocusAction.REQUEST_PAGE, budget.next(page(30)))
        assertEquals(TvPagingFocusAction.FOUND, budget.next(page(60).copy(targetPresent = true)))
    }

    @Test
    fun endOrFailureAllowsImmediateFallback() {
        assertEquals(TvPagingFocusAction.FALLBACK, TvPagingFocusBudget().next(page(30).copy(hasMore = false)))
        assertEquals(TvPagingFocusAction.FALLBACK, TvPagingFocusBudget().next(page(30).copy(hasError = true)))
    }

    @Test
    fun homeWaitsForFollowedThenChoosesTrendingThenSearch() {
        assertNull(tvHomeInitialFocus(null, "trending:1", followedReady = false, trendingReady = true))
        assertEquals("followed:8", tvHomeInitialFocus("followed:8", "trending:1", true, true))
        assertEquals("trending:1", tvHomeInitialFocus(null, "trending:1", true, true))
        assertNull(tvHomeInitialFocus(null, null, true, false))
        assertEquals("home-search", tvHomeInitialFocus(null, null, true, true))
    }

    private fun page(count: Int, loading: Boolean = false) = TvPagingFocusSnapshot(
        itemCount = count, targetPresent = false, appendLoading = loading, hasMore = true, hasError = false,
    )
}
