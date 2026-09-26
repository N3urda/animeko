/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.SubjectInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TvHomeBackdropTest {
    private val poster = TvPoster(17, "番剧", "https://example.test/cover.jpg")

    @Test
    fun unrestrictedPreferenceAllowsTheCurrentPosterBeforeDetailsLoad() {
        assertEquals(poster.imageUrl, tvHomeBackdropImageUrl(poster, TvHomeSubjectDetails(17, loading = true), NsfwMode.DISPLAY))
    }

    @Test
    fun protectedPreferencesWaitForTheCurrentSubjectsClassification() {
        listOf(NsfwMode.BLUR, NsfwMode.HIDE).forEach { mode ->
            assertNull(tvHomeBackdropImageUrl(poster, TvHomeSubjectDetails(17, loading = true), mode))
            assertNull(tvHomeBackdropImageUrl(poster, TvHomeSubjectDetails(17, failed = true), mode))
            val safe = TvHomeSubjectDetails(17, SubjectInfo.Empty.copy(subjectId = 17, nsfw = false))
            assertEquals(poster.imageUrl, tvHomeBackdropImageUrl(poster, safe, mode))
        }
    }

    @Test
    fun maskedAndHiddenCardsNeverSupplyBackgroundArt() {
        listOf(NsfwMode.BLUR, NsfwMode.HIDE).forEach { mode ->
            assertNull(tvHomeBackdropImageUrl(poster.copy(nsfwMode = mode), TvHomeSubjectDetails(), NsfwMode.DISPLAY))
        }
    }

    @Test
    fun restrictedMetadataImmediatelySuppressesAnOtherwiseVisiblePoster() {
        val details = TvHomeSubjectDetails(17, SubjectInfo.Empty.copy(subjectId = 17, nsfw = true))
        listOf(NsfwMode.BLUR, NsfwMode.HIDE).forEach { mode ->
            assertNull(tvHomeBackdropImageUrl(poster, details, mode))
        }
        assertEquals(poster.imageUrl, tvHomeBackdropImageUrl(poster, details, NsfwMode.DISPLAY))
    }

    @Test
    fun staleDetailsCannotChooseOrMaskTheNextPostersBackground() {
        val previous = TvHomeSubjectDetails(18, SubjectInfo.Empty.copy(subjectId = 18, nsfw = true))
        assertNull(tvHomeBackdropImageUrl(poster, previous, NsfwMode.HIDE))
        assertEquals(poster.imageUrl, tvHomeBackdropImageUrl(poster, previous, NsfwMode.DISPLAY))
        val previousSafe = previous.copy(info = SubjectInfo.Empty.copy(subjectId = 18, nsfw = false))
        assertNull(tvHomeBackdropImageUrl(poster, previousSafe, NsfwMode.HIDE))
    }

    @Test
    fun emptyContentAndMissingArtworkUseTheGradientFallback() {
        assertNull(tvHomeBackdropImageUrl(null, TvHomeSubjectDetails(), NsfwMode.DISPLAY))
        listOf(null, "", "   ").forEach { url ->
            assertNull(tvHomeBackdropImageUrl(poster.copy(imageUrl = url), TvHomeSubjectDetails(), NsfwMode.DISPLAY))
        }
    }
}
