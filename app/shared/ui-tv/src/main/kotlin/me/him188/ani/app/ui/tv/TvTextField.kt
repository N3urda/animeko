/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.launch

/** IME 未处理的方向键用于页面导航, 确认键显式开启编辑. */
internal fun Modifier.tvTextFieldNavigation(onEdit: () -> Unit = {}): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val beginEditing by rememberUpdatedState(onEdit)
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }.onPreviewKeyEvent { event ->
        val direction = when (event.key) {
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            else -> null
        }
        when {
            direction != null -> {
                if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                true
            }
            event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                if (event.type == KeyEventType.KeyUp) {
                    beginEditing()
                    scope.launch {
                        // 编辑许可应用到 TextField 后, 输入会话才能接收键盘显示请求.
                        withFrameNanos { }
                        if (focused) keyboard?.show()
                    }
                }
                true
            }
            else -> false
        }
    }
}

/** 焦点进入仅用于导航; 本次焦点收到确认键后才允许创建输入会话. */
@Composable
internal fun TvOutlinedTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (TextFieldLabelScope.() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    var editingRequested by remember { mutableStateOf(false) }
    OutlinedTextField(
        state = state,
        modifier = modifier.onFocusChanged { if (!it.isFocused) editingRequested = false }
            .tvTextFieldNavigation(onEdit = { editingRequested = true }),
        enabled = enabled,
        label = label,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = keyboardOptions.copy(showKeyboardOnFocus = editingRequested),
        onKeyboardAction = onKeyboardAction,
    )
}
