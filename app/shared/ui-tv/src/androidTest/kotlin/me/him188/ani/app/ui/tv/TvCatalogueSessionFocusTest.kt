/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvCatalogueSessionFocusTest {
    @Test
    fun successfulLoginMovesFocusFromRemovedLoginToLoadedCollection() = runAniComposeUiTest {
        var session by mutableStateOf<Boolean?>(false)
        setContent {
            TvTheme {
                val focus = rememberTvFocusState("collection:")
                TvPage("收藏", {}, focusState = focus) {
                    TvCatalogueCollectionsGate(session, { session = null }, focus) {
                        TvStaticFocusGroup(focus, "collection:", listOf("collection:71"))
                        TvButton("番剧", {}, Modifier.tvFocusTarget("collection:71", focus).testTag("collection-poster"))
                    }
                }
            }
        }
        onNodeWithText("登录").assertIsFocused().performTvClick()
        runOnIdle { session = true }
        onNodeWithTag("collection-poster").assertIsFocused()
    }

    @Test
    fun sessionLossMovesFocusFromRemovedTabToLogin() = runAniComposeUiTest {
        var session by mutableStateOf<Boolean?>(true)
        setContent {
            TvTheme {
                val focus = rememberTvFocusState("collection-tab-0")
                TvPage("收藏", {}, focusState = focus) {
                    TvCatalogueCollectionsGate(session, {}, focus) {
                        TvStaticFocusGroup(focus, "collection:", emptyList(), fallbackKey = "collection-tab-0")
                        TvButton("在看", {}, Modifier.tvFocusTarget("collection-tab-0", focus).testTag("collection-tab-0"))
                    }
                }
            }
        }
        onNodeWithTag("collection-tab-0").assertIsFocused()
        runOnIdle { session = false }
        onNodeWithText("登录").assertIsFocused()
    }
    @Test
    fun completingSessionCheckKeepsPageBackIfUserIsOutsideCollections() = runAniComposeUiTest {
        var session by mutableStateOf<Boolean?>(null)
        setContent {
            TvTheme {
                val focus = rememberTvFocusState("page-back")
                TvPage("收藏", {}, focusState = focus) {
                    TvCatalogueCollectionsGate(session, {}, focus) {
                        TvStaticFocusGroup(focus, "collection:", listOf("collection:71"))
                        TvButton("番剧", {}, Modifier.tvFocusTarget("collection:71", focus).testTag("collection-poster"))
                    }
                }
            }
        }
        onNodeWithTag("tv-back").assertIsFocused()
        runOnIdle { session = true }
        onNodeWithTag("tv-back").assertIsFocused()
    }

}
