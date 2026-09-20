/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvRemoteNavigationTest {
    @Test
    fun episodeSelectionUsesEpisodeIdAfterMovingRight() = runAniComposeUiTest {
        var selected: Int? = null
        val firstEpisode = EpisodeListItem(901, EpisodeSort(1), null, "Episode 1", "第一话", UnifiedCollectionType.DOING, true)
        val secondEpisode = firstEpisode.copy(episodeId = 947, sort = EpisodeSort(2), nameCn = "第二话")
        setContent {
            TvTheme {
                val firstFocus = remember { FocusRequester() }
                Row {
                    TvEpisodeCard(firstEpisode, { selected = firstEpisode.episodeId }, Modifier.weight(1f).focusRequester(firstFocus))
                    TvEpisodeCard(secondEpisode, { selected = secondEpisode.episodeId }, Modifier.weight(1f))
                }
                LaunchedEffect(Unit) { firstFocus.requestFocus() }
            }
        }
        onNodeWithTag("tv-episode-901").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        onNodeWithTag("tv-episode-947").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        runOnIdle { assertEquals(947, selected) }
    }

    @Test
    fun maskedPosterNeedsExplicitRevealBeforeNavigation() = runAniComposeUiTest {
        var opened = 0
        setContent {
            TvTheme {
                TvPosterCard(TvPoster(17, "受限番剧", null, nsfwMode = NsfwMode.BLUR), { opened++ })
            }
        }
        onNodeWithText("受限番剧").assertDoesNotExist()
        onNodeWithTag("tv-subject-17").performTvClick()
        onNodeWithText("受限番剧").assertExists()
        runOnIdle { assertEquals(0, opened) }
        onNodeWithTag("tv-subject-17").performTvClick()
        runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun hiddenPosterCannotBeFocusedOrOpened() = runAniComposeUiTest {
        setContent {
            TvTheme {
                TvPosterCard(TvPoster(18, "隐藏番剧", null, nsfwMode = NsfwMode.HIDE), {})
            }
        }
        onNodeWithTag("tv-subject-18").assertDoesNotExist()
        onNodeWithText("隐藏番剧").assertDoesNotExist()
    }

    @Test
    fun remoteMovesFocusAndActivatesExactlyOnce() = runAniComposeUiTest {
        var clicks = 0
        setContent {
            TvTheme {
                val first = remember { FocusRequester() }
                Row {
                    TvButton("搜索", {}, Modifier.focusRequester(first).testTag("search"))
                    TvButton("收藏", { clicks++ }, Modifier.testTag("collection"))
                }
                LaunchedEffect(Unit) { first.requestFocus() }
            }
        }
        onNodeWithTag("search").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        onNodeWithTag("collection").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun disabledActionIsSkippedByDirectionalNavigation() = runAniComposeUiTest {
        setContent {
            TvTheme {
                val first = remember { FocusRequester() }
                Row {
                    TvButton("返回", {}, Modifier.focusRequester(first).testTag("back"))
                    TvButton("加载中", {}, Modifier.testTag("disabled"), enabled = false)
                    TvButton("重试", {}, Modifier.testTag("retry"))
                }
                LaunchedEffect(Unit) { first.requestFocus() }
            }
        }
        onNodeWithTag("back").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        onNodeWithTag("retry").assertIsFocused()
    }
}
