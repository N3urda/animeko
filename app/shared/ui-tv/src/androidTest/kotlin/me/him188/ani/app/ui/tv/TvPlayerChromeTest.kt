/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvPlayerChromeTest {
    @Test
    fun hiddenConfirmTogglesExactlyOnceAndFocusesPlay() = runAniComposeUiTest {
        var toggles = 0
        setContent {
            TvTheme {
                var input by remember { mutableStateOf(TvPlayerInputState()) }
                TvPlayerChrome(
                    state = playerState(), input = input, onInputChange = { input = it },
                    onTogglePlayback = { toggles++ }, onSeek = {}, onNextEpisode = {}, onRetry = {}, onBack = {},
                ) {}
            }
        }
        onNodeWithTag("tv-player").performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        onNodeWithTag("tv-play-pause").assertIsFocused()
        runOnIdle { assertEquals(1, toggles) }
    }

    @Test
    fun previewShowsTargetWithoutChangingPlaybackUntilConfirm() = runAniComposeUiTest {
        val seeks = mutableListOf<Long>()
        setContent {
            TvTheme {
                var input by remember { mutableStateOf(TvPlayerInputState()) }
                TvPlayerChrome(
                    state = playerState(), input = input, onInputChange = { input = it },
                    onTogglePlayback = {}, onSeek = { seeks += it }, onNextEpisode = {}, onRetry = {}, onBack = {},
                ) {}
            }
        }
        onNodeWithTag("tv-player").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        onNodeWithText("跳转至 0:00:15").assertExists()
        runOnIdle { assertEquals(emptyList<Long>(), seeks) }
        onNodeWithTag("tv-player").performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        runOnIdle { assertEquals(listOf(15000L), seeks) }
    }

    @Test
    fun progressControlPreviewSurvivesDirectionDownAndUpInOneFrame() = runAniComposeUiTest {
        val seeks = mutableListOf<Long>()
        setContent {
            TvTheme {
                var input by remember { mutableStateOf(TvPlayerInputState(TvPlayerOverlay.Controls)) }
                TvPlayerChrome(
                    state = playerState().copy(positionMillis = 15000), input = input,
                    onInputChange = { input = it }, onTogglePlayback = {}, onSeek = { seeks += it },
                    onNextEpisode = {}, onRetry = {}, onBack = {},
                ) {}
            }
        }
        onNodeWithTag("tv-player-progress").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        onNodeWithTag("tv-player-progress").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft)
            keyUp(Key.DirectionLeft)
        }
        onNodeWithText("跳转至 0:00:05").assertExists()
        runOnIdle { assertEquals(emptyList<Long>(), seeks) }
        onNodeWithTag("tv-player").performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        runOnIdle { assertEquals(listOf(5000L), seeks) }
        onNodeWithTag("tv-player-progress").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        onNodeWithText("跳转至 0:00:25").assertExists()
        runOnIdle { assertEquals(listOf(5000L), seeks) }
    }

    @Test
    fun unknownDurationDisablesProgressControl() = runAniComposeUiTest {
        setContent {
            TvTheme {
                var input by remember { mutableStateOf(TvPlayerInputState(TvPlayerOverlay.Controls)) }
                TvPlayerChrome(
                    state = playerState().copy(durationMillis = 0), input = input, onInputChange = { input = it },
                    onTogglePlayback = {}, onSeek = {}, onNextEpisode = {}, onRetry = {}, onBack = {},
                ) {}
            }
        }
        onNodeWithTag("tv-player-progress").assertIsNotEnabled()
        onNodeWithText("时长未知 · 暂不可跳转").assertExists()
    }

    @Test
    fun prolongedBufferingOffersRecoveryWithoutCallingRetryAutomatically() = runAniComposeUiTest {
        var retries = 0
        mainClock.autoAdvance = false
        setContent {
            TvTheme {
                var input by remember { mutableStateOf(TvPlayerInputState()) }
                TvPlayerChrome(
                    state = playerState().copy(
                        isPlaying = false,
                        status = TvPlayerStatus(TvPlayerPhase.Buffering, "正在缓冲视频…"),
                    ),
                    input = input, onInputChange = { input = it },
                    onTogglePlayback = {}, onSeek = {}, onNextEpisode = {}, onRetry = { retries++ }, onBack = {},
                ) {}
            }
        }
        mainClock.advanceTimeBy(16000)
        mainClock.autoAdvance = true
        onNodeWithTag("tv-player-retry").assertExists()
        onNodeWithTag("tv-player-sources").assertIsFocused()
        runOnIdle { assertEquals(0, retries) }
        onNodeWithTag("tv-player-retry").performTvClick()
        runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun selectedOffscreenOptionReceivesFocusAndScrolls() = runAniComposeUiTest {
        setContent {
            TvTheme {
                TvPlayerOptionPanel(
                    title = "选集", onBack = {}, onSelect = {},
                    options = (1..50).map { TvPlayerOption("episode-$it", "第 $it 集", selected = it == 42) },
                )
            }
        }
        waitUntil { onAllNodesWithTag("tv-player-option-episode-42").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-player-option-episode-42").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        onNodeWithTag("tv-player-option-episode-43").assertIsFocused()
    }

    @Test
    fun selectedTrackArrivingAfterPanelOpensGetsInitialFocus() = runAniComposeUiTest {
        var options by mutableStateOf(listOf(TvPlayerOption("off", "关闭字幕")))
        setContent {
            TvTheme {
                TvPlayerOptionPanel(
                    title = "字幕", options = options, initialOptionId = "track-42", onBack = {}, onSelect = {},
                )
            }
        }
        runOnIdle {
            options = options + (1..50).map { TvPlayerOption("track-$it", "字幕 $it", selected = it == 42) }
        }
        waitUntil { onAllNodesWithTag("tv-player-option-track-42").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-player-option-track-42").assertIsFocused()
    }

    @Test
    fun arrivingTracksDoNotStealFocusAfterRemoteNavigation() = runAniComposeUiTest {
        var options by mutableStateOf(listOf(TvPlayerOption("off", "关闭字幕")))
        setContent {
            TvTheme {
                TvPlayerOptionPanel(
                    title = "字幕", options = options, initialOptionId = "track-42", onBack = {}, onSelect = {},
                )
            }
        }
        onNodeWithTag("tv-player-menu-close").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        onNodeWithTag("tv-player-option-off").assertIsFocused()
        runOnIdle {
            options = options + (1..50).map { TvPlayerOption("track-$it", "字幕 $it", selected = it == 42) }
        }
        onNodeWithTag("tv-player-option-off").assertIsFocused()
    }

    @Test
    fun closingSourcePanelReturnsFocusToSourceEntry() = runAniComposeUiTest {
        setContent {
            TvTheme {
                var input by remember { mutableStateOf(TvPlayerInputState(TvPlayerOverlay.Controls)) }
                TvPlayerChrome(
                    state = playerState(), input = input, onInputChange = { input = it },
                    onTogglePlayback = {}, onSeek = {}, onNextEpisode = {}, onRetry = {}, onBack = {},
                ) {}
                if (input.overlay == TvPlayerOverlay.Sources) {
                    TvPlayerOptionPanel(
                        title = "资源", options = listOf(TvPlayerOption("current", "当前线路", selected = true)),
                        onBack = { input = tvPlayerBack(input).state }, onSelect = {},
                    )
                }
            }
        }
        onNodeWithTag("tv-player-sources").performTvClick()
        onNodeWithTag("tv-player-option-current").assertIsFocused()
        onNodeWithTag("tv-player-menu-close").performTvClick()
        onNodeWithTag("tv-player-sources").assertIsFocused()
    }

    private fun playerState() = TvPlayerUiState(
        title = "测试番剧", episodeTitle = "第 1 集", positionMillis = 5000, durationMillis = 60000,
        playWhenReady = true, isPlaying = true, status = TvPlayerStatus(TvPlayerPhase.Playing, "正在播放"),
    )
}
