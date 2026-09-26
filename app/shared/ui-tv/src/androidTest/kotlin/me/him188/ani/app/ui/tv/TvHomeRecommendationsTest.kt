/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvHomeRecommendationsTest {
    @Test
    fun remoteReachesRecommendationAnchorFromPopularCardsAndEntersItsRail() = runAniComposeUiTest {
        setContent {
            TvTheme {
                val idle = remember { LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true)) }
                val followed = remember {
                    flowOf(PagingData.from(emptyList<FollowedSubjectInfo>(), sourceLoadStates = idle))
                }.collectAsLazyPagingItems()
                val trending = remember {
                    flowOf(PagingData.from((1..12).map { TrendingSubjectInfo(90000 + it, "热门番剧 $it", "") }, sourceLoadStates = idle))
                }.collectAsLazyPagingItems()
                val recommendations = remember {
                    flowOf(PagingData.from<RecommendedItemInfo>((1..12).map { RecommendedSubjectInfo(91000 + it, "推荐番剧 $it", "") }, sourceLoadStates = idle))
                }.collectAsLazyPagingItems()
                TvHomeCatalogue(followed, trending, recommendations, {}, {}, {}, {}, {}, {})
            }
        }
        waitUntil { onAllNodesWithTag("tv-subject-90001").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-90001").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }
        listOf("home-search", "home-collections", "home-history", "home-settings", "home-login").forEach { key ->
            onNodeWithTag(key).assertIsFocused().performKeyInput {
                keyDown(Key.DirectionRight)
                keyUp(Key.DirectionRight)
            }
        }
        onNodeWithTag("home-recommendations").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        waitUntil { onAllNodesWithTag("tv-subject-91001").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-91001").assertIsFocused()
    }

    @Test
    fun recommendationAnchorShowsApiTitleAndOpensItsSubjectId() = runAniComposeUiTest {
        var opened: Int? = null
        setContent {
            TvTheme {
                val followed = remember { flowOf(PagingData.empty<FollowedSubjectInfo>()) }.collectAsLazyPagingItems()
                val trending = remember { flowOf(PagingData.empty<TrendingSubjectInfo>()) }.collectAsLazyPagingItems()
                val recommendations = remember {
                    flowOf(PagingData.from<RecommendedItemInfo>(listOf(RecommendedSubjectInfo(91071, "接口推荐番剧完整标题", ""))))
                }.collectAsLazyPagingItems()
                TvHomeCatalogue(followed, trending, recommendations, { opened = it }, {}, {}, {}, {}, {})
            }
        }
        onNodeWithTag("home-recommendations").performTvClick()
        onNodeWithText("推荐番剧").assertExists()
        onNodeWithTag("tv-subject-title-91071", useUnmergedTree = true).assertTextContains("接口推荐番剧完整标题")
        onNodeWithTag("tv-subject-91071").assertIsFocused().performTvClick()
        runOnIdle { assertEquals(91071, opened) }
    }
}
