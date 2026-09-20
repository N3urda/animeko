/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** 页面焦点以业务键持久化; 懒加载容器负责把目标滚入布局后才请求焦点. */
@Stable
class TvFocusState internal constructor(
    private val savedKey: MutableState<String?>,
    private val savedIndex: MutableState<Int>,
    internal val initialKey: String,
) {
    internal val groups = mutableStateMapOf<String, TvFocusGroupSpec>()
    internal val layouts = mutableMapOf<String, TvFocusGroupLayout>()
    internal val nodes = mutableStateMapOf<String, FocusRequester>()
    internal var requestVersion by mutableIntStateOf(0)
    internal val requestedKey: String get() = savedKey.value ?: initialKey
    private var hasRestored = false
    private var manualNavigation = false

    internal fun onNavigationKey(event: KeyEvent) {
        if (event.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) {
            manualNavigation = event.type == KeyEventType.KeyDown
        }
    }

    internal fun clearManualNavigation() { manualNavigation = false }

    fun requestFocus(key: String) {
        saveTarget(key)
        requestVersion++
    }

    internal fun requestInitialFocus(key: String) {
        if (savedKey.value == null) requestFocus(key)
    }

    private fun saveTarget(key: String) {
        savedKey.value = key
        savedIndex.value = groups.values.firstNotNullOfOrNull { group ->
            group.keys.indexOf(key).takeIf { it >= 0 }
        } ?: 0
    }

    internal fun onFocused(key: String) {
        val requested = requestedKey
        val group = groups.entries.firstOrNull { requested.startsWith(it.key) }?.value
        // 初次进入和条目移除时, 系统自动选中的控件不能覆盖待恢复的业务键与位置.
        if (!manualNavigation && key != requested && (!hasRestored || (group != null && requested !in group.keys))) return
        saveTarget(key)
        hasRestored = true
    }

    internal suspend fun restore() {
        // 子容器的 SideEffect 在本帧注册业务键, 静态控件在布局后注册请求器.
        withFrameNanos { }
        val requested = requestedKey
        val groupEntry = groups.entries.firstOrNull { requested.startsWith(it.key) }
        if (groupEntry != null && !groupEntry.value.ready) return
        val target = if (groupEntry != null) {
            tvFocusReplacement(requested, groupEntry.value.keys, savedIndex.value, groupEntry.value.fallbackKey)
        } else requested
        run {
            val index = groupEntry?.value?.keys?.indexOf(target) ?: -1
            if (groupEntry != null && index >= 0) {
                val layout = layouts[groupEntry.key] ?: return
                layout.scrollToItem(index)
                snapshotFlow { layout.isItemVisible(target) }.first { it }
            }
            val requester = snapshotFlow { nodes[target] }.filterNotNull().first()
            withFrameNanos { }
            // 等待布局期间发生的真实焦点移动拥有优先权.
            if (requestedKey != requested && requestedKey != target) return
            if (nodes[target] === requester) {
                if (requester.requestFocus()) {
                    saveTarget(target)
                    hasRestored = true
                }
                else {
                    val fallback = groupEntry?.value?.fallbackKey ?: "page-back"
                    if (nodes[fallback]?.requestFocus() == true) saveTarget(fallback)
                }
            }
        }
    }
}

internal data class TvFocusGroupSpec(val keys: List<String>, val ready: Boolean, val fallbackKey: String)
internal class TvFocusGroupLayout(val scrollToItem: suspend (Int) -> Unit, val isItemVisible: (String) -> Boolean)

@Composable
fun rememberTvFocusState(initialKey: String = "page-back"): TvFocusState {
    val saved = rememberSaveable { mutableStateOf<String?>(null) }
    val savedIndex = rememberSaveable { mutableStateOf(0) }
    return remember { TvFocusState(saved, savedIndex, initialKey) }
}

internal val LocalTvFocusState = staticCompositionLocalOf<TvFocusState> { error("TV focus requires TvPage") }

@Composable
internal fun TvRestorePageFocus(state: TvFocusState) {
    val groups = state.groups.toMap()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(state, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) state.clearManualNavigation()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            state.clearManualNavigation()
        }
    }
    LaunchedEffect(state, groups, state.requestVersion, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { state.restore() }
    }
}

@Composable
internal fun TvLazyFocusGroup(
    state: TvFocusState,
    groupKey: String,
    itemKeys: List<String>,
    ready: Boolean,
    fallbackKey: String,
    scrollToItem: suspend (Int) -> Unit,
    isItemVisible: (String) -> Boolean,
) {
    SideEffect {
        state.layouts[groupKey] = TvFocusGroupLayout(scrollToItem, isItemVisible)
        state.groups[groupKey] = TvFocusGroupSpec(itemKeys, ready, fallbackKey)
    }
    DisposableEffect(state, groupKey) {
        onDispose {
            state.layouts.remove(groupKey)
            state.groups.remove(groupKey)
        }
    }
}

internal fun Modifier.tvFocusTarget(key: String, state: TvFocusState): Modifier = composed {
    val requester = remember { FocusRequester() }
    DisposableEffect(key, state, requester) {
        onDispose { if (state.nodes[key] === requester) state.nodes.remove(key) }
    }
    this.focusRequester(requester)
        .onGloballyPositioned { state.nodes[key] = requester }
        .onFocusChanged { if (it.isFocused) state.onFocused(key) }
}

@Composable
internal fun TvStaticFocusGroup(
    state: TvFocusState,
    groupKey: String,
    keys: List<String>,
    ready: Boolean = true,
    fallbackKey: String = "page-back",
) {
    TvLazyFocusGroup(state, groupKey, keys, ready, fallbackKey, {}, { it in state.nodes })
}
