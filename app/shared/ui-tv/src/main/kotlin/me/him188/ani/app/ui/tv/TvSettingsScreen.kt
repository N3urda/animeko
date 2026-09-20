/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.him188.ani.app.data.repository.media.MediaSourceInstanceRepository
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionUpdater
import me.him188.ani.app.platform.DeviceUiMode
import me.him188.ani.utils.platform.Uuid
import org.koin.mp.KoinPlatform
import java.net.URI

@Composable
fun TvSettingsScreen(
    deviceUiMode: DeviceUiMode,
    onDeviceUiMode: (DeviceUiMode) -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val koin = remember { KoinPlatform.getKoin() }
    val settings = remember { koin.get<SettingsRepository>() }
    val users = remember { koin.get<UserRepository>() }
    val subscriptions = remember { koin.get<MediaSourceSubscriptionRepository>() }
    val sources = remember { koin.get<MediaSourceInstanceRepository>() }
    val sourceManager = remember { koin.get<MediaSourceManager>() }
    val namedSources by sourceManager.allInstances.collectAsStateWithLifecycle(emptyList())
    val updater = remember { koin.get<MediaSourceSubscriptionUpdater>() }
    val downloads = remember { koin.get<MediaDownloadManager>() }
    val video by settings.videoScaffoldConfig.flow.collectAsStateWithLifecycle(null)
    val danmaku by settings.danmakuEnabled.flow.collectAsStateWithLifecycle(null)
    val subscriptionList by subscriptions.flow.collectAsStateWithLifecycle(null)
    val sourceList by sources.flow.collectAsStateWithLifecycle(null)
    val downloadList by downloads.downloads.collectAsStateWithLifecycle()
    val self by users.selfInfoFlow.collectAsStateWithLifecycle(null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf("播放") }
    val url = rememberTextFieldState()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    fun operate(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        status = null
        scope.launch {
            try {
                block()
                if (status == null) status = "设置已保存"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status = "操作未完成，请稍后重试。"
            } finally {
                busy = false
            }
        }
    }
    val entries = buildList {
        when (category) {
            "播放" -> {
                val config = video
                if (config == null || danmaku == null) add(TvSettingsItem("loading", "正在读取播放设置…"))
                else {
                    add(TvSettingsItem("danmaku", "弹幕", "播放时显示其他观众的弹幕", checked = danmaku, onClick = { operate { settings.danmakuEnabled.set(danmaku != true) } }))
                    add(TvSettingsItem("auto-next", "自动连播", "本集播放结束后继续下一集", checked = config.autoPlayNext, onClick = { operate { settings.videoScaffoldConfig.update { copy(autoPlayNext = !autoPlayNext) } } }))
                    add(TvSettingsItem("auto-skip", "自动跳过片头片尾", "有片头片尾信息时自动跳过", checked = config.autoSkipOpEd, onClick = { operate { settings.videoScaffoldConfig.update { copy(autoSkipOpEd = !autoSkipOpEd) } } }))
                    add(TvSettingsItem("auto-source", "播放失败自动换源", "当前资源无法播放时尝试其他资源", checked = config.autoSwitchMediaOnPlayerError, onClick = { operate { settings.videoScaffoldConfig.update { copy(autoSwitchMediaOnPlayerError = !autoSwitchMediaOnPlayerError) } } }))
                    add(TvSettingsItem("playback-help", "播放中的设置", "播放时按上 / 下方向键打开菜单，再选择「更多」调整字幕、音轨和倍速。"))
                }
            }
            "订阅" -> {
                add(TvSettingsItem("subscription-url", "订阅网址（http / https）", input = url))
                add(TvSettingsItem("subscription-add", "添加订阅", "导入网址中的数据源并检查更新", enabled = url.text.isNotBlank(), onClick = {
                    val normalized = validTvSubscriptionUrl(url.text.toString())
                    when {
                        normalized == null -> status = "请输入有效的 http 或 https 订阅网址。"
                        subscriptionList.orEmpty().any { it.url == normalized } -> status = "此订阅已存在。"
                        else -> operate {
                            subscriptions.add(MediaSourceSubscription(Uuid.randomString(), normalized))
                            url.setTextAndPlaceCursorAtEnd("")
                            updater.updateAllOutdated(force = true)
                            status = "订阅检查完成，请查看下方更新结果。"
                        }
                    }
                }))
                add(TvSettingsItem("subscription-update", "更新全部订阅", "从订阅地址获取最新数据源", enabled = !subscriptionList.isNullOrEmpty(), onClick = { operate { updater.updateAllOutdated(force = true); status = "订阅检查完成，请查看下方更新结果。" } }))
                if (subscriptionList == null) add(TvSettingsItem("loading", "正在读取订阅…"))
                else if (subscriptionList.isNullOrEmpty()) add(TvSettingsItem("empty", "暂无订阅", "输入订阅网址，再选择「添加订阅」。"))
                subscriptionList.orEmpty().forEach { subscription ->
                    val result = subscription.lastUpdated?.let {
                        if (it.error != null || it.mediaSourceCount == null) "更新失败，可选择「更新全部订阅」重试"
                        else "已更新 ${it.mediaSourceCount} 个数据源"
                    } ?: "等待更新"
                    add(TvSettingsItem("subscription-${subscription.subscriptionId}", subscription.url, result, value = "移除", onClick = {
                        confirmation = "移除订阅后，已经导入的数据源会保留。可在「数据源」中停用。" to { subscriptions.remove(subscription); status = "订阅已移除" }
                    }))
                }
            }
            "数据源" -> {
                if (sourceList == null) add(TvSettingsItem("loading", "正在读取数据源…"))
                else if (sourceList.isNullOrEmpty()) add(TvSettingsItem("empty", "暂无数据源", "请先在「订阅」中添加并更新订阅。"))
                sourceList.orEmpty().forEach { source ->
                    val sourceName = namedSources.firstOrNull { it.instanceId == source.instanceId }?.source?.info?.displayName
                        ?: ((source.config.serializedArguments as? JsonObject)?.get("name") as? JsonPrimitive)?.content
                        ?: "名称暂不可用"
                    val subscription = subscriptionList.orEmpty().firstOrNull { it.subscriptionId == source.config.subscriptionId }
                    add(TvSettingsItem("source-${source.instanceId}", sourceName, subscription?.url ?: "本地数据源", checked = source.isEnabled, onClick = {
                        operate { sources.updateSave(source.instanceId) { copy(isEnabled = !isEnabled) } }
                    }))
                }
            }
            "存储" -> {
                val availableMb = remember(downloadList, busy) { StatFs(context.filesDir.path).availableBytes / 1024 / 1024 }
                add(TvSettingsItem("space", "可用空间 $availableMb MB", "本地视频缓存 ${downloadList.size} 项。删除缓存后再次播放需要重新下载。"))
                if (downloadList.isEmpty()) add(TvSettingsItem("empty", "暂无视频缓存", "BT 播放和离线下载使用本地存储空间。"))
                downloadList.forEach { download ->
                    val subject = download.metadata.subjectNameCN?.takeIf { it.isNotBlank() }
                        ?: download.metadata.subjectNames.firstOrNull().orEmpty().ifBlank { "未命名番剧" }
                    add(TvSettingsItem("download-${download.id}", "$subject · ${download.metadata.episodeSort}", "删除本地视频缓存，保留观看记录", value = "删除", onClick = {
                        confirmation = "删除「$subject · ${download.metadata.episodeSort}」的本地视频缓存？观看记录会保留。" to { check(downloads.delete(download)); status = "视频缓存已删除" }
                    }))
                }
            }
            "账号" -> {
                add(TvSettingsItem("account-info", self?.let { "已登录：${it.nickname.ifBlank { it.email.orEmpty().ifBlank { "Animeko 账号" } }}" } ?: "尚未登录", "登录后可同步收藏和观看记录。"))
                add(TvSettingsItem("account-login", if (self == null) "邮箱登录" else "绑定 / 更换邮箱", "通过邮箱验证码验证账号", onClick = onLogin))
                if (self != null) add(TvSettingsItem("account-logout", "退出登录", "本机的播放缓存会保留", onClick = { confirmation = "退出当前账号？本机的播放缓存会保留。" to { users.clearSelfInfo(); status = "已退出登录" } }))
            }
            "界面" -> {
                add(TvSettingsItem("mode-info", "当前界面模式：${deviceUiMode.tvDisplayName}", "切换模式后返回首页。"))
                add(TvSettingsItem("mode-tv", "电视界面", "使用遥控器方向键和确认键操作", value = if (deviceUiMode == DeviceUiMode.Television) "当前" else "", onClick = { onDeviceUiMode(DeviceUiMode.Television) }))
                add(TvSettingsItem("mode-auto", "自动识别设备", "电视使用遥控器界面，其他设备使用标准界面", value = if (deviceUiMode == DeviceUiMode.Auto) "当前" else "", onClick = { onDeviceUiMode(DeviceUiMode.Auto) }))
                add(TvSettingsItem("mode-standard", "手机 / 平板界面", "需要触控或鼠标操作", value = if (deviceUiMode == DeviceUiMode.Standard) "当前" else "", onClick = { confirmation = "手机 / 平板界面需要触控或鼠标操作。确认切换？" to { onDeviceUiMode(DeviceUiMode.Standard) } }))
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                add(TvSettingsItem("version", "Animeko TV · ${info.versionName}", "Android 8.1 及以上"))
            }
        }
    }
    TvSettingsLayout(category, { category = it; status = null }, entries, onBack, busy, status)
    confirmation?.let { (message, action) ->
        TvConfirmDialog("确认操作", message, onConfirm = { confirmation = null; operate(action) }, onDismiss = { confirmation = null })
    }
}

