/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import me.him188.ani.app.data.models.schedule.AnimeSeason
import me.him188.ani.app.data.models.schedule.AnimeSeasonId
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvSearchFiltersTest {
    @Test
    fun genreAndSettingCombineWithoutAKeywordAndOnlyApplyOnConfirmation() = runAniComposeUiTest {
        var applied: SubjectSearchQuery? = null
        setContent { TvTheme { TvSearchFilterDialog(SubjectSearchQuery(""), emptyList(), { applied = it }, {}) } }
        onNodeWithTag("tv-filter-value:科幻").performTvClick().assertIsSelected()
        onNodeWithText("设定").performTvClick()
        onNodeWithTag("tv-filter-values").performScrollToNode(hasText("异世界"))
        onNodeWithTag("tv-filter-value:异世界").performTvClick().assertIsSelected()
        runOnIdle { assertNull(applied) }
        onNodeWithTag("tv-filter-apply").performTvClick()
        runOnIdle {
            val query = requireNotNull(applied)
            assertEquals(setOf("科幻", "异世界"), query.tags!!.toSet())
            assertEquals("", query.keywords)
        }
    }

    @Test
    fun cancellingADraftDoesNotSubmitChanges() = runAniComposeUiTest {
        var submitted = 0
        var dismissed = 0
        setContent { TvTheme { TvSearchFilterDialog(SubjectSearchQuery("Naruto"), emptyList(), { submitted++ }, { dismissed++ }) } }
        onNodeWithTag("tv-filter-value:科幻").performTvClick()
        onNodeWithTag("tv-filter-cancel").performTvClick()
        runOnIdle { assertEquals(0, submitted); assertEquals(1, dismissed) }
    }

    @Test
    fun clearingFiltersKeepsTypedKeywordAndRemovesYearSeasonSortAndCustomTags() = runAniComposeUiTest {
        var applied: SubjectSearchQuery? = null
        val query = SubjectSearchQuery("Naruto", tags = listOf("自定义标签", "科幻"), year = 2025, season = AnimeSeason.SPRING, sort = SearchSort.RANK)
        setContent { TvTheme { TvSearchFilterDialog(query, listOf(AnimeSeasonId(2025, AnimeSeason.SPRING)), { applied = it }, {}) } }
        onNodeWithTag("tv-filter-reset").performTvClick()
        onNodeWithTag("tv-filter-apply").performTvClick()
        runOnIdle { assertEquals(SubjectSearchQuery("Naruto"), applied) }
    }

    @Test
    fun clearingYearAlsoClearsTheDependentSeason() = runAniComposeUiTest {
        var applied: SubjectSearchQuery? = null
        val query = SubjectSearchQuery("", tags = listOf("科幻"), year = 2025, season = AnimeSeason.SPRING)
        setContent { TvTheme { TvSearchFilterDialog(query, listOf(AnimeSeasonId(2025, AnimeSeason.SPRING)), { applied = it }, {}) } }
        onNodeWithTag("tv-filter-categories").performScrollToNode(hasText("年份"))
        onNodeWithTag("tv-filter-category:年份").performTvClick()
        onNodeWithTag("tv-filter-value:全部年份").performTvClick()
        onNodeWithTag("tv-filter-apply").performTvClick()
        runOnIdle { assertEquals(SubjectSearchQuery("", tags = listOf("科幻")), applied) }
    }

    @Test
    fun remoteRightEntersSelectedConditionAndLeftReturnsToItsCategory() = runAniComposeUiTest {
        val query = SubjectSearchQuery("", tags = listOf("科幻"))
        setContent { TvTheme { TvSearchFilterDialog(query, emptyList(), {}, {}) } }
        onNodeWithTag("tv-filter-category:类型").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        onNodeWithTag("tv-filter-value:科幻").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft)
            keyUp(Key.DirectionLeft)
        }
        onNodeWithTag("tv-filter-category:类型").assertIsFocused()
    }
}
