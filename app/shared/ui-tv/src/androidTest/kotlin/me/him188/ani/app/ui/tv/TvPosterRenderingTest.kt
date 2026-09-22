/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalTestApi::class)
class TvPosterRenderingTest {
    @Test
    fun movingRemoteFocusDoesNotRemapLoadedPosters() = runAniComposeUiTest {
        val conversions = AtomicInteger()
        val idle = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
        val pages = flowOf(PagingData.from((1..200).toList(), idle))
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(960.dp, 540.dp)) {
                    val items = pages.collectAsLazyPagingItems()
                    val focus = rememberTvFocusState("poster:")
                    TvPage("番剧", {}, focusState = focus) {
                        TvPosterGrid(items, { it }, { id ->
                            conversions.incrementAndGet()
                            TvPoster(id, "番剧 $id", null)
                        }, {}, focusState = focus)
                    }
                }
            }
        }
        waitUntil { onAllNodesWithTag("tv-subject-1").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-1").assertIsFocused()
        val initialConversions = runOnIdle { conversions.get() }
        onNodeWithTag("tv-subject-1").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-subject-2").assertIsFocused().performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tv-subject-3").assertIsFocused().performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        onNodeWithTag("tv-subject-2").assertIsFocused()
        runOnIdle {
            assertEquals(200, initialConversions)
            assertEquals(initialConversions, conversions.get())
        }
    }

    @Test
    fun changedPagingDataAndContentPreferencesRefreshPosters() = runAniComposeUiTest {
        val idle = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
        val pages = MutableStateFlow(PagingData.from(listOf(1, 2), idle))
        var nsfwMode by mutableStateOf(NsfwMode.DISPLAY)
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(960.dp, 540.dp)) {
                    val items = pages.collectAsLazyPagingItems()
                    val mode = nsfwMode
                    val focus = rememberTvFocusState("poster:")
                    TvPage("番剧", {}, focusState = focus) {
                        TvPosterGrid(items, { it }, { id ->
                            TvPoster(id, "番剧 $id", null, nsfwMode = if (id == 2) mode else NsfwMode.DISPLAY)
                        }, {}, focusState = focus)
                    }
                }
            }
        }
        waitUntil { onAllNodesWithTag("tv-subject-2").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("番剧 2").assertExists()
        runOnIdle { nsfwMode = NsfwMode.HIDE }
        onNodeWithTag("tv-subject-2").assertDoesNotExist()
        runOnIdle { nsfwMode = NsfwMode.BLUR }
        onNodeWithTag("tv-subject-2").assertExists()
        onNodeWithText("番剧 2").assertDoesNotExist()
        runOnIdle { pages.value = PagingData.from(listOf(1, 2, 3), idle) }
        waitUntil { onAllNodesWithTag("tv-subject-3").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("番剧 3").assertExists()
    }
}
