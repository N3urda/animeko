/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvShellCompactHistoryTest {
    @Test
    fun compactHistoryKeepsTitleAndPlaybackTimeVisibleAndPlaysWithRemote() = runAniComposeUiTest {
        var plays = 0
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(536.dp, 180.dp)) {
                    TvHistoryCard(
                        EpisodeHistory(
                            episodeId = 701, subjectId = 51,
                            subjectName = "葬送的芙莉莲 第二季 · 勇者们旅途的后续故事",
                            episodeName = "第 12 集 · 真正的勇气",
                            positionMillis = 750000, durationMillis = 1440000,
                        ),
                        NsfwMode.DISPLAY, { plays++ },
                    )
                }
            }
        }
        onNodeWithTag("tv-history-701").assertIsDisplayed().performTvClick()
        onNodeWithTag("tv-history-title-701", useUnmergedTree = true).assertIsDisplayed().assertWidthIsAtLeast(300.dp)
        onNodeWithTag("tv-history-time-701", useUnmergedTree = true).assertIsDisplayed()
        val card = onNodeWithTag("tv-history-701").getUnclippedBoundsInRoot()
        val time = onNodeWithTag("tv-history-time-701", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(time.left >= card.left && time.right <= card.right && time.bottom <= card.bottom)
        runOnIdle { assertEquals(1, plays) }
    }
}
