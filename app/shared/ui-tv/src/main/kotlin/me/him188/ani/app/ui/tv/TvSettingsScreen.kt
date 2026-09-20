/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
    val subscriptionList by subscriptions.flow.collectAsStateWithLifecycle(emptyList())
    val sourceList by sources.flow.collectAsStateWithLifecycle(emptyList())
    val downloadList by downloads.downloads.collectAsStateWithLifecycle()
    val self by users.selfInfoFlow.collectAsStateWithLifecycle(null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("播放") }
    val url = rememberTextFieldState()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    fun operate(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        status = null
        scope.launch {
            try { block(); if (status == null) status = "操作完成" }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { status = "操作失败，请检查网络后重试。" }
            finally { busy = false }
        }
    }
    TvPage("设置", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("播放", "订阅", "数据源", "存储", "账号", "界面").forEach { title ->
                TvButton(if (tab == title) "● $title" else title, { tab = title; status = null }, Modifier.testTag("tv-settings-$title"))
            }
        }
        if (busy) Text("正在处理…")
        status?.let { Text(it) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (tab) {
                "播放" -> {
                    val config = video
                    if (config == null || danmaku == null) item { Text("正在读取设置…") }
                    else {
                        item { TvButton("弹幕：${if (danmaku == true) "开启" else "关闭"}", { operate { settings.danmakuEnabled.set(danmaku != true) } }, enabled = !busy) }
                        item { TvButton("自动连播：${if (config.autoPlayNext) "开启" else "关闭"}", { operate { settings.videoScaffoldConfig.update { copy(autoPlayNext = !autoPlayNext) } } }, enabled = !busy) }
                        item { TvButton("自动跳过片头片尾：${if (config.autoSkipOpEd) "开启" else "关闭"}", { operate { settings.videoScaffoldConfig.update { copy(autoSkipOpEd = !autoSkipOpEd) } } }, enabled = !busy) }
                        item { TvButton("播放错误自动换源：${if (config.autoSwitchMediaOnPlayerError) "开启" else "关闭"}", { operate { settings.videoScaffoldConfig.update { copy(autoSwitchMediaOnPlayerError = !autoSwitchMediaOnPlayerError) } } }, enabled = !busy) }
                        item { Text("字幕、音轨和倍速可以在播放时按确认键打开控制菜单选择。") }
                    }
                }
                "订阅" -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvOutlinedTextField(url, Modifier.fillMaxWidth().testTag("tv-subscription-url"), label = { Text("订阅网址（http / https）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                TvButton("添加订阅", {
                                    val normalized = validTvSubscriptionUrl(url.text.toString())
                                    if (normalized == null) status = "请输入有效的 http 或 https 订阅网址。"
                                    else if (subscriptionList.any { it.url == normalized }) status = "此订阅已存在。"
                                    else operate {
                                        subscriptions.add(MediaSourceSubscription(Uuid.randomString(), normalized))
                                        url.setTextAndPlaceCursorAtEnd("")
                                        updater.updateAllOutdated(force = true); status = "订阅检查完成，请查看各项更新结果。"
                                    }
                                }, enabled = !busy && url.text.isNotBlank())
                                TvButton("更新全部订阅", { operate { updater.updateAllOutdated(force = true); status = "订阅检查完成，请查看各项更新结果。" } }, enabled = !busy)
                            }
                        }
                    }
                    if (subscriptionList.isEmpty()) item { Text("暂无订阅，输入网址后选择添加。") }
                    items(subscriptionList, key = { it.subscriptionId }) { subscription ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(subscription.url)
                            Text(subscription.lastUpdated?.let { if (it.error != null || it.mediaSourceCount == null) "更新失败，可重试更新" else "已更新 ${it.mediaSourceCount} 个数据源" } ?: "等待更新")
                            TvButton("移除此订阅", {
                                confirmation = "移除订阅后，已经导入的数据源会保留。可在「数据源」中停用。" to { subscriptions.remove(subscription) }
                            }, enabled = !busy)
                        }
                    }
                }
                "数据源" -> {
                    item { Text("选择数据源可切换启用状态。名称后的标识用于区分不同订阅中的同类来源。") }
                    if (sourceList.isEmpty()) item { Text("暂无数据源，请先添加并更新订阅。") }
                    items(sourceList, key = { it.instanceId }) { source ->
                        TvButton("${if (source.isEnabled) "✓" else "○"} ${namedSources.firstOrNull { it.instanceId == source.instanceId }?.source?.info?.displayName ?: "正在读取名称"} · ${source.instanceId.takeLast(6)}", {
                            operate { sources.updateSave(source.instanceId) { copy(isEnabled = !isEnabled) } }
                        }, enabled = !busy)
                    }
                }
                "存储" -> {
                    item { Text("可用空间：${StatFs(context.filesDir.path).availableBytes / 1024 / 1024} MB · 缓存 ${downloadList.size} 项") }
                    item { Text("BT 播放会占用本地空间；空间不足时可删除已有缓存。删除后再次播放需要重新下载。") }
                    if (downloadList.isEmpty()) item { Text("暂无视频缓存") }
                    items(downloadList, key = { it.id }) { download ->
                        TvButton("删除 ${download.metadata.subjectNameCN ?: download.metadata.subjectNames.firstOrNull().orEmpty()} · ${download.metadata.episodeSort}", {
                            confirmation = "删除此剧集的本地视频缓存？观看记录会保留。" to { check(downloads.delete(download)) }
                        }, enabled = !busy)
                    }
                }
                "账号" -> {
                    item { Text(self?.let { "已登录：${it.nickname.ifBlank { it.email.orEmpty() }}" } ?: "尚未登录，登录后可同步收藏和观看记录。") }
                    item { TvButton(if (self == null) "邮箱登录" else "绑定 / 更换邮箱", onLogin, enabled = !busy) }
                    if (self != null) item { TvButton("退出登录", { confirmation = "退出当前账号？本机的播放缓存会保留。" to { users.clearSelfInfo() } }, enabled = !busy) }
                }
                "界面" -> {
                    item { Text("界面模式：${deviceUiMode.name}。切换后返回首页。") }
                    item { TvButton("使用电视界面", { onDeviceUiMode(DeviceUiMode.Television) }) }
                    item { TvButton("自动识别设备", { onDeviceUiMode(DeviceUiMode.Auto) }) }
                    item { TvButton("使用手机 / 平板界面", { confirmation = "手机界面需要触控或鼠标操作。确认切换？" to { onDeviceUiMode(DeviceUiMode.Standard) } }) }
                    item {
                        val info = context.packageManager.getPackageInfo(context.packageName, 0)
                        Text("Animeko TV 测试版 · ${info.versionName}\nAndroid 8.1 及以上 · 方向键移动，确认键选择，返回键返回")
                    }
                }
            }
        }
    }
    confirmation?.let { (message, action) ->
        TvConfirmDialog("确认操作", message, onConfirm = { confirmation = null; operate(action) }, onDismiss = { confirmation = null })
    }
}

internal fun validTvSubscriptionUrl(text: String): String? {
    val value = text.trim()
    val uri = try { URI(value) } catch (_: Exception) { return null }
    return value.takeIf { uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null }
}
