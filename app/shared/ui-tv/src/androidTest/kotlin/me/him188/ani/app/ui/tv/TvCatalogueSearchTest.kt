/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.ui.exploration.search.SubjectPreviewItemInfo
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalTestApi::class)
class TvCatalogueSearchTest {
    @Test
    fun emptyResultsOfferRemoteReturnToInput() = runAniComposeUiTest {
        setContent {
            TvTheme {
                val pager = remember { cataloguePager { PagingSource.LoadResult.Page(emptyList(), null, null) } }
                val results = pager.flow.collectAsLazyPagingItems()
                val focus = rememberTvFocusState("search-result:")
                TvPage("搜索", {}, focusState = focus) {
                    TvButton("输入框", {}, Modifier.tvFocusTarget("search-input", focus).testTag("search-input"))
                    TvCatalogueSearchResults(results, results.loadState.refresh !is LoadState.Loading, focus, {})
                }
            }
        }
        waitUntil { onAllNodesWithTag("tv-message-action").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("修改关键词").performTvClick()
        onNodeWithTag("search-input").assertIsFocused()
    }

    @Test
    fun failedSearchRetriesToReadableResultWithoutUpstreamEnglishTags() = runAniComposeUiTest {
        val attempts = AtomicInteger()
        setContent {
            TvTheme {
                val pager = remember {
                    cataloguePager {
                        if (attempts.incrementAndGet() == 1) PagingSource.LoadResult.Error(IllegalStateException("offline"))
                        else PagingSource.LoadResult.Page(listOf(
                            SubjectPreviewItemInfo(101, "", "完整番剧名称", "Planned 12 eps", null, null,
                                RatingInfo.Empty.copy(score = "8.5", total = 30), false, NsfwMode.DISPLAY),
                        ), null, null)
                    }
                }
                val results = pager.flow.collectAsLazyPagingItems()
                val focus = rememberTvFocusState("search-result:")
                TvPage("搜索", {}, focusState = focus) {
                    TvButton("输入框", {}, Modifier.tvFocusTarget("search-input", focus))
                    TvCatalogueSearchResults(results, results.loadState.refresh !is LoadState.Loading, focus, {})
                }
            }
        }
        waitUntil { onAllNodesWithTag("tv-message-action").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("重试").performTvClick()
        waitUntil { onAllNodesWithTag("tv-subject-101").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-101").assertIsFocused()
        onNodeWithText("评分 8.5").assertExists()
        onNodeWithText("Planned 12 eps").assertDoesNotExist()
    }
}

private fun cataloguePager(load: () -> PagingSource.LoadResult<Int, SubjectPreviewItemInfo>) =
    Pager(PagingConfig(pageSize = 24, enablePlaceholders = false)) {
        object : PagingSource<Int, SubjectPreviewItemInfo>() {
            override fun getRefreshKey(state: PagingState<Int, SubjectPreviewItemInfo>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SubjectPreviewItemInfo> = load()
        }
    }
