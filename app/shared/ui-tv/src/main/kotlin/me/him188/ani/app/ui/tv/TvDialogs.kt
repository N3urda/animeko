/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

@Composable
internal fun TvConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TvTextDialog(title, message, onDismiss, onConfirm)
}

@Composable
internal fun TvScrollableTextDialog(title: String, text: String, onDismiss: () -> Unit) {
    TvTextDialog(title, text, onDismiss)
}

/** 说明区域独立滚动, 操作按钮始终可见并默认聚焦关闭或取消. */
@Composable
private fun TvTextDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: (() -> Unit)? = null) {
    val cancelFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        Surface {
            Column(
                Modifier.widthIn(max = 620.dp).heightIn(max = 440.dp).padding(28.dp)
                    .onPreviewKeyEvent {
                        when (it.key) {
                            Key.DirectionUp, Key.DirectionDown -> {
                                if (it.type == KeyEventType.KeyDown) scope.launch {
                                    val delta = if (it.key == Key.DirectionDown) 120 else -120
                                    scroll.animateScrollTo((scroll.value + delta).coerceIn(0, scroll.maxValue))
                                }
                                true
                            }
                            else -> false
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(title, fontSize = 23.sp)
                Text(message, Modifier.weight(1f, fill = false).verticalScroll(scroll).testTag("tv-dialog-message"), fontSize = 18.sp)
                Text(
                    when {
                        onConfirm == null -> "上下滚动说明 · 确认或返回关闭"
                        scroll.maxValue > 0 -> "上下滚动说明 · 左右选择 · 返回取消"
                        else -> "左右选择 · 确认执行 · 返回取消"
                    },
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TvButton(
                        if (onConfirm == null) "关闭" else "取消", onDismiss,
                        Modifier.focusRequester(cancelFocus).testTag(if (onConfirm == null) "tv-dialog-close" else "tv-dialog-cancel"),
                    )
                    if (onConfirm != null) TvButton("确认", onConfirm, Modifier.testTag("tv-dialog-confirm"))
                }
            }
        }
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    }
}