internal data class TvSettingsItem(
    val key: String,
    val title: String,
    val description: String = "",
    val value: String = "",
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val input: TextFieldState? = null,
    val onClick: (() -> Unit)? = null,
)

/** 分类与内容各自保存位置; 左键返回当前分类, 右键恢复该分类的最近选项. */
@Composable
internal fun TvSettingsLayout(
    category: String,
    onCategorySelected: (String) -> Unit,
    items: List<TvSettingsItem>,
    onBack: () -> Unit,
    busy: Boolean = false,
    status: String? = null,
) {
    val categories = listOf("播放", "订阅", "数据源", "存储", "账号", "界面")
    val focus = rememberTvFocusState("settings-category:$category")
    var lastContent by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    val holder = rememberSaveableStateHolder()
    val groupKey = "settings-content:$category:"
    val keys = items.filter { it.enabled && (it.onClick != null || it.input != null) }.map { groupKey + it.key }
    fun enterContent() {
        val target = lastContent[category]?.takeIf { it in keys } ?: keys.firstOrNull() ?: return
        focus.requestFocus(target)
    }
    TvPage("设置", onBack, focusState = focus) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.width(164.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { title ->
                    TvButton(
                        title, ::enterContent,
                        Modifier.fillMaxWidth().tvFocusTarget("settings-category:$title", focus)
                            .onFocusChanged { if (it.isFocused && title != category) onCategorySelected(title) }
                            .onPreviewKeyEvent {
                                if (it.key == Key.DirectionRight) {
                                    if (it.type == KeyEventType.KeyDown) enterContent()
                                    true
                                } else false
                            }.semantics { selected = title == category }.testTag("tv-settings-$title"),
                        selected = title == category,
                    )
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(category, fontSize = 22.sp)
                Text(
                    if (busy) "正在处理，请稍候…" else status ?: "上下选择  ·  确认修改  ·  左键返回分类",
                    Modifier.testTag("tv-settings-status"),
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                holder.SaveableStateProvider(category) {
                    key(category) {
                        val list = rememberLazyListState()
                        TvLazyFocusGroup(
                            focus, groupKey, keys, true, "settings-category:$category",
                            scrollToItem = { index -> list.scrollToItem(items.indexOfFirst { groupKey + it.key == keys[index] }.coerceAtLeast(0)) },
                            isItemVisible = { target -> list.layoutInfo.visibleItemsInfo.any { it.key == target } },
                        )
                        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(items, key = { groupKey + it.key }) { item ->
                                val target = groupKey + item.key
                                val modifier = Modifier.fillMaxWidth().tvFocusTarget(target, focus)
                                    .onFocusChanged { if (it.isFocused) lastContent = lastContent + (category to target) }
                                    .testTag("tv-settings-row-${item.key}")
                                when {
                                    item.input != null -> TvOutlinedTextField(
                                        item.input, modifier.testTag("tv-subscription-url"), enabled = !busy,
                                        label = { Text(item.title) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                        onNavigateLeft = { focus.requestFocus("settings-category:$category") },
                                    )
                                    item.onClick != null -> TvSettingsRow(
                                        item,
                                        modifier.onPreviewKeyEvent {
                                            if (it.key == Key.DirectionLeft) {
                                                if (it.type == KeyEventType.KeyDown) focus.requestFocus("settings-category:$category")
                                                true
                                            } else false
                                        },
                                        onClick = { if (!busy) item.onClick.invoke() },
                                    )
                                    else -> Column(
                                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(item.title, fontSize = 18.sp)
                                        if (item.description.isNotEmpty()) Text(item.description, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSettingsRow(item: TvSettingsItem, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick, modifier.heightIn(min = 78.dp).focusProperties { canFocus = item.enabled }.semantics {
            if (item.checked != null) {
                role = Role.Switch
                stateDescription = if (item.checked) "开启" else "关闭"
            }
        },
        enabled = item.enabled,
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1.01f, pressedScale = 1f),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.description.isNotEmpty()) Text(item.description, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val value = item.checked?.let { if (it) "开启" else "关闭" } ?: item.value
            if (value.isNotEmpty()) Text(value, fontSize = 16.sp, maxLines = 1)
        }
    }
}

internal val DeviceUiMode.tvDisplayName: String
    get() = when (this) {
        DeviceUiMode.Auto -> "自动识别设备"
        DeviceUiMode.Television -> "电视界面"
        DeviceUiMode.Standard -> "手机 / 平板界面"
    }

internal fun validTvSubscriptionUrl(text: String): String? {
    val value = text.trim()
    val uri = try { URI(value) } catch (_: Exception) { return null }
    return value.takeIf { uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null }
}
