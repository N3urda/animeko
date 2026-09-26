/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalTestApi::class)
class TvHomeRecommendationsPagingTest {
    @Test
    fun remotePagesRecommendationsAndReturningRestoresTheSelectedBusinessId() = runAniComposeUiTest {
        var opened by mutableStateOf<Int?>(null)
        val requests = AtomicInteger()
        setContent {
            TvTheme {
                val scope = rememberCoroutineScope()
                val flow = remember {
                    recommendationPager { page ->
                        requests.incrementAndGet()
                        PagingSource.LoadResult.Page(
                            ((page * 8 + 1)..(page * 8 + 8)).map { recommendation(it) },
                            null, if (page == 0) 1 else null,
                        )
                    }.flow.cachedIn(scope)
                }
                val holder = rememberSaveableStateHolder()
                if (opened == null) holder.SaveableStateProvider("home") {
                    Box(Modifier.requiredSize(960.dp, 540.dp)) { RecommendationHome(flow, { opened = it }) }
                } else TvButton("返回首页", { opened = null })
            }
        }
        onNodeWithTag("home-recommendations").performTvClick()
        waitUntil { onAllNodesWithTag("tv-subject-91001").fetchSemanticsNodes().isNotEmpty() }
        listOf(91001, 91005).forEach { id ->
            onNodeWithTag("tv-subject-$id").assertIsFocused().performKeyInput {
                keyDown(Key.DirectionDown)
                keyUp(Key.DirectionDown)
            }
            waitUntil { onAllNodesWithTag("tv-subject-${id + 4}").fetchSemanticsNodes().isNotEmpty() }
        }
        onNodeWithTag("tv-subject-91009").assertIsFocused().performTvClick()
        runOnIdle { assertEquals(91009, opened); assertTrue(requests.get() >= 2) }
        onNodeWithText("返回首页").performTvClick()
        waitUntil { onAllNodesWithTag("tv-subject-91009").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-91009").assertIsFocused()
    }

    @Test
    fun failedRecommendationsOfferRetryAndFocusTheLoadedResult() = runAniComposeUiTest {
        val attempts = AtomicInteger()
        setContent {
            TvTheme {
                val flow = remember {
                    recommendationPager {
                        if (attempts.incrementAndGet() == 1) PagingSource.LoadResult.Error(IllegalStateException("offline"))
                        else PagingSource.LoadResult.Page(listOf(recommendation(1)), null, null)
                    }.flow
                }
                RecommendationHome(flow)
            }
        }
        onNodeWithTag("home-recommendations").performTvClick()
        waitUntil { onAllNodesWithTag("tv-home-recommended-retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-home-recommended-retry").assertIsFocused().performTvClick()
        waitUntil { onAllNodesWithTag("tv-subject-91001").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-91001").assertIsFocused()
    }

    @Test
    fun failedAppendRetryKeepsTheLastLoadedCardAndContinuesToTheNextPage() = runAniComposeUiTest {
        val appendAttempts = AtomicInteger()
        val nextPage = CompletableDeferred<Unit>()
        setContent {
            TvTheme {
                val flow = remember {
                    recommendationPager { page ->
                        if (page == 0) {
                            PagingSource.LoadResult.Page((1..8).map { recommendation(it) }, null, 1)
                        } else if (appendAttempts.incrementAndGet() == 1) {
                            PagingSource.LoadResult.Error(IllegalStateException("append offline"))
                        } else {
                            nextPage.await()
                            PagingSource.LoadResult.Page((9..16).map { recommendation(it) }, null, null)
                        }
                    }.flow
                }
                Box(Modifier.requiredSize(960.dp, 540.dp)) { RecommendationHome(flow) }
            }
        }
        onNodeWithTag("home-recommendations").performTvClick()
        onNodeWithTag("tv-subject-91001").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        repeat(3) { index ->
            onNodeWithTag("tv-subject-${91005 + index}").assertIsFocused().performKeyInput {
                keyDown(Key.DirectionRight)
                keyUp(Key.DirectionRight)
            }
            waitUntil { onAllNodesWithTag("tv-subject-${91006 + index}").fetchSemanticsNodes().isNotEmpty() }
        }
        waitUntil { onAllNodesWithTag("tv-home-recommended-retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-91008").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        onNodeWithTag("tv-home-recommended-retry").assertIsFocused().performTvClick()
        waitUntil { appendAttempts.get() == 2 }
        onNodeWithTag("tv-subject-91008").assertIsFocused()
        runOnIdle { nextPage.complete(Unit) }
        waitUntil { onAllNodesWithTag("tv-subject-91009").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-91008").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        onNodeWithTag("tv-subject-91012").assertIsFocused()
    }

    @Test
    fun downWaitsForAnAppendingPartialRowAndKeepsTheClosestColumn() = runAniComposeUiTest {
        val nextPage = CompletableDeferred<Unit>()
        setContent {
            TvTheme {
                val flow = remember {
                    recommendationPager { page ->
                        if (page == 0) PagingSource.LoadResult.Page((1..8).map { recommendation(it) }, null, 1)
                        else {
                            nextPage.await()
                            PagingSource.LoadResult.Page((9..10).map { recommendation(it) }, null, null)
                        }
                    }.flow
                }
                Box(Modifier.requiredSize(960.dp, 540.dp)) { RecommendationHome(flow) }
            }
        }
        onNodeWithTag("home-recommendations").performTvClick()
        onNodeWithTag("tv-subject-91001").performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        for (number in 5..7) onNodeWithTag("tv-subject-${91000 + number}").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
        }
        onNodeWithTag("tv-subject-91008").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("tv-subject-91008").assertIsFocused()
        runOnIdle { nextPage.complete(Unit) }
        waitUntil { onAllNodesWithTag("tv-subject-91010").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-91010").assertIsFocused()
    }

    @Test
    fun hiddenPendingCardLetsBusinessFocusReplacementChooseItsNeighbour() = runAniComposeUiTest {
        val nextPage = CompletableDeferred<Unit>()
        var details by mutableStateOf(TvHomeSubjectDetails())
        setContent {
            TvTheme {
                val flow = remember {
                    recommendationPager { page ->
                        if (page == 0) PagingSource.LoadResult.Page((1..8).map { recommendation(it) }, null, 1)
                        else {
                            nextPage.await()
                            PagingSource.LoadResult.Page(emptyList(), null, null)
                        }
                    }.flow
                }
                Box(Modifier.requiredSize(960.dp, 540.dp)) {
                    RecommendationHome(flow, details = details, preference = NsfwMode.HIDE)
                }
            }
        }
        onNodeWithTag("home-recommendations").performTvClick()
        onNodeWithTag("tv-subject-91001").performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        for (number in 5..7) onNodeWithTag("tv-subject-${91000 + number}").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
        }
        onNodeWithTag("tv-subject-91008").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        runOnIdle { details = TvHomeSubjectDetails(91008, SubjectInfo.Empty.copy(subjectId = 91008, nsfw = true)) }
        onNodeWithTag("tv-subject-91008").assertDoesNotExist()
        runOnIdle { nextPage.complete(Unit) }
        onNodeWithTag("tv-subject-91007").assertIsFocused()
    }

    @Test
    fun emptyRecommendationsOfferRefreshWithoutInventingItems() = runAniComposeUiTest {
        val attempts = AtomicInteger()
        setContent {
            TvTheme {
                val flow = remember {
                    recommendationPager {
                        PagingSource.LoadResult.Page(if (attempts.incrementAndGet() == 1) emptyList() else listOf(recommendation(1)), null, null)
                    }.flow
                }
                RecommendationHome(flow)
            }
        }
        onNodeWithTag("home-recommendations").performTvClick()
        waitUntil { onAllNodesWithTag("tv-home-recommended-refresh").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("暂无推荐番剧").assertExists()
        onNodeWithTag("tv-subject-91001").assertDoesNotExist()
        onNodeWithTag("tv-home-recommended-refresh").assertIsFocused().performTvClick()
        waitUntil { onAllNodesWithTag("tv-subject-91001").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-91001").assertIsFocused()
    }

    @Test
    fun loadingRecommendationsDoNotStealFocusAfterUserMovesToAccount() = runAniComposeUiTest {
        val loaded = CompletableDeferred<Unit>()
        setContent {
            TvTheme {
                val flow = remember {
                    recommendationPager { loaded.await(); PagingSource.LoadResult.Page(listOf(recommendation(1)), null, null) }.flow
                }
                RecommendationHome(flow)
            }
        }
        onNodeWithTag("home-recommendations").performTvClick()
        onNodeWithText("正在加载推荐…").assertExists()
        listOf("home-recommendations", "home-search", "home-collections", "home-history", "home-settings").forEach { key ->
            onNodeWithTag(key).assertIsFocused().performKeyInput {
                keyDown(Key.DirectionDown)
                keyUp(Key.DirectionDown)
            }
        }
        onNodeWithTag("home-login").assertIsFocused()
        runOnIdle { loaded.complete(Unit) }
        waitUntil { onAllNodesWithTag("tv-subject-91001").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("home-login").assertIsFocused()
    }

    @Test
    fun standardGridKeepsRowBoundariesAndReturnsFromSidebar() = verifyGridNavigation(960, 540, 4)

    @Test
    fun compactGridKeepsRowBoundariesAndReturnsFromSidebar() = verifyGridNavigation(720, 405, 3)

    private fun verifyGridNavigation(width: Int, height: Int, columns: Int) = runAniComposeUiTest {
        setContent {
            TvTheme {
                val flow = remember { flowOf(PagingData.from<RecommendedItemInfo>((1..10).map { recommendation(it) })) }
                Box(Modifier.requiredSize(width.dp, height.dp)) { RecommendationHome(flow) }
            }
        }
        val navigation = listOf("home-overview", "home-recommendations", "home-search", "home-collections", "home-history", "home-settings", "home-login")
        val viewport = onNodeWithTag("tv-home-content-viewport").getUnclippedBoundsInRoot()
        assertTrue("All sidebar actions fit the viewport", onNodeWithTag("home-login").getUnclippedBoundsInRoot().bottom <= viewport.bottom)
        navigation.zipWithNext().forEach { (previous, next) ->
            val first = onNodeWithTag(previous).getUnclippedBoundsInRoot()
            val second = onNodeWithTag(next).getUnclippedBoundsInRoot()
            assertTrue("Navigation belongs to the left sidebar", first.right < viewport.left)
            assertTrue("Navigation flows vertically", first.bottom <= second.top)
        }
        onNodeWithTag("home-recommendations").performTvClick()
        onNodeWithTag("tv-subject-91001").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
        }
        onNodeWithTag("home-recommendations").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
        }
        for (number in 1..columns) {
            onNodeWithTag("tv-subject-${91000 + number}").assertIsFocused().performKeyInput {
                keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
            }
        }
        onNodeWithTag("tv-subject-${91000 + columns}").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("tv-subject-${91000 + columns * 2}").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        val thirdRow = minOf(columns * 3, 10)
        onNodeWithTag("tv-subject-${91000 + thirdRow}").assertIsFocused()
        if (thirdRow < 10) onNodeWithTag("tv-subject-${91000 + thirdRow}").performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("tv-subject-91010").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
        }
        onNodeWithTag("tv-subject-91010").assertIsFocused()
        val lastRowFirst = (9 / columns) * columns + 1
        for (number in 10 downTo lastRowFirst) {
            onNodeWithTag("tv-subject-${91000 + number}").assertIsFocused().performKeyInput {
                keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
            }
        }
        onNodeWithTag("home-recommendations").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
        }
        onNodeWithTag("tv-subject-${91000 + lastRowFirst}").assertIsFocused()
    }

    @Test
    fun recommendationCardFitsCompactHomeAfterAnchorNavigation() = verifyRecommendationFits(720, 405)

    @Test
    fun recommendationCardFitsStandardHomeAfterAnchorNavigation() = verifyRecommendationFits(960, 540)

    private fun verifyRecommendationFits(width: Int, height: Int) = runAniComposeUiTest {
        setContent {
            TvTheme {
                val flow = remember { flowOf(PagingData.from<RecommendedItemInfo>(listOf(recommendation(1)))) }
                Box(Modifier.requiredSize(DpSize(width.dp, height.dp))) { RecommendationHome(flow) }
            }
        }
        onNodeWithTag("home-recommendations").performTvClick()
        waitUntil { onAllNodesWithTag("tv-subject-91001").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-91001").assertIsFocused()
        val viewport = onNodeWithTag("tv-home-content-viewport").getUnclippedBoundsInRoot()
        val card = onNodeWithTag("tv-subject-91001").getUnclippedBoundsInRoot()
        val title = onNodeWithTag("tv-subject-title-91001", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(card.top >= viewport.top && card.bottom <= viewport.bottom)
        assertTrue(title.top >= viewport.top && title.bottom <= viewport.bottom)
    }
}

@Composable
private fun RecommendationHome(
    flow: Flow<PagingData<RecommendedItemInfo>>,
    onSubject: (Int) -> Unit = {},
    details: TvHomeSubjectDetails = TvHomeSubjectDetails(),
    preference: NsfwMode = NsfwMode.DISPLAY,
) {
    val followed = remember { flowOf(PagingData.empty<FollowedSubjectInfo>()) }.collectAsLazyPagingItems()
    val trending = remember { flowOf(PagingData.from(listOf(TrendingSubjectInfo(90001, "热门番剧条目", "")))) }.collectAsLazyPagingItems()
    val recommendations = flow.collectAsLazyPagingItems()
    TvHomeCatalogue(followed, trending, recommendations, onSubject, {}, {}, {}, {}, {}, details = details, preference = preference)
}

private fun recommendation(number: Int) = RecommendedSubjectInfo(91000 + number, "推荐番剧 $number", "")

private fun recommendationPager(load: suspend (Int) -> PagingSource.LoadResult<Int, RecommendedItemInfo>) =
    Pager(PagingConfig(pageSize = 8, initialLoadSize = 8, prefetchDistance = 1, enablePlaceholders = false)) {
        object : PagingSource<Int, RecommendedItemInfo>() {
            override fun getRefreshKey(state: PagingState<Int, RecommendedItemInfo>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RecommendedItemInfo> = load(params.key ?: 0)
        }
    }
