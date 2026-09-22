/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalTestApi::class)
class TvHomeProjectionTest {
    @Test
    fun focusMovementReusesPostersAndPagingUpdatesRefreshThem() = runAniComposeUiTest {
        val idle = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
        val pages = MutableStateFlow(PagingData.from(listOf(TvPoster(1, "First", null), TvPoster(2, "Second", null)), idle))
        val conversions = AtomicInteger()
        val mapper: (TvPoster) -> TvPoster = { conversions.incrementAndGet(); it }
        setContent {
            TvTheme { ProjectionPage(pages.collectAsLazyPagingItems(), mapper) }
        }
        waitUntil { onAllNodesWithTag("rail:1").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { assertEquals(2, conversions.get()) }
        onNodeWithTag("rail:1").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        onNodeWithTag("rail:2").assertIsFocused()
        runOnIdle {
            assertEquals("Remote focus must not rebuild the loaded catalogue", 2, conversions.get())
            pages.value = PagingData.from(listOf(TvPoster(1, "Updated", null), TvPoster(2, "Second", null), TvPoster(3, "Third", null)), idle)
        }
        waitUntil { onAllNodesWithTag("rail:3").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Updated").assertExists()
        runOnIdle { assertEquals(5, conversions.get()) }
    }

    @Test
    fun contentPreferenceChangesRefreshTheExistingSnapshot() = runAniComposeUiTest {
        val idle = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
        val pages = MutableStateFlow(PagingData.from(listOf(TvPoster(1, "Restricted", null)), idle))
        var mode by mutableStateOf(NsfwMode.DISPLAY)
        setContent {
            TvTheme {
                val mapper = remember(mode) { { item: TvPoster -> item.copy(nsfwMode = mode) } }
                ProjectionPage(pages.collectAsLazyPagingItems(), mapper)
            }
        }
        waitUntil { onAllNodesWithTag("rail:1").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { mode = NsfwMode.HIDE }
        onNodeWithText("Restricted").assertDoesNotExist()
        runOnIdle { mode = NsfwMode.DISPLAY }
        onNodeWithText("Restricted").assertExists()
    }
}

@Composable
private fun ProjectionPage(items: LazyPagingItems<TvPoster>, mapper: (TvPoster) -> TvPoster) {
    val focus = rememberTvFocusState("rail:1")
    val entries = items.tvRailPosters(mapper)
    TvPage(focus.requestedKey, {}, focusState = focus) {
        TvStaticFocusGroup(focus, "rail:", entries.map { "rail:${it.poster.id}" }, ready = entries.isNotEmpty())
        Row {
            entries.forEach { entry ->
                val key = "rail:${entry.poster.id}"
                TvButton(entry.poster.title, {}, Modifier.tvFocusTarget(key, focus).testTag(key))
            }
        }
    }
}
