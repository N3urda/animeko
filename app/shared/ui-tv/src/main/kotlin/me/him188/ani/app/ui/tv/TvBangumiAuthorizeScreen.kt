/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import me.him188.ani.app.navigation.OpenBrowserResult
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.LocalBrowserNavigator
import me.him188.ani.app.ui.oauth.AuthState
import me.him188.ani.app.ui.oauth.BangumiAuthorizeViewModel

/** 授权进行中的状态不携带账号身份, 刷新二维码时沿用已确定的登录或绑定模式. */
internal fun tvBangumiIsRegister(state: AuthState?, previous: Boolean?): Boolean? = when (state) {
    AuthState.NoAniAccount -> true
    is AuthState.LoggedInAni -> false
    is AuthState.Failed -> !state.loggedIn
    else -> previous
}

@Composable
fun TvBangumiAuthorizeScreen(onSuccess: () -> Unit, onBack: () -> Unit) {
    val vm = viewModel { BangumiAuthorizeViewModel() }
    val authState by vm.state.collectAsStateWithLifecycle<AuthState?>(null)
    var isRegister by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(authState) { isRegister = tvBangumiIsRegister(authState, isRegister) }
    val currentIsRegister by rememberUpdatedState(isRegister)
    val scope = rememberCoroutineScope()
    val browser = LocalBrowserNavigator.current
    val context = LocalContext.current
    var browserError by remember { mutableStateOf(false) }
    val request = remember(vm, scope) {
        TvOAuthRequest(scope, authorize = { showUrl ->
            vm.doOAuth(requireNotNull(currentIsRegister)) {
                showUrl(it)
            }
        }, cancelAuthorization = vm::cancelCurrentOAuth)
    }
    val state by request.state.collectAsStateWithLifecycle()
    DisposableEffect(request) { onDispose { request.cancel() } }
    LaunchedEffect(state) { if (state == TvOAuthRequestState.Success) onSuccess() }
    val back = { request.cancel(); onBack() }
    BackHandler(onBack = back)
    TvBangumiAuthorizeContent(
        state = state,
        binding = isRegister == false,
        onStart = { browserError = false; request.start() },
        onBrowser = { browserError = browser.openBrowser(context, it) is OpenBrowserResult.Failure },
        onBack = back,
        browserError = browserError,
        enabled = isRegister != null,
    )
}

/** 手机打开同一授权链接, 电视继续通过共享 OAuth 服务等待授权结果. */
@Composable
internal fun TvBangumiAuthorizeContent(
    state: TvOAuthRequestState,
    binding: Boolean,
    onStart: () -> Unit,
    onBrowser: (String) -> Unit,
    onBack: () -> Unit,
    browserError: Boolean = false,
    enabled: Boolean = true,
) {
    val focus = rememberTvFocusState("oauth-start")
    val url = (state as? TvOAuthRequestState.Awaiting)?.url
    val qr = remember(url) {
        url?.let { value ->
            runCatching {
                val bits = encodeTvQrCode(value)
                val pixels = IntArray(bits.width * bits.height) { index ->
                    if (bits[index % bits.width, index / bits.width]) 0xff000000.toInt() else 0xffffffff.toInt()
                }
                Bitmap.createBitmap(pixels, bits.width, bits.height, Bitmap.Config.ARGB_8888).asImageBitmap()
            }.getOrNull()
        }
    }
    LaunchedEffect(state == TvOAuthRequestState.Loading) {
        if (state == TvOAuthRequestState.Loading) focus.requestFocus("oauth-cancel")
    }
    TvPage(if (binding) "绑定 Bangumi" else "Bangumi 登录", onBack, focusState = focus) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxWidth < 600.dp
            val qrSize = minOf(if (compact) 192.dp else 240.dp, maxHeight, maxWidth * .4f)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(qrSize).background(if (qr != null) Color.White else Color.Transparent), contentAlignment = Alignment.Center) {
                    if (qr != null) Image(qr, "手机扫描 Bangumi 授权二维码", Modifier.fillMaxSize().testTag("tv-oauth-qr"), filterQuality = FilterQuality.None)
                    else Text(if (state == TvOAuthRequestState.Loading) "正在获取二维码…" else "手机扫码授权", fontSize = 20.sp)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                    Text("使用手机相机扫码，在手机浏览器登录 Bangumi 并授权。完成后电视会自动${if (binding) "关联账号" else "登录"}。", fontSize = if (compact) 16.sp else 18.sp)
                    Text(when (state) {
                        TvOAuthRequestState.Idle -> "选择下方按钮获取本次授权二维码。"
                        TvOAuthRequestState.Loading -> "正在连接授权服务…"
                        is TvOAuthRequestState.Awaiting -> if (qr == null) "二维码生成失败，请重新获取或使用电视浏览器。" else "等待手机授权，两分钟未完成将自动超时。"
                        TvOAuthRequestState.Expired -> "二维码已超时，请重新获取。"
                        TvOAuthRequestState.Failed -> "授权未完成，请检查网络后重试。"
                        TvOAuthRequestState.Success -> "授权成功，正在返回…"
                    }, fontSize = 16.sp, modifier = Modifier.testTag("tv-oauth-status"))
                    if (browserError) Text("电视未能打开浏览器，请继续用手机扫码。", fontSize = 16.sp)
                    TvButton(if (state == TvOAuthRequestState.Idle) "获取登录二维码" else "重新获取二维码", onStart,
                        Modifier.fillMaxWidth().tvFocusTarget("oauth-start", focus).testTag("tv-oauth-start"),
                        enabled = enabled && state != TvOAuthRequestState.Loading && state != TvOAuthRequestState.Success)
                    if (url != null) TvButton("在电视浏览器授权", { onBrowser(url) },
                        Modifier.fillMaxWidth().tvFocusTarget("oauth-browser", focus).testTag("tv-oauth-browser"))
                    TvButton("取消并返回", onBack, Modifier.fillMaxWidth().tvFocusTarget("oauth-cancel", focus).testTag("tv-oauth-back"))
                }
            }
        }
    }
}
