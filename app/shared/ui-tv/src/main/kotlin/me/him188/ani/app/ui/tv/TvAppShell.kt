/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

internal val LocalTvShell = staticCompositionLocalOf<TvAppShellState?> { null }

/** 侧栏使用当前页面的焦点注册表, 内容的懒加载恢复与侧栏选择共用同一所有者. */
@Stable
internal class TvAppShellState {
    var pageFocus by mutableStateOf<TvFocusState?>(null)
    var onOverview: (() -> Unit)? by mutableStateOf(null)
    var onRecommendations: (() -> Unit)? by mutableStateOf(null)
    var homeAnchor by mutableStateOf("home-overview")
    var pendingHomeAnchor by mutableStateOf<String?>(null)

    fun enterContent() {
        pageFocus?.let {
            if (it.contentKey.startsWith("home-") && onOverview != null) onOverview?.invoke()
            else it.requestFocus(it.contentKey)
        }
    }

    fun openHomeAnchor(anchor: String) {
        homeAnchor = anchor
        val action = if (anchor == "home-recommendations") onRecommendations else onOverview
        if (action != null) action() else pendingHomeAnchor = anchor
    }
}

@Composable
internal fun TvBindShellPage(
    focus: TvFocusState,
    onOverview: (() -> Unit)? = null,
    onRecommendations: (() -> Unit)? = null,
) {
    val shell = LocalTvShell.current ?: return
    val homeContentKey = focus.contentKey.takeIf { onOverview != null && it.contains(':') }
    DisposableEffect(shell, focus) {
        shell.pageFocus = focus
        if (focus.requestedKey.startsWith("home-") && !focus.contentKey.startsWith("home-")) {
            focus.requestFocus(focus.contentKey)
        }
        onDispose {
            if (shell.pageFocus === focus) {
                shell.pageFocus = null
                shell.onOverview = null
                shell.onRecommendations = null
            }
        }
    }
    SideEffect {
        shell.pageFocus = focus
        shell.onOverview = onOverview
        shell.onRecommendations = onRecommendations
        if (homeContentKey != null) {
            shell.homeAnchor = if (homeContentKey.startsWith("recommended:")) "home-recommendations" else "home-overview"
        }
        if (onOverview != null && onRecommendations != null) {
            shell.pendingHomeAnchor?.let { anchor ->
                shell.pendingHomeAnchor = null
                if (anchor == "home-recommendations") onRecommendations() else onOverview()
            }
        }
    }
}

@Composable
internal fun TvAppShell(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    state: TvAppShellState = remember { TvAppShellState() },
    content: @Composable () -> Unit,
) {
    val placeholderFocus = rememberTvFocusState()
    val focus = state.pageFocus ?: placeholderFocus
    // 播放模式保留同一内容组合位置, 页面状态由导航条目管理.
    CompositionLocalProvider(LocalTvShell provides if (fullscreen) null else state) {
        BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val compact = maxHeight < 460.dp
            Row(Modifier.fillMaxSize().onPreviewKeyEvent { focus.onNavigationKey(it); false }) {
                if (!fullscreen) {
                    Column(
                        Modifier.padding(start = if (compact) 16.dp else 24.dp, top = if (compact) 12.dp else 16.dp)
                            .width(if (compact) 96.dp else 112.dp).fillMaxHeight()
                            .verticalScroll(rememberScrollState()).testTag("tv-app-sidebar"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Animeko", Modifier.padding(vertical = 4.dp), fontSize = if (compact) 20.sp else 24.sp,
                            color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tvAppNavigationItems.forEachIndexed { index, (label, key) ->
                                TvButton(label, { onSelect(key) },
                                    Modifier.fillMaxWidth().height(if (compact) 40.dp else 48.dp)
                                        .tvFocusTarget(key, focus).testTag(key)
                                        .onPreviewKeyEvent { event ->
                                            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                                                Key.DirectionUp -> { tvAppNavigationItems.getOrNull(index - 1)?.let { focus.requestFocus(it.second) }; true }
                                                Key.DirectionDown -> { tvAppNavigationItems.getOrNull(index + 1)?.let { focus.requestFocus(it.second) }; true }
                                                Key.DirectionRight -> { state.enterContent(); true }
                                                Key.DirectionLeft -> true
                                                else -> false
                                            }
                                        }, selected = key == selectedKey,
                                )
                            }
                        }
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxSize().testTag("tv-app-content")
                        .focusProperties {
                            // 只有内容焦点搜索越过左边界时交接侧栏; 输入和内部分类先处理自身按键.
                            onExit = {
                                if (!fullscreen && requestedFocusDirection == FocusDirection.Left) {
                                    focus.requestFocus(selectedKey)
                                    cancelFocusChange()
                                }
                            }
                        }.focusGroup(),
                ) { content() }
            }
        }
    }
}

private val tvAppNavigationItems = listOf(
    "首页" to "home-overview", "推荐" to "home-recommendations", "搜索" to "home-search",
    "收藏" to "home-collections", "历史" to "home-history", "设置" to "home-settings", "账号" to "home-login",
)
