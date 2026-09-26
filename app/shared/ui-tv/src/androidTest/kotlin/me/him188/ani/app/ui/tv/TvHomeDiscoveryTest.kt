/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvHomeDiscoveryTest {
    @Test
    fun standardHomeShowsTwoCompleteContentRows() = runAniComposeUiTest {
        setContent { TvTheme { Box(Modifier.requiredSize(960.dp, 540.dp)) { DiscoveryHome() } } }
        waitUntil { onAllNodesWithTag("tv-subject-90001").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-90001").assertIsFocused()
        val viewport = onNodeWithTag("tv-home-content-viewport").getUnclippedBoundsInRoot()
        listOf(90001, 90003, 91001, 91003).forEach { id ->
            val card = onNodeWithTag("tv-subject-$id").getUnclippedBoundsInRoot()
            assertTrue("Card $id must be fully visible on the home screen", card.top >= viewport.top && card.bottom <= viewport.bottom)
        }
    }

    @Test
    fun remoteFocusUpdatesPreviewWithoutOpeningSubject() = runAniComposeUiTest {
        setContent { TvTheme { DiscoveryHome() } }
        waitUntil { onAllNodesWithTag("tv-subject-90001").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-90001").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        onNodeWithTag("tv-subject-90002").assertIsFocused()
        onNodeWithTag("tv-home-preview-title").assertTextContains("热门番剧 2")
    }
    @Test
    fun genreNavigationOpensTheTagAndReturnsToItsFocus() = runAniComposeUiTest {
        var opened by mutableStateOf<String?>(null)
        setContent {
            TvTheme {
                val holder = rememberSaveableStateHolder()
                if (opened == null) holder.SaveableStateProvider("home") {
                    DiscoveryHome(onSearchTag = { opened = it })
                } else TvButton("返回首页", { opened = null })
            }
        }
        waitUntil { onAllNodesWithTag("tv-subject-90001").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-subject-90001").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("tv-subject-91001").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("tv-home-genre-科幻").assertIsFocused().performTvClick()
        runOnIdle { assertEquals("科幻", opened) }
        onNodeWithText("返回首页").performTvClick()
        onNodeWithTag("tv-home-genre-科幻").assertIsFocused()
    }

    @Test
    fun maskedPreviewDoesNotExposeTitleSummaryOrMetadata() = runAniComposeUiTest {
        setContent {
            TvTheme {
                TvHomePreview(TvPoster(1, "受限标题", "", nsfwMode = NsfwMode.BLUR), "热门番剧",
                    TvHomeSubjectDetails(1, SubjectInfo.Empty.copy(subjectId = 1, nameCn = "受限标题", summary = "受限简介", nsfw = true)),
                    NsfwMode.BLUR, false)
            }
        }
        onNodeWithText("受限标题").assertDoesNotExist()
        onNodeWithText("受限简介").assertDoesNotExist()
        onNodeWithTag("tv-home-preview-title").assertTextContains("内容已遮盖")
    }

    @Test
    fun previousSubjectDetailsNeverAppearUnderTheNextTitle() = runAniComposeUiTest {
        setContent {
            TvTheme {
                TvHomePreview(TvPoster(2, "当前番剧", ""), "热门番剧",
                    TvHomeSubjectDetails(1, SubjectInfo.Empty.copy(subjectId = 1, summary = "上一部的简介")), NsfwMode.DISPLAY, false)
            }
        }
        onNodeWithTag("tv-home-preview-title").assertTextContains("当前番剧")
        onNodeWithText("上一部的简介").assertDoesNotExist()
    }

    @Test
    fun pendingScheduleOpensProtectedDetailsWithoutRevealingCard() = runAniComposeUiTest {
        var opened = false
        var protectedDetailsOpened = 0
        setContent {
            TvTheme {
                TvHomePosterCard(TvPoster(17, "未确认的标题", "", nsfwMode = NsfwMode.BLUR), { opened = true },
                    classificationPending = true, onCheckContent = { protectedDetailsOpened++ })
            }
        }
        repeat(2) { onNodeWithTag("tv-subject-17").performTvClick() }
        onNodeWithText("未确认的标题").assertDoesNotExist()
        runOnIdle { assertTrue(!opened); assertEquals(2, protectedDetailsOpened) }
    }

    @Test
    fun confirmedRestrictedScheduleIsRemovedAndFocusMovesToNextItem() = runAniComposeUiTest {
        var details by mutableStateOf(TvHomeSubjectDetails())
        setContent {
            TvTheme {
                DiscoveryHome(
                    schedule = TvHomeScheduleState(listOf(TvHomeScheduleItem(17, "受限日程", "", "12:00"),
                        TvHomeScheduleItem(18, "下一条日程", "", "13:00")), loading = false),
                    details = details, preference = NsfwMode.HIDE,
                )
            }
        }
        onNodeWithTag("home-recommendations").performTvClick()
        onNodeWithTag("tv-subject-91001").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("tv-subject-17").assertIsFocused()
        onNodeWithText("受限日程").assertDoesNotExist()
        runOnIdle { details = TvHomeSubjectDetails(17, SubjectInfo.Empty.copy(subjectId = 17, nsfw = true)) }
        onNodeWithTag("tv-subject-17").assertDoesNotExist()
        onNodeWithTag("tv-subject-18").assertIsFocused()
    }

    @Test
    fun standardPreviewFitsTwoLinesOfSummary() = runAniComposeUiTest {
        setContent {
            TvTheme {
                Box(Modifier.requiredSize(880.dp, 140.dp)) {
                    TvHomePreview(TvPoster(1, "番剧", ""), "热门番剧",
                        TvHomeSubjectDetails(1, SubjectInfo.Empty.copy(subjectId = 1, summary = "第一行简介\n第二行简介")),
                        NsfwMode.DISPLAY, false)
                }
            }
        }
        val preview = onNodeWithTag("tv-home-preview").getUnclippedBoundsInRoot()
        val summary = onNodeWithTag("tv-home-preview-summary").getUnclippedBoundsInRoot()
        assertTrue("Two summary lines need 36dp; got $summary within $preview", summary.bottom - summary.top >= 36.dp)
        assertTrue(summary.bottom <= preview.bottom)
    }

    @Test
    fun knownRestrictedSubjectStaysHiddenAfterLeavingAndReturningHome() = runAniComposeUiTest {
        var details by mutableStateOf(TvHomeSubjectDetails(90001, SubjectInfo.Empty.copy(subjectId = 90001, nsfw = true)))
        var away by mutableStateOf(false)
        setContent {
            TvTheme {
                val holder = rememberSaveableStateHolder()
                if (!away) holder.SaveableStateProvider("home") {
                    DiscoveryHome(details = details, preference = NsfwMode.HIDE, onSearchTag = { away = true })
                } else TvButton("返回首页", { away = false })
            }
        }
        onNodeWithTag("tv-subject-90001").assertDoesNotExist()
        runOnIdle { details = TvHomeSubjectDetails(); away = true }
        onNodeWithText("返回首页").performTvClick()
        onNodeWithTag("tv-subject-90001").assertDoesNotExist()
    }

}

@Composable
private fun DiscoveryHome(
    onSearchTag: (String) -> Unit = {},
    schedule: TvHomeScheduleState = TvHomeScheduleState(loading = false),
    details: TvHomeSubjectDetails = TvHomeSubjectDetails(),
    preference: NsfwMode = NsfwMode.DISPLAY,
) {
    val idle = remember { LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true)) }
    val followed = remember { flowOf(PagingData.from(emptyList<FollowedSubjectInfo>(), idle)) }.collectAsLazyPagingItems()
    val trending = remember {
        flowOf(PagingData.from((1..10).map { TrendingSubjectInfo(90000 + it, "热门番剧 $it", "") }, idle))
    }.collectAsLazyPagingItems()
    val recommended = remember {
        flowOf(PagingData.from<RecommendedItemInfo>((1..10).map { RecommendedSubjectInfo(91000 + it, "推荐番剧 $it", "") }, idle))
    }.collectAsLazyPagingItems()
    TvHomeCatalogue(followed, trending, recommended, {}, {}, {}, {}, {}, {}, onSearchTag = onSearchTag, schedule = schedule, details = details, preference = preference)
}
