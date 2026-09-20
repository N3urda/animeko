/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalTestApi::class)
class TvPosterPagingTest {
    @Test
    fun hiddenPageOffersRemoteLoadMoreWithoutAutomaticallyScanning() = runAniComposeUiTest {
        val pagesLoaded = AtomicInteger()
        setContent {
            TvTheme {
                val pager = remember {
                    Pager(PagingConfig(pageSize = 2, initialLoadSize = 2, prefetchDistance = 1, enablePlaceholders = false)) {
                        object : PagingSource<Int, TvPoster>() {
                            override fun getRefreshKey(state: PagingState<Int, TvPoster>): Int? = null

                            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TvPoster> {
                                pagesLoaded.incrementAndGet()
                                val page = params.key ?: 0
                                return LoadResult.Page(
                                    data = if (page == 0) listOf(
                                        TvPoster(1, "隐藏条目一", null, nsfwMode = NsfwMode.HIDE),
                                        TvPoster(2, "隐藏条目二", null, nsfwMode = NsfwMode.HIDE),
                                    ) else listOf(TvPoster(3, "可见条目", null)),
                                    prevKey = null,
                                    nextKey = if (page == 0) 1 else null,
                                )
                            }
                        }
                    }
                }
                val items = pager.flow.collectAsLazyPagingItems()
                val focus = rememberTvFocusState("poster:")
                TvPage("番剧", {}, focusState = focus) {
                    TvPosterGrid(items, { it.id }, { it }, {}, focusState = focus)
                }
            }
        }
        waitUntil { onAllNodesWithTag("tv-message-action").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("隐藏条目一").assertDoesNotExist()
        onNodeWithTag("tv-message-action").assertIsFocused()
        runOnIdle { assertEquals(1, pagesLoaded.get()) }
        onNodeWithTag("tv-message-action").performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        waitUntil { onAllNodesWithTag("tv-subject-3").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-3").assertIsFocused()
        runOnIdle { assertEquals(2, pagesLoaded.get()) }
    }
}
