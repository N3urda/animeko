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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager

@Composable
fun TvCaptchaDialogHost(manager: WebSessionManager) {
    val pending by manager.interactiveUi.collectAsStateWithLifecycle()
    val ui = pending ?: return
    val focus = remember(ui) { FocusRequester() }
    Dialog(onDismissRequest = ui.onDismiss) {
        TvTheme {
            Surface {
                Column(Modifier.widthIn(max = 640.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("${ui.title} 需要网页验证", fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("该数据源需要网页交互。请返回播放器，打开「换源」选择其他资源。", fontSize = 18.sp)
                    TvButton("返回播放器", ui.onDismiss, Modifier.focusRequester(focus))
                }
            }
            LaunchedEffect(ui) { focus.requestFocus() }
        }
    }
}
