/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

internal data class TvPlayerOption(
    val id: String,
    val title: String,
    val detail: String = "",
    val selected: Boolean = false,
    val enabled: Boolean = true,
)

/** 列表按业务键恢复焦点, 当前选项在进入面板时滚动到可见区域. */
@Composable
internal fun TvPlayerOptionPanel(
    title: String,
    options: List<TvPlayerOption>,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    initialOptionId: String? = null,
    message: String? = null,
) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val initial = initialOptionId ?: options.firstOrNull { it.selected && it.enabled }?.id
            ?: options.firstOrNull { it.enabled }?.id
        val focus = rememberTvFocusState(initial?.let { "option:$it" } ?: "menu-close")
        val list = rememberLazyListState()
        val keys = options.filter { it.enabled }.map { "option:${it.id}" }
        CompositionLocalProvider(LocalTvFocusState provides focus) {
            TvRestorePageFocus(focus)
            TvLazyFocusGroup(
                state = focus, groupKey = "option:", itemKeys = keys,
                ready = initialOptionId == null || focus.requestedKey != "option:$initialOptionId" || "option:$initialOptionId" in keys,
                fallbackKey = "menu-close",
                scrollToItem = { keyIndex ->
                    val itemIndex = options.indexOfFirst { "option:${it.id}" == keys[keyIndex] }
                    if (itemIndex >= 0) list.scrollToItem(itemIndex)
                },
                isItemVisible = { key -> list.layoutInfo.visibleItemsInfo.any { it.key == key } },
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f))) {
                Column(
                    Modifier.align(Alignment.CenterEnd).widthIn(max = 460.dp).fillMaxWidth().fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                        .onPreviewKeyEvent { focus.onNavigationKey(it); false }
                        .padding(horizontal = 28.dp, vertical = 24.dp).testTag("tv-player-menu"),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(title, Modifier.weight(1f), fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TvButton("关闭", onBack, Modifier.tvFocusTarget("menu-close", focus).testTag("tv-player-menu-close"))
                    }
                    if (!message.isNullOrBlank()) {
                        Text(message, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f).testTag("tv-player-menu-list"),
                        state = list,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(options, key = { "option:${it.id}" }) { option ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TvButton(
                                    text = option.title,
                                    onClick = { onSelect(option.id) },
                                    modifier = Modifier.fillMaxWidth().tvFocusTarget("option:${option.id}", focus)
                                        .testTag("tv-player-option-${option.id}"),
                                    enabled = option.enabled,
                                    selected = option.selected,
                                )
                                if (option.selected || option.detail.isNotBlank()) {
                                    Text(
                                        (if (option.selected) "当前选项 · " else "") + option.detail,
                                        Modifier.padding(horizontal = 14.dp),
                                        fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        color = if (option.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Text("上下选择 · 确认应用 · 返回关闭", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
