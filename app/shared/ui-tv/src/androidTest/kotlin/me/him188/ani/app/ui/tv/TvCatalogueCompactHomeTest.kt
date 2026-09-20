/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvCatalogueCompactHomeTest {
    @Test
    fun compactHomeKeepsFocusedCardAndBothTitleLinesInsideContentViewport() = runAniComposeUiTest {
        setContent { TvTheme { HomeViewportHarness(DpSize(720.dp, 405.dp)) } }
        val card = onNodeWithTag("tv-subject-71")
        card.assertIsFocused().assertWidthIsEqualTo(240.dp).assertHeightIsEqualTo(144.dp)
        onNodeWithTag("tv-subject-cover-71", useUnmergedTree = true)
            .assertWidthIsEqualTo(96.dp).assertHeightIsEqualTo(144.dp)
        val viewport = onNodeWithTag("tv-home-content-viewport").getUnclippedBoundsInRoot()
        val bounds = card.getUnclippedBoundsInRoot()
        val title = onNodeWithTag("tv-subject-title-71", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("焦点卡片底部必须完整可见", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
        assertTrue("标题两行必须位于可见内容区域内", title.top >= viewport.top && title.bottom <= viewport.bottom)
        assertTrue("标题应完整保留两行高度", (title.bottom - title.top) >= 46.dp)
    }

    @Test
    fun regularHomeRetainsPortraitCoverAndVisibleTitle() = runAniComposeUiTest {
        setContent { TvTheme { HomeViewportHarness(DpSize(960.dp, 540.dp)) } }
        val card = onNodeWithTag("tv-subject-71").assertIsFocused().assertWidthIsEqualTo(148.dp)
        onNodeWithTag("tv-subject-cover-71", useUnmergedTree = true)
            .assertWidthIsEqualTo(148.dp).assertHeightIsEqualTo(222.dp)
        val viewport = onNodeWithTag("tv-home-content-viewport").getUnclippedBoundsInRoot()
        assertTrue(card.getUnclippedBoundsInRoot().bottom <= viewport.bottom)
        assertTrue(onNodeWithTag("tv-subject-title-71", useUnmergedTree = true).getUnclippedBoundsInRoot().bottom <= viewport.bottom)
    }
}

@Composable
private fun HomeViewportHarness(size: DpSize) {
    Box(Modifier.requiredSize(size)) {
        val focus = rememberTvFocusState("trending:71")
        TvCatalogueHomeLayout({}, {}, {}, {}, {}, focus) { compact ->
            LazyColumn {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("热门番剧", fontSize = 22.sp)
                        LazyRow(contentPadding = PaddingValues(8.dp)) {
                            item {
                                TvPosterCard(
                                    TvPoster(71, "完整番剧标题\n第二行标题", null), {},
                                    Modifier.tvFocusTarget("trending:71", focus), compact = compact,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
