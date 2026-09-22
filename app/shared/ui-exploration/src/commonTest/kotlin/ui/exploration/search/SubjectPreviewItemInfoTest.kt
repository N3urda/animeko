/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SubjectPreviewItemInfoTest {
    @Test
    fun `poster projection preserves visible data without reading preview details`() = runTest {
        val rating = RatingInfo.Empty.copy(score = "8.5", total = 123, rank = 10)
        val subject = SubjectInfo.Empty.copy(
            subjectId = 101,
            name = "Original title",
            nameCn = "中文标题",
            imageLarge = "https://example.com/poster.jpg",
            ratingInfo = rating,
            tags = unusedDetails(),
        )
        val result = SubjectPreviewItemInfo.compute(
            subject,
            mainEpisodeCount = 24,
            nsfwModeSettings = NsfwMode.HIDE,
            relatedPersonList = unusedDetails(),
            characters = unusedDetails(),
            includePreviewDetails = false,
        )

        assertEquals(101, result.subjectId)
        assertEquals("中文标题", result.title)
        assertEquals(subject.imageLarge, result.imageUrl)
        assertSame(rating, result.rating)
        assertEquals("", result.tags)
        assertNull(result.staff)
        assertNull(result.actors)
        assertFalse(result.hide)
    }

    @Test
    fun `poster projection falls back to original title and displays safe subjects`() = runTest {
        for (mode in NsfwMode.entries) {
            val result = SubjectPreviewItemInfo.compute(
                SubjectInfo.Empty.copy(name = "Original title", nameCn = ""),
                mainEpisodeCount = 0,
                nsfwModeSettings = mode,
                relatedPersonList = null,
                characters = null,
                includePreviewDetails = false,
            )
            assertEquals("Original title", result.title)
            assertFalse(result.nsfw)
            assertEquals(NsfwMode.DISPLAY, result.nsfwMode)
        }
    }

    @Test
    fun `poster projection preserves adult content preferences and explicit hiding`() = runTest {
        for (mode in NsfwMode.entries) {
            for (hide in listOf(false, true)) {
                val result = SubjectPreviewItemInfo.compute(
                    SubjectInfo.Empty.copy(nsfw = true),
                    mainEpisodeCount = 0,
                    nsfwModeSettings = mode,
                    relatedPersonList = null,
                    characters = null,
                    hide = hide,
                    includePreviewDetails = false,
                )
                assertTrue(result.nsfw)
                assertEquals(mode, result.nsfwMode)
                assertEquals(hide, result.hide)
            }
        }
    }

    private fun <T> unusedDetails(): List<T> = object : AbstractList<T>() {
        override val size: Int get() = error("Poster projection must not inspect preview details")
        override fun get(index: Int): T = error("Poster projection must not inspect preview details")
    }
}
