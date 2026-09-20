/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvFocusRestorationTest {
    @Test
    fun restoredOffscreenKeyIsScrolledIntoView() = runAniComposeUiTest {
        setContent { TvTheme { FocusList((1..40).toList(), initialKey = "item:35") } }
        waitUntil { onAllNodesWithTag("item:35").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("item:35").assertIsFocused()
    }

    @Test
    fun returningAfterReorderingRestoresBusinessId() = runAniComposeUiTest {
        var visible by mutableStateOf(true)
        var ids by mutableStateOf((1..40).toList())
        setContent {
            TvTheme {
                val holder = rememberSaveableStateHolder()
                if (visible) holder.SaveableStateProvider("catalogue") {
                    FocusList(ids, "item:35", onOpen = { visible = false })
                } else {
                    TvButton("返回", { ids = ids.reversed(); visible = true }, Modifier.testTag("return"))
                }
            }
        }
        waitUntil { onAllNodesWithTag("item:35").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("item:35").performTvClick()
        onNodeWithTag("return").performTvClick()
        waitUntil { onAllNodesWithTag("item:35").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("item:35").assertIsFocused()
    }

    @Test
    fun returningAfterDeletionRestoresSavedNeighbourPosition() = runAniComposeUiTest {
        var visible by mutableStateOf(true)
        var ids by mutableStateOf(listOf(11, 22, 33, 44))
        setContent {
            TvTheme {
                val holder = rememberSaveableStateHolder()
                if (visible) holder.SaveableStateProvider("catalogue") {
                    FocusList(ids, "item:33", onOpen = { visible = false })
                } else TvButton("返回", { ids = listOf(11, 22, 44); visible = true }, Modifier.testTag("return"))
            }
        }
        onNodeWithTag("item:33").performTvClick()
        onNodeWithTag("return").performTvClick()
        onNodeWithTag("item:44").assertIsFocused()
    }

    @Test
    fun refreshReorderingKeepsTheSameBusinessId() = runAniComposeUiTest {
        var ids by mutableStateOf((1..40).toList())
        setContent { TvTheme { FocusList(ids, "item:35") } }
        waitUntil { onAllNodesWithTag("item:35").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { ids = ids.reversed() }
        onNodeWithTag("item:35").assertIsFocused()
    }

    @Test
    fun removedTargetFallsBackToNeighbourThenBackOnEmpty() = runAniComposeUiTest {
        var ids by mutableStateOf(listOf(11, 22, 33))
        setContent { TvTheme { FocusList(ids, "item:22") } }
        onNodeWithTag("item:22").assertIsFocused()
        runOnIdle { ids = listOf(33, 11) }
        onNodeWithTag("item:11").assertIsFocused()
        runOnIdle { ids = emptyList() }
        onNodeWithTag("tv-back").assertIsFocused()
    }

    @Test
    fun failedSearchFocusesRetryInsteadOfThePageBackButton() = runAniComposeUiTest {
        var failed by mutableStateOf(false)
        lateinit var focus: TvFocusState
        setContent {
            TvTheme {
                val pageFocus = rememberTvFocusState("search-input")
                SideEffect { focus = pageFocus }
                TvPage("搜索", {}, focusState = pageFocus) {
                    OutlinedTextField("", {}, Modifier.tvFocusTarget("search-input", pageFocus).testTag("input"))
                    TvStaticFocusGroup(pageFocus, "result:", if (failed) listOf("result:retry") else emptyList(), failed, "search-input")
                    if (failed) TvButton("重试", {}, Modifier.tvFocusTarget("result:retry", pageFocus).testTag("retry"))
                }
            }
        }
        onNodeWithTag("input").assertIsFocused()
        runOnIdle { focus.requestFocus("result:"); failed = true }
        onNodeWithTag("retry").assertIsFocused()
    }

    @Test
    fun searchStartsOnInputThenMovesToFirstCompletedResult() = runAniComposeUiTest {
        var ready by mutableStateOf(false)
        var ids by mutableStateOf(emptyList<Int>())
        lateinit var focus: TvFocusState
        setContent {
            TvTheme {
                val pageFocus = rememberTvFocusState("search-input")
                SideEffect { focus = pageFocus }
                Box(Modifier.height(420.dp)) {
                    TvPage("搜索", {}, focusState = pageFocus) {
                        OutlinedTextField("", {}, Modifier.tvFocusTarget("search-input", pageFocus).testTag("input"))
                        FocusRows(ids, pageFocus, ready, "search-input")
                    }
                }
            }
        }
        onNodeWithTag("input").assertIsFocused()
        runOnIdle { focus.requestFocus("item:") }
        onNodeWithTag("input").assertIsFocused()
        runOnIdle { ids = listOf(17, 21); ready = true }
        onNodeWithTag("item:17").assertIsFocused()
        runOnIdle { ids = emptyList() }
        onNodeWithTag("input").assertIsFocused()
    }
}

@Composable
private fun FocusList(ids: List<Int>, initialKey: String, onOpen: () -> Unit = {}) {
    val focus = rememberTvFocusState(initialKey)
    Box(Modifier.height(320.dp)) {
        TvPage("目录", {}, focusState = focus) {
            FocusRows(ids, focus, true, "page-back", onOpen)
        }
    }
}

@Composable
private fun FocusRows(ids: List<Int>, focus: TvFocusState, ready: Boolean, fallback: String, onOpen: () -> Unit = {}) {
    val list = rememberLazyListState()
    val keys = ids.map { "item:$it" }
    TvLazyFocusGroup(
        focus, "item:", keys, ready, fallback,
        scrollToItem = { list.scrollToItem(it) },
        isItemVisible = { key -> list.layoutInfo.visibleItemsInfo.any { it.key == key } },
    )
    LazyColumn(state = list) {
        items(ids, key = { "item:$it" }) { id ->
            TvButton("条目 $id", onOpen, Modifier.fillMaxWidth().tvFocusTarget("item:$id", focus).testTag("item:$id"))
        }
    }
}
