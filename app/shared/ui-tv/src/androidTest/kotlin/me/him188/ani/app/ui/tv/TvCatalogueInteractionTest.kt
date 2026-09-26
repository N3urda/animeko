/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.app.ui.subject.episode.list.EpisodeListUiState
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvCatalogueInteractionTest {
    @Test
    fun remoteSkipsUnairedEpisodeAndOpensNextAiredEpisode() = runAniComposeUiTest {
        var selected: Int? = null
        val first = episode(901, 1)
        val unaired = episode(947, 2).copy(isBroadcast = false)
        val aired = episode(978, 3)
        setContent {
            TvTheme {
                val firstFocus = remember { FocusRequester() }
                Row {
                    TvEpisodeCard(first, { selected = first.episodeId }, Modifier.weight(1f).focusRequester(firstFocus))
                    TvEpisodeCard(unaired, { selected = unaired.episodeId }, Modifier.weight(1f))
                    TvEpisodeCard(aired, { selected = aired.episodeId }, Modifier.weight(1f))
                }
                LaunchedEffect(Unit) { firstFocus.requestFocus() }
            }
        }
        onNodeWithTag("tv-episode-947").assertIsNotEnabled()
        onNodeWithTag("tv-episode-901").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        onNodeWithTag("tv-episode-978").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        runOnIdle { assertEquals(978, selected) }
    }

    @Test
    fun historyShowsActualPositionAndDuration() = runAniComposeUiTest {
        setContent {
            TvTheme {
                TvHistoryCard(
                    EpisodeHistory(701, 90_000, subjectId = 51, durationMillis = 1_440_000),
                    NsfwMode.DISPLAY,
                    {},
                )
            }
        }
        onNodeWithText("1:30 / 24:00").assertExists()
    }

    @Test
    fun changingEpisodeGroupAndReturningRestoresSelectedBusinessId() = runAniComposeUiTest {
        var opened by mutableStateOf(false)
        var selected: Int? = null
        val episodes = EpisodeListUiState("长篇番剧", (1..49).map { episode(1000 + it, it) }, emptyList())
        setContent {
            TvTheme {
                val holder = rememberSaveableStateHolder()
                if (opened) {
                    TvButton("返回详情", { opened = false })
                } else holder.SaveableStateProvider("subject") {
                    val focus = rememberTvFocusState("subject:")
                    TvPage("长篇番剧", {}, focusState = focus) {
                        TvCatalogueEpisodeGrid(episodes, 1001, emptyList(), { selected = it; opened = true }) {}
                    }
                }
            }
        }
        onNodeWithTag("tv-episode-next-group").performTvClick()
        onNodeWithTag("tv-episode-1025").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        onNodeWithTag("tv-episode-1026").assertIsFocused().performTvClick()
        runOnIdle { assertEquals(1026, selected) }
        onNodeWithText("返回详情").performTvClick()
        onNodeWithTag("tv-episode-1026").assertIsFocused()
        onNodeWithTag("tv-episode-grid").performScrollToNode(hasTestTag("tv-episode-locate"))
        onNodeWithTag("tv-episode-locate").performTvClick()
        onNodeWithTag("tv-episode-1001").assertIsFocused()
    }

    @Test
    fun refreshedEpisodesUpdateTheGroupAndPlaybackTargetOnTheOpenPage() = runAniComposeUiTest {
        var episodes by mutableStateOf(EpisodeListUiState("连载番剧", listOf(episode(1001, 1)), emptyList()))
        var playEpisodeId by mutableStateOf(1001)
        setContent {
            TvTheme {
                TvPage("连载番剧", {}, focusState = rememberTvFocusState("subject:")) {
                    TvCatalogueEpisodeGrid(episodes, playEpisodeId, emptyList(), {}) {}
                }
            }
        }
        onNodeWithTag("tv-episode-1001").assertExists()
        onNodeWithText("定位 01").assertExists()
        runOnIdle {
            episodes = EpisodeListUiState("连载番剧", (1..49).map { episode(1000 + it, it) }, emptyList())
        }
        onNodeWithTag("tv-episode-next-group").assertExists()
        runOnIdle { playEpisodeId = 1025 }
        onNodeWithTag("tv-episode-1025").assertExists()
        onNodeWithText("定位 25").assertExists()
        runOnIdle {
            episodes = episodes.copy(mainEpisodes = episodes.mainEpisodes.map {
                if (it.episodeId == 1025) it.copy(isBroadcast = false) else it
            })
        }
        onNodeWithTag("tv-episode-1025").assertIsNotEnabled()
        onNodeWithText("定位 01").assertExists()
    }

    @Test
    fun homeNavigationMovesDownEverySidebarEntry() = runAniComposeUiTest {
        var collections = 0
        setContent {
            TvTheme {
                val focus = rememberTvFocusState("home-search")
                TvAppShell("home-search", { if (it == "home-collections") collections++ }) {
                    TvPage("首页", null, focusState = focus) { }
                }
            }
        }
        onNodeWithTag("home-search").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        onNodeWithTag("home-collections").assertIsFocused().performTvClick()
        runOnIdle { assertEquals(1, collections) }
        listOf("home-collections", "home-history", "home-settings").forEach { key ->
            onNodeWithTag(key).performKeyInput {
                keyDown(Key.DirectionDown)
                keyUp(Key.DirectionDown)
            }
        }
        onNodeWithTag("home-login").assertIsFocused()
    }

    private fun episode(id: Int, number: Int) = EpisodeListItem(
        id, EpisodeSort(number), null, "Episode $number", "第 $number 话", UnifiedCollectionType.DOING, true,
    )
}
