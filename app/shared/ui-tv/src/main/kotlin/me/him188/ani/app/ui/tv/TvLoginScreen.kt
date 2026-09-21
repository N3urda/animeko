/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.user.UserRepository.SendOtpResult
import me.him188.ani.app.ui.login.EmailLoginUiState
import me.him188.ani.app.ui.login.EmailLoginViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Composable
fun TvLoginScreen(onSuccess: () -> Unit, onBack: () -> Unit, onBangumi: () -> Unit) {
    val vm = viewModel { EmailLoginViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()
    TvLoginForm(
        mode = state.mode,
        initialEmail = state.email,
        nextResendTime = state.nextResendTime,
        onSend = { email -> vm.setEmail(email); vm.sendEmailOtp() },
        onSubmit = { otp ->
            if (state.mode == EmailLoginUiState.Mode.LOGIN) vm.submitEmailOtp(otp) else vm.bindOrRebind(otp)
        },
        onSuccess = onSuccess,
        onBack = onBack,
        onBangumi = onBangumi,
    )
}

/** 表单仅在发送请求成功后启用验证码; 邮箱变更会使之前的验证码失效. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvLoginForm(
    mode: EmailLoginUiState.Mode,
    onSend: suspend (String) -> Unit,
    onSubmit: suspend (String) -> SendOtpResult,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    initialEmail: String = "",
    nextResendTime: Instant = Instant.DISTANT_PAST,
    onBangumi: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = rememberTvFocusState("login-email")
    val email = rememberTextFieldState(initialEmail)
    val otp = rememberTextFieldState()
    val emailIntoView = remember { BringIntoViewRequester() }
    val otpIntoView = remember { BringIntoViewRequester() }
    var focusedInput by remember { mutableStateOf("login-email") }
    var sentEmail by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingFocus by remember { mutableStateOf<String?>(null) }
    var localDeadline by remember { mutableStateOf(Instant.DISTANT_PAST) }
    var seconds by remember { mutableStateOf(0L) }
    val deadline = maxOf(nextResendTime, localDeadline)
    val sent = sentEmail != null && sentEmail == email.text.toString().trim()
    LaunchedEffect(deadline) {
        do {
            seconds = ((deadline - Clock.System.now()).inWholeMilliseconds + 999).div(1000).coerceAtLeast(0)
            if (seconds > 0) delay(1000)
        } while (seconds > 0)
    }
    LaunchedEffect(email) {
        snapshotFlow { email.text.toString().trim() }.collect { value ->
            if (sentEmail != null && value != sentEmail) {
                sentEmail = null
                otp.setTextAndPlaceCursorAtEnd("")
                error = null
            }
        }
    }
    LaunchedEffect(busy, pendingFocus, sent) {
        val target = pendingFocus
        if (!busy && target != null && (target != "login-otp" || sent)) {
            // 等待启用状态与倒计时按钮完成布局, 再恢复目标焦点.
            withFrameNanos { }
            focus.requestFocus(target)
            pendingFocus = null
        }
    }
    fun submit(send: Boolean) {
        if (busy || (send && seconds > 0)) return
        keyboard?.hide()
        busy = true
        error = null
        scope.launch {
            try {
                if (send) {
                    val address = email.text.toString().trim()
                    onSend(address)
                    sentEmail = address
                    localDeadline = Clock.System.now() + 30.seconds
                    otp.setTextAndPlaceCursorAtEnd("")
                    pendingFocus = "login-otp"
                } else {
                    when (onSubmit(otp.text.toString().trim())) {
                        is SendOtpResult.Success -> onSuccess()
                        SendOtpResult.InvalidOtp -> {
                            error = "验证码无效或已过期，请重新输入或发送。"
                            pendingFocus = "login-otp"
                        }
                        SendOtpResult.EmailAlreadyExist -> {
                            error = "此邮箱已被其他账号使用，请更换邮箱。"
                            pendingFocus = "login-email"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = if (send) "验证码发送失败，请检查邮箱和网络后重试。" else "验证失败，请检查验证码，或重新发送。"
                pendingFocus = if (send) "login-send" else "login-submit"
            } finally {
                busy = false
            }
        }
    }
    val title = when (mode) {
        EmailLoginUiState.Mode.LOGIN -> "邮箱登录"
        EmailLoginUiState.Mode.BIND -> "绑定邮箱"
        EmailLoginUiState.Mode.REBIND -> "更换邮箱"
    }
    val statusText = error ?: when {
        busy -> "正在处理，请稍候…"
        sent -> "验证码已发送至 $sentEmail。${if (seconds > 0) "${seconds} 秒后可重发。" else "未收到邮件可重新发送。"}"
        else -> "先填写邮箱并发送验证码，再输入邮件中的验证码。"
    }
    CompositionLocalProvider(
        LocalTvFocusState provides focus,
        LocalBringIntoViewSpec provides TvLoginBringIntoViewSpec,
    ) {
        TvRestorePageFocus(focus)
        // 背景覆盖键盘预留区; 页头与说明可让出空间, 输入控件保持同一组合实例.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
                val inputOnly = maxHeight < 200.dp
                val showIntroduction = maxHeight >= 360.dp
                val horizontalPadding = if (maxWidth < 800.dp) 32.dp else 48.dp
                var previousHeight by remember { mutableStateOf(maxHeight) }
                LaunchedEffect(maxHeight) {
                    val heightChanged = previousHeight != maxHeight
                    previousHeight = maxHeight
                    if (heightChanged) {
                        withFrameNanos { }
                        if (focusedInput == "login-otp") otpIntoView.bringIntoView()
                        else emailIntoView.bringIntoView()
                    }
                }
                Column(
                    Modifier.fillMaxSize().onPreviewKeyEvent { focus.onNavigationKey(it); false }
                        .padding(horizontal = horizontalPadding, vertical = if (inputOnly) 8.dp else 24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (inputOnly) 8.dp else 12.dp),
                ) {
                    if (!inputOnly) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            TvButton("返回", onBack, Modifier.tvFocusTarget("page-back", focus).testTag("tv-back"))
                            Text(title, Modifier.weight(1f), fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (mode == EmailLoginUiState.Mode.LOGIN && onBangumi != null) TvButton(
                                "Bangumi 登录", onBangumi,
                                Modifier.tvFocusTarget("login-bangumi", focus).testTag("tv-login-bangumi"),
                            )
                        }
                        if (showIntroduction) Text(
                            if (mode == EmailLoginUiState.Mode.LOGIN) "未注册的邮箱会创建 Animeko 账号。确认键输入，返回键关闭输入法。"
                            else "请输入要绑定的邮箱。确认键输入，返回键关闭输入法。",
                            fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TvLoginStatus(statusText, isError = error != null)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvOutlinedTextField(
                            state = email, enabled = !busy,
                            modifier = Modifier.weight(1f).tvFocusTarget("login-email", focus)
                                .bringIntoViewRequester(emailIntoView)
                                .onFocusChanged { if (it.isFocused) focusedInput = "login-email" }
                                .testTag("tv-login-email"),
                            label = { Text("邮箱") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        )
                        TvButton(
                            if (seconds > 0) "${seconds} 秒后重发" else if (sent) "重新发送" else "发送验证码",
                            { submit(true) }, Modifier.width(180.dp).tvFocusTarget("login-send", focus).testTag("tv-login-send"),
                            enabled = !busy && seconds == 0L && email.text.contains('@'),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvOutlinedTextField(
                            state = otp, enabled = sent && !busy,
                            modifier = Modifier.weight(1f).tvFocusTarget("login-otp", focus)
                                .bringIntoViewRequester(otpIntoView)
                                .onFocusChanged { if (it.isFocused) focusedInput = "login-otp" }
                                .testTag("tv-login-otp"),
                            label = { Text("邮件验证码") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        TvButton(
                            if (mode == EmailLoginUiState.Mode.LOGIN) "确认登录" else "确认绑定",
                            { submit(false) }, Modifier.width(180.dp).tvFocusTarget("login-submit", focus).testTag("tv-login-submit"),
                            enabled = !busy && sent && otp.text.isNotBlank(),
                        )
                    }
                    if (inputOnly) TvLoginStatus(statusText, isError = error != null)
                }
            }
        }
    }
}

@Composable
private fun TvLoginStatus(text: String, isError: Boolean) {
    Text(
        text, Modifier.fillMaxWidth().testTag("tv-login-status"),
        fontSize = 14.sp, maxLines = 3,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

/** 输入框与内部光标共享可见性约束, 已完整显示的区域无需额外对齐. */
@OptIn(ExperimentalFoundationApi::class)
private object TvLoginBringIntoViewSpec : BringIntoViewSpec {}
