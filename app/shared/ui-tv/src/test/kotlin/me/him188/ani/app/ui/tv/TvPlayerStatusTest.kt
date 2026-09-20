/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.subject.episode.EpisodePageLoadError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openani.mediamp.MediaStatus

class TvPlayerStatusTest {
    @Test
    fun completedPlaybackIsNotReportedAsPaused() {
        val status = status(mediaStatus = MediaStatus.Ended, playWhenReady = false)
        assertEquals(TvPlayerPhase.Ended, status.phase)
        assertFalse(status.phase.waiting)
    }

    @Test
    fun bufferingKeepsItsOwnStatusEvenWhenPlaybackIsRequested() {
        val status = status(isBuffering = true, playWhenReady = true)
        assertEquals(TvPlayerPhase.Buffering, status.phase)
        assertTrue(status.phase.waiting)
    }

    @Test
    fun queriesAndAddressResolutionHaveDistinctStates() {
        assertEquals(TvPlayerPhase.Searching, status(sourceLoading = true, selected = false).phase)
        assertEquals(TvPlayerPhase.Resolving, status(loading = VideoLoadingState.ResolvingSource).phase)
        assertEquals(TvPlayerPhase.Unavailable, status(selected = false).phase)
    }

    @Test
    fun networkFailureOffersRecoveryAndKeepsSpecificReason() {
        val status = status(loading = VideoLoadingState.NetworkError)
        assertEquals(TvPlayerPhase.Failed, status.phase)
        assertEquals("网络连接失败", status.message)
        assertTrue(status.recoverable)
    }

    @Test
    fun optionalSeriesInformationFailureDoesNotReportPlaybackFailure() {
        val status = status(isPlaying = true, pageError = EpisodePageLoadError.SeriesError(LoadError.NetworkError))
        assertEquals(TvPlayerPhase.Playing, status.phase)
        assertFalse(status.recoverable)
    }

    @Test
    fun requiredEpisodeInformationFailureOffersRecovery() {
        val status = status(pageError = EpisodePageLoadError.SubjectError(LoadError.NetworkError))
        assertEquals(TvPlayerPhase.Failed, status.phase)
        assertTrue(status.recoverable)
    }

    @Test
    fun realResourceQueryIsNotReportedAsEmptyAfterPlaceholderEnds() {
        assertEquals(
            "正在查询播放资源…",
            tvPlayerSourceMenuMessage(isPlaceholder = false, isQuerying = true, hasAvailableMedia = false),
        )
        assertEquals(
            "暂无可播放资源，可重试查询或返回设置检查数据源。",
            tvPlayerSourceMenuMessage(isPlaceholder = false, isQuerying = false, hasAvailableMedia = false),
        )
    }

    @Test
    fun partialResourceResultsCanBeSelectedWhileOtherQueriesContinue() {
        assertEquals(
            "仍在查询更多资源，可先选择已找到的线路。",
            tvPlayerSourceMenuMessage(isPlaceholder = false, isQuerying = true, hasAvailableMedia = true),
        )
    }

    private fun status(
        loading: VideoLoadingState = VideoLoadingState.Succeed(isBt = false),
        mediaStatus: MediaStatus = MediaStatus.Ready,
        isBuffering: Boolean = false,
        playWhenReady: Boolean = true,
        sourceLoading: Boolean = false,
        selected: Boolean = true,
        isPlaying: Boolean = false,
        pageError: EpisodePageLoadError? = null,
    ) = tvPlayerStatus(
        loading, mediaStatus, isBuffering, isPlaying, playWhenReady,
        sourceLoading, selected, pageLoading = false, pageError = pageError,
    )
}
