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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.user.UserRepository.SendOtpResult
import me.him188.ani.app.ui.login.EmailLoginUiState
import me.him188.ani.app.ui.login.EmailLoginViewModel
import kotlin.time.Clock

@Composable
fun TvLoginScreen(onSuccess: () -> Unit, onBack: () -> Unit) {
    val vm = viewModel { EmailLoginViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var sent by remember { mutableStateOf(false) }
    val email = rememberTextFieldState(state.email)
    val otp = rememberTextFieldState()
    LaunchedEffect(email) {
        snapshotFlow { email.text.toString() }.collect {
            vm.setEmail(it)
            sent = false
            otp.setTextAndPlaceCursorAtEnd("")
        }
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var seconds by remember { mutableStateOf(0L) }
    LaunchedEffect(state.nextResendTime) {
        do {
            seconds = (state.nextResendTime - Clock.System.now()).inWholeSeconds.coerceAtLeast(0)
            if (seconds > 0) delay(1000)
        } while (seconds > 0)
    }
    fun submit(send: Boolean) {
        if (busy) return
        keyboard?.hide()
        busy = true
        error = null
        scope.launch {
            try {
                if (send) {
                    vm.setEmail(email.text.toString().trim())
                    vm.sendEmailOtp()
                    sent = true
                } else {
                    val result = if (state.mode == EmailLoginUiState.Mode.LOGIN) vm.submitEmailOtp(otp.text.toString().trim())
                    else vm.bindOrRebind(otp.text.toString().trim())
                    when (result) {
                        is SendOtpResult.Success -> onSuccess()
                        SendOtpResult.InvalidOtp -> error = "验证码无效或已过期，请重新输入或发送。"
                        SendOtpResult.EmailAlreadyExist -> error = "此邮箱已被其他账号使用，请更换邮箱。"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = if (send) "验证码发送失败，请检查邮箱和网络后重试。" else "验证失败，请检查验证码，或重新发送。"
            } finally {
                busy = false
            }
        }
    }
    TvPage(if (state.mode == EmailLoginUiState.Mode.LOGIN) "邮箱登录" else "绑定邮箱", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("用遥控器确认键打开输入法。未注册的邮箱会创建 Animeko 账号。")
            TvOutlinedTextField(
                state = email,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("tv-login-email"),
                label = { Text("邮箱") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            TvButton(
                if (seconds > 0) "${seconds} 秒后可重发" else if (sent) "重新发送验证码" else "发送验证码",
                { submit(true) }, Modifier.testTag("tv-login-send"),
                enabled = !busy && seconds == 0L && email.text.contains('@'),
            )
            if (sent) {
                TvOutlinedTextField(
                    state = otp,
                    modifier = Modifier.fillMaxWidth().testTag("tv-login-otp"),
                    label = { Text("邮件验证码") }, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                TvButton("确认登录", { submit(false) }, Modifier.testTag("tv-login-submit"), enabled = !busy && otp.text.isNotBlank())
            }
            if (busy) Text("正在处理…")
            error?.let { Text(it) }
        }
    }
}
