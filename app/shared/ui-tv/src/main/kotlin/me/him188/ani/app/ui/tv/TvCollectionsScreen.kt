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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
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
    TvPage("我的收藏", onBack, modifier, focusState = focus, actions = { TvButton("刷新", state::refresh, Modifier.tvFocusTarget("collection-refresh", focus)) }) {
        if (selfInfo.isSessionValid == false) {
            LaunchedEffect(Unit) { focus.requestFocus("collection-login") }
            TvMessage("登录后查看收藏", "收藏和观看进度会同步到你的账号。", "登录", onLogin,
                actionModifier = Modifier.tvFocusTarget("collection-login", focus))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(COLLECTION_TABS_SORTED, key = { _, type -> type.name }) { index, type ->
                    val label = tvCollectionLabel(type)
                    TvButton(if (index == state.selectedTypeIndex) "● $label" else label, { state.selectTypeIndex(index) }, Modifier.tvFocusTarget("collection-tab-$index", focus))
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
                )
            }
        }
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
