/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectWithLifecycle
import me.him188.ani.app.ui.subject.collection.COLLECTION_TABS_SORTED
import me.him188.ani.app.ui.subject.collection.UserCollectionsViewModel
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

@Composable
fun TvCollectionsScreen(
    onSubject: (Int) -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = viewModel { UserCollectionsViewModel() }
    val selfInfo by remember { SelfInfoStateProducer().flow }.collectAsStateWithLifecycle()
    val state = vm.state
    val tabStateHolder = rememberSaveableStateHolder()
    val focus = rememberTvFocusState("collection:")
    TvPage("我的收藏", onBack, modifier, focusState = focus, actions = {
        if (selfInfo.isSessionValid == true) TvButton("刷新", state::refresh, Modifier.tvFocusTarget("collection-refresh", focus))
    }) {
        TvCatalogueCollectionsGate(selfInfo.isSessionValid, onLogin, focus) {
            LazyRow(
                Modifier.focusRestorer(), contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(COLLECTION_TABS_SORTED, key = { _, type -> type.name }) { index, type ->
                    val label = tvCollectionLabel(type)
                    TvButton(
                        label, { state.selectTypeIndex(index) },
                        Modifier.tvFocusTarget("collection-tab-$index", focus).testTag("collection-tab-$index"),
                        selected = index == state.selectedTypeIndex,
                    )
                }
            }
            val items = state.getCollectionLazyPagingItems(state.selectedTypeIndex).collectWithLifecycle()
            tabStateHolder.SaveableStateProvider(state.selectedTypeIndex) {
                TvPosterGrid(
                    items,
                    key = { it.subjectId },
                    poster = { TvPoster(it.subjectId, it.subjectInfo.displayName, it.subjectInfo.imageLarge, nsfwMode = it.nsfwMode) },
                    onSubject = onSubject,
                    emptyTitle = "这个分类还没有收藏",
                    focusState = focus,
                    focusGroup = "collection:",
                    fallbackKey = "collection-tab-${state.selectedTypeIndex}",
                )
            }
        }
    }
}

/** 登录状态决定可见内容, 失效的内容或导航焦点均回退到当前可用入口. */
@Composable
internal fun TvCatalogueCollectionsGate(
    isSessionValid: Boolean?,
    onLogin: () -> Unit,
    focus: TvFocusState,
    content: @Composable () -> Unit,
) {
    var previousSession by remember { mutableStateOf(isSessionValid) }
    val focusBeforeTransition = remember(isSessionValid) { focus.requestedKey }
    LaunchedEffect(isSessionValid) {
        val changed = previousSession != isSessionValid
        previousSession = isSessionValid
        if (changed && (focusBeforeTransition.startsWith("collection:") || focusBeforeTransition.startsWith("collection-"))) {
            // 状态切换移除旧节点时的系统回焦不能覆盖收藏页的业务目标.
            val target = when (isSessionValid) {
                false -> "collection:login"
                true -> if (focusBeforeTransition == "collection:login") "collection:" else focusBeforeTransition
                null -> focusBeforeTransition
            }
            focus.requestFocus(target)
        }
    }
    TvStaticFocusGroup(
        focus, "collection-",
        if (isSessionValid == true) listOf("collection-refresh") + COLLECTION_TABS_SORTED.indices.map { "collection-tab-$it" }
        else emptyList(),
        ready = isSessionValid != null,
        fallbackKey = "collection:login",
    )
    when (isSessionValid) {
        null -> {
            TvStaticFocusGroup(focus, "collection:", emptyList(), ready = false)
            TvMessage("正在检查登录状态…")
        }
        false -> {
            TvStaticFocusGroup(focus, "collection:", listOf("collection:login"))
            TvMessage(
                "登录后查看收藏", "收藏和观看进度会同步到你的账号。", "登录", onLogin,
                actionModifier = Modifier.tvFocusTarget("collection:login", focus),
            )
        }
        true -> content()
    }
}

internal fun tvCollectionLabel(type: UnifiedCollectionType): String = when (type) {
    UnifiedCollectionType.WISH -> "想看"
    UnifiedCollectionType.DOING -> "在看"
    UnifiedCollectionType.DONE -> "看过"
    UnifiedCollectionType.ON_HOLD -> "搁置"
    UnifiedCollectionType.DROPPED -> "抛弃"
    UnifiedCollectionType.NOT_COLLECTED -> "未收藏"
}
