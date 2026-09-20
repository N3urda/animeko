/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvHistoryProtectionTest {
    private val history = EpisodeHistory(episodeId = 701, positionMillis = 8000, subjectId = 51, subjectName = "受限番剧", episodeName = "受限剧集")

    @Test
    fun unknownClassificationDoesNotExposeStoredNamesOrPlayback() = runAniComposeUiTest {
        setContent { TvTheme { TvHistoryRecord(history, null, NsfwMode.BLUR, {}, {}) } }
        onNodeWithText("受限番剧").assertDoesNotExist()
        onNodeWithText("受限剧集").assertDoesNotExist()
        onNodeWithTag("tv-history-701").assertDoesNotExist()
    }

    @Test
    fun failedClassificationOnlyOffersRetry() = runAniComposeUiTest {
        var retries = 0
        var plays = 0
        setContent { TvTheme { TvHistoryRecord(history, TvHistorySubjectState.Failed, NsfwMode.BLUR, { retries++ }, { plays++ }) } }
        onNodeWithText("受限番剧").assertDoesNotExist()
        onNodeWithText("重试").performTvClick()
        runOnIdle { assertEquals(1, retries); assertEquals(0, plays) }
    }

    @Test
    fun blurredHistoryRequiresRevealBeforePlayback() = runAniComposeUiTest {
        var plays = 0
        setContent { TvTheme { TvHistoryCard(history, NsfwMode.BLUR, { plays++ }) } }
        onNodeWithText("受限番剧").assertDoesNotExist()
        onNodeWithText("受限剧集").assertDoesNotExist()
        onNodeWithTag("tv-history-701").performTvClick()
        runOnIdle { assertEquals(0, plays) }
        onNodeWithText("受限番剧").assertExists()
        onNodeWithTag("tv-history-701").performTvClick()
        runOnIdle { assertEquals(1, plays) }
    }

    @Test
    fun hiddenHistoryHasNoPlaybackTargetOrTitle() = runAniComposeUiTest {
        setContent { TvTheme { TvHistoryCard(history, NsfwMode.HIDE, {}) } }
        onNodeWithText("受限番剧").assertDoesNotExist()
        onNodeWithTag("tv-history-701").assertDoesNotExist()
    }
}
