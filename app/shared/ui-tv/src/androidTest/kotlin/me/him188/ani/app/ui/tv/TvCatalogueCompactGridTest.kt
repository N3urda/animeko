/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvCatalogueCompactGridTest {
    @Test
    fun compactSearchGridShowsTitlesAndSupportsHorizontalAndVerticalRemoteNavigation() = runAniComposeUiTest {
        var selected: Int? = null
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(720.dp, 405.dp)) {
                    val pager = remember {
                        Pager(PagingConfig(pageSize = 4, enablePlaceholders = false)) {
                            object : PagingSource<Int, TvPoster>() {
                                override fun getRefreshKey(state: PagingState<Int, TvPoster>): Int? = null
                                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TvPoster> =
                                    LoadResult.Page((1..4).map { TvPoster(it, "完整番剧标题\n第二行标题", null, "评分 8.5") }, null, null)
                            }
                        }
                    }
                    val items = pager.flow.collectAsLazyPagingItems()
                    val focus = rememberTvFocusState("poster:")
                    TvPage("搜索番剧", {}, focusState = focus) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            TvOutlinedTextField(rememberTextFieldState("关键词"), Modifier.weight(1f))
                            TvButton("搜索", {})
                        }
                        TvPosterGrid(items, { it.id }, { it }, { selected = it }, focusState = focus)
                    }
                }
            }
        }
        waitUntil { onAllNodesWithTag("tv-subject-1").fetchSemanticsNodes().isNotEmpty() }
        val viewport = onNodeWithTag("tv-poster-grid-viewport").getUnclippedBoundsInRoot()
        val first = onNodeWithTag("tv-subject-1").assertIsFocused().assertHeightIsEqualTo(144.dp)
        val firstBounds = first.getUnclippedBoundsInRoot()
        val title = onNodeWithTag("tv-subject-title-1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(firstBounds.top >= viewport.top && firstBounds.bottom <= viewport.bottom)
        assertTrue((title.bottom - title.top) >= 46.dp && title.bottom <= viewport.bottom)
        first.performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-subject-2").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        val fourth = onNodeWithTag("tv-subject-4").assertIsFocused().assertHeightIsEqualTo(144.dp)
        val fourthBounds = fourth.getUnclippedBoundsInRoot()
        assertTrue("向下导航后当前整卡必须完整可见", fourthBounds.top >= viewport.top && fourthBounds.bottom <= viewport.bottom)
        fourth.performTvClick()
        runOnIdle { assertEquals(4, selected) }
    }

    @Test
    fun compactPosterKeepsRestrictedTitleMaskedUntilExplicitReveal() = runAniComposeUiTest {
        var opened = 0
        setContent {
            TvTheme {
                TvPosterCard(TvPoster(81, "受限标题", null, nsfwMode = NsfwMode.BLUR), { opened++ }, compact = true)
            }
        }
        onNodeWithText("受限标题").assertDoesNotExist()
        onNodeWithTag("tv-subject-81").performTvClick()
        onNodeWithText("受限标题").assertExists()
        runOnIdle { assertEquals(0, opened) }
        onNodeWithTag("tv-subject-81").performTvClick()
        runOnIdle { assertEquals(1, opened) }
    }
}
