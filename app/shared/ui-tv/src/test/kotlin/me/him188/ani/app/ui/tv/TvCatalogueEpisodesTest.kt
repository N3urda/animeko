/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.app.ui.subject.episode.list.EpisodeListUiState
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.serialization.BigNum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TvCatalogueEpisodesTest {
    @Test
    fun groupsKeepEveryBusinessIdAndSeparateSpecialsFromMainEpisodes() {
        val main = (1..49).map { episode(1000 + it, it) }
        val special = episode(8888, 1).copy(sort = EpisodeSort(BigNum(1), EpisodeType.SP))
        val groups = tvCatalogueEpisodeGroups(EpisodeListUiState("test", main, listOf(special)))
        assertEquals(listOf(24, 24, 1, 1), groups.map { it.episodes.size })
        assertEquals(main.map { it.episodeId } + special.episodeId, groups.flatMap { it.episodes }.map { it.episodeId })
        assertEquals("特别篇 · SP01", groups.last().title)
    }

    @Test
    fun playbackNeverChoosesUnairedPreferredEpisode() {
        val aired = episode(501, 1)
        val future = episode(777, 2).copy(isBroadcast = false)
        val state = EpisodeListUiState("test", listOf(aired, future), emptyList())
        assertEquals(501, tvCataloguePlaybackEpisode(state, 777)?.episodeId)
        assertNull(tvCataloguePlaybackEpisode(state.copy(mainEpisodes = listOf(future)), 777))
    }

    @Test
    fun continuationCanLocateSpecialEpisodeByItsId() {
        val special = episode(902, 1).copy(sort = EpisodeSort(BigNum(1), EpisodeType.SP), playProgress = .25f)
        val state = EpisodeListUiState("test", listOf(episode(901, 1)), listOf(special))
        assertEquals(902, tvCataloguePlaybackEpisode(state, 902)?.episodeId)
        assertEquals("继续观看 · SP01", tvCataloguePlaybackLabel(special))
    }

    @Test
    fun mainEpisodeLabelUsesSeasonNumberInsteadOfSeriesNumber() {
        val secondSeason = episode(1501, 25).copy(ep = EpisodeSort(1))
        assertEquals("播放 · 第 01 集", tvCataloguePlaybackLabel(secondSeason))
    }

    private fun episode(id: Int, number: Int) = EpisodeListItem(
        id, EpisodeSort(number), null, "Episode $number", "第 $number 话", UnifiedCollectionType.DOING, true,
    )
}
