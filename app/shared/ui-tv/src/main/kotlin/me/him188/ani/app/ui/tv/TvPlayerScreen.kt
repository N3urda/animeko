/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext as AndroidLocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.danmaku.PlayerDanmakuHost
import me.him188.ani.app.ui.foundation.effects.ScreenOnEffect
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.subject.episode.EpisodePageState
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.app.ui.subject.episode.statistics.VideoStatistics
import me.him188.ani.app.videoplayer.media.TvMediaSessionBridge
import me.him188.ani.app.videoplayer.ui.VideoPlayer
import me.him188.ani.app.videoplayer.ui.progress.audioName
import me.him188.ani.app.videoplayer.ui.progress.subtitleLanguage
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.features.audioTracks
import org.openani.mediamp.features.subtitleTracks

@Composable
fun TvPlayerScreen(subjectId: Int, initialEpisodeId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel(key = "tv-player-$subjectId-$initialEpisodeId") {
        EpisodeViewModel(subjectId, initialEpisodeId, initialIsFullscreen = true, context = context)
    }
    vm.mediaResolver.ComposeContent()
    // 常驻订阅维持现有资源查询、自动选择与播放会话。
    val page by vm.pageState.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val properties by vm.player.mediaProperties.collectAsStateWithLifecycle()
    val position by vm.player.currentPositionMillis.collectAsStateWithLifecycle()
    val statistics by vm.videoStatisticsFlow.collectAsStateWithLifecycle(VideoStatistics.Placeholder)
    val duration = properties?.durationMillis ?: 0L
    var input by remember(vm) { mutableStateOf(TvPlayerInputState()) }
    var activity by remember { mutableLongStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val progressFocus = remember { FocusRequester() }
    val menuFocus = remember { TvPlayerOverlay.entries.associateWith { FocusRequester() } }
    var restoreFocus by remember { mutableStateOf(playFocus) }
    val scope = rememberCoroutineScope()
    val selectedMedia = page?.mediaSelectorState?.presentationFlow?.collectAsStateWithLifecycle()?.value?.selected?.mediaId
    val error = page?.loadError != null || statistics.videoLoadingState is VideoLoadingState.Failed || playback.mediaStatus is MediaStatus.Error
    fun back() {
        val result = tvPlayerBack(input)
        input = result.state
        if (result.exitPlayer) onBack()
    }
    fun menu(overlay: TvPlayerOverlay) {
        restoreFocus = if (overlay == TvPlayerOverlay.Controls) playFocus else menuFocus.getValue(overlay)
        input = input.copy(overlay = overlay, previewMillis = null)
    }
    BackHandler { back() }
    LaunchedEffect(vm) { vm.onUIReady(); vm.collectDanmakuConfig() }
    LaunchedEffect(selectedMedia, page?.episodePresentation?.episodeId) { input = input.mediaChanged() }
    LaunchedEffect(error) { if (error) menu(TvPlayerOverlay.Controls) }
    LaunchedEffect(input.overlay, input.previewMillis != null) {
        when {
            input.previewMillis != null || input.overlay == TvPlayerOverlay.Hidden -> rootFocus.requestFocus()
            input.overlay == TvPlayerOverlay.Controls -> restoreFocus.requestFocus()
        }
    }
    LaunchedEffect(input.overlay, input.previewMillis, activity, playback.isPlaying, error) {
        if (input.overlay == TvPlayerOverlay.Controls && input.previewMillis == null && playback.isPlaying && !error) {
            delay(5000)
            input = input.copy(overlay = TvPlayerOverlay.Hidden)
        }
    }
    TvPlaybackLifecycle(vm, page) { menu(TvPlayerOverlay.Controls) }
    if (playback.playWhenReady) ScreenOnEffect()
    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag("tv-player")
            .onPreviewKeyEvent { event ->
                activity++
                val key = when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> TvPlayerKey.Confirm
                    Key.DirectionLeft -> TvPlayerKey.Left
                    Key.DirectionRight -> TvPlayerKey.Right
                    Key.DirectionUp -> TvPlayerKey.Up
                    Key.DirectionDown -> TvPlayerKey.Down
                    else -> return@onPreviewKeyEvent false
                }
                val native = event.nativeKeyEvent
                val result = tvPlayerInput(input, key, event.type == KeyEventType.KeyDown, native.repeatCount, native.eventTime - native.downTime, position, duration)
                if (input.overlay == TvPlayerOverlay.Hidden && input.previewMillis == null) restoreFocus = playFocus
                input = result.state
                result.seekToMillis?.let(vm.player::seekTo)
                result.consumed
            }.focusRequester(rootFocus).focusable(),
    ) {
        VideoPlayer(vm.player, Modifier.fillMaxSize())
        if (page?.danmakuEnabled == true) PlayerDanmakuHost(vm.player, vm.danmakuHostState, vm.uiDanmakuEventFlow, Modifier.fillMaxSize())
        if (input.previewMillis != null) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .88f)).padding(40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("预览进度 ${tvTime(input.previewMillis ?: 0)} / ${tvTime(duration)}", fontSize = 28.sp)
                Text("左右调整 · 确认跳转 · 返回取消", fontSize = 20.sp)
            }
        } else if (input.overlay == TvPlayerOverlay.Controls) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .88f)).padding(horizontal = 40.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${page?.subjectPresentation?.title.orEmpty()} · ${page?.episodePresentation?.title.orEmpty()}", fontSize = 24.sp, maxLines = 1)
                Text("${tvTime(position)} / ${tvTime(duration)}", fontSize = 20.sp)
                if (error) Text("播放遇到问题，请重试或选择其他资源。", color = Color(0xFFFFB4AB))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton(if (playback.playWhenReady) "暂停" else "播放", { if (vm.player.state.value.playWhenReady) vm.player.pause() else vm.player.play() }, Modifier.focusRequester(playFocus).testTag("tv-play-pause"))
                    TvButton("进度", { restoreFocus = progressFocus; input = input.copy(previewMillis = position.coerceIn(0, duration), previewOrigin = TvPlayerOverlay.Controls) }, Modifier.focusRequester(progressFocus), enabled = duration > 0)
                    TvButton("选集", { menu(TvPlayerOverlay.Episodes) }, Modifier.focusRequester(menuFocus.getValue(TvPlayerOverlay.Episodes)))
                    TvButton("下一集", { if (vm.episodeSelectorState.hasNextEpisode) vm.episodeSelectorState.selectNext() }, enabled = vm.episodeSelectorState.hasNextEpisode)
                    TvButton("资源", { menu(TvPlayerOverlay.Sources) }, Modifier.focusRequester(menuFocus.getValue(TvPlayerOverlay.Sources)).testTag("tv-player-sources"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton("字幕", { menu(TvPlayerOverlay.Subtitles) }, Modifier.focusRequester(menuFocus.getValue(TvPlayerOverlay.Subtitles)))
                    TvButton("音轨", { menu(TvPlayerOverlay.Audio) }, Modifier.focusRequester(menuFocus.getValue(TvPlayerOverlay.Audio)))
                    TvButton("倍速", { menu(TvPlayerOverlay.Speed) }, Modifier.focusRequester(menuFocus.getValue(TvPlayerOverlay.Speed)))
                    TvButton(if (page?.danmakuEnabled == true) "关闭弹幕" else "开启弹幕", { vm.setDanmakuEnabled(page?.danmakuEnabled != true) })
                    TvButton("重试", {
                        val loadError = page?.loadError
                        if (loadError != null) vm.retryLoad(loadError)
                        else scope.launch {
                            vm.switchEpisode(vm.episodeSelectorState.current?.episodeId ?: initialEpisodeId)
                        }
                    })
                    TvButton("退出", onBack)
                }
                if (duration <= 0) Text("当前资源尚未提供可跳转的时长。")
            }
        } else if (!playback.isPlaying && input.overlay == TvPlayerOverlay.Hidden) {
            Text(
                if (error) "播放失败 · 按确认键重试或换源" else if (playback.mediaStatus == MediaStatus.Ended) "本集播放结束 · 按确认键选集" else if (playback.mediaStatus == MediaStatus.Ready && !playback.playWhenReady) "已暂停 · 按确认键打开控制菜单" else "正在准备播放 · 按确认键打开控制菜单",
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .75f)).padding(24.dp), fontSize = 22.sp,
            )
        }
    }
    when (input.overlay) {
        TvPlayerOverlay.Sources -> TvSourceMenu(vm, page, ::back) { input = input.copy(overlay = TvPlayerOverlay.Hidden) }
        TvPlayerOverlay.Episodes -> TvPlayerMenu("选集", ::back) {
            if (vm.episodeSelectorState.items.isEmpty()) item { Text("正在加载剧集…") }
            items(vm.episodeSelectorState.items, key = { it.episodeId }) { episode ->
                TvButton("${episode.ep.ifBlank { episode.sort }} · ${episode.title}${if (episode.episodeId == vm.episodeSelectorState.current?.episodeId) " · 正在播放" else ""}", {
                    vm.episodeSelectorState.select(episode)
                    input = input.copy(overlay = TvPlayerOverlay.Hidden)
                }, enabled = episode.isKnownBroadcast)
            }
        }
        TvPlayerOverlay.Subtitles -> TvSubtitleMenu(vm, ::back)
        TvPlayerOverlay.Audio -> TvAudioMenu(vm, ::back)
        TvPlayerOverlay.Speed -> TvPlayerMenu("播放速度", ::back) {
            items(listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)) { speed ->
                TvButton("${speed}x", { vm.setPlaybackSpeed(speed); back() }, enabled = speed in vm.playbackSpeedRange)
            }
        }
        else -> Unit
    }
}

@Composable
private fun TvPlaybackLifecycle(vm: EpisodeViewModel, page: EpisodePageState?, onReturnToForeground: () -> Unit) {
    val context = AndroidLocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onReturn by rememberUpdatedState(onReturnToForeground)
    var bridge by remember(vm) { mutableStateOf<TvMediaSessionBridge?>(null) }
    var hasStopped by remember(vm) { mutableStateOf(false) }
    LaunchedEffect(vm, lifecycle) {
        combine(vm.player.state, lifecycle.currentStateFlow) { state, lifecycleState ->
            state.playWhenReady && !lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        }.collect { shouldPause -> if (shouldPause) vm.player.pause() }
    }
    DisposableEffect(vm, lifecycle) {
        fun start() {
            if (bridge == null) bridge = TvMediaSessionBridge(context, vm.player) {
                if (vm.episodeSelectorState.hasNextEpisode) vm.episodeSelectorState.selectNext()
            }
            if (hasStopped) onReturn()
            hasStopped = false
        }
        fun stop() {
            hasStopped = true
            if (vm.player.state.value.playWhenReady) vm.player.pause()
            bridge?.close()
            bridge = null
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_START -> start(); Lifecycle.Event.ON_STOP -> stop(); else -> Unit }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    SideEffect {
        bridge?.updateMetadata(
            id = page?.episodePresentation?.episodeId?.toString().orEmpty(),
            title = page?.subjectPresentation?.title ?: "Animeko",
            subtitle = page?.episodePresentation?.title.orEmpty(),
            artworkUrl = page?.subjectPresentation?.info?.imageLarge,
            hasNext = vm.episodeSelectorState.hasNextEpisode,
        )
    }
}

@Composable
private fun TvPlayerMenu(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TvPage(title, onBack, Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
private fun TvSourceMenu(vm: EpisodeViewModel, page: EpisodePageState?, onBack: () -> Unit, onSelected: () -> Unit) {
    val selector = page?.mediaSelectorState
    val state = selector?.presentationFlow?.collectAsStateWithLifecycle()?.value
    TvPlayerMenu("选择播放资源", onBack) {
        item { TvButton("重新查询全部资源", vm::refreshFetch) }
        if (state == null || state.isPlaceholder) item { Text("正在加载资源…") }
        if (state != null && selector != null) {
            state.webSources.forEach { source ->
                item(key = "source-${source.instanceId}") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(source.name + when { source.isCaptchaRequired -> " · 需要网页验证，请尝试其他来源"; source.isLoading -> " · 查询中"; source.isError -> " · 查询失败"; else -> "" })
                        TvButton("重试 ${source.name}", { vm.restartSource(source.instanceId) })
                    }
                }
                items(source.channels, key = { "${source.instanceId}-${it.name}" }) { channel ->
                    TvButton("${source.name} · ${channel.name}", { channel.original?.let(selector::select); onSelected() }, enabled = channel.original != null)
                }
            }
            val candidates = state.filteredCandidates.mapNotNull { it.result }.distinctBy { it.mediaId }
                .filter { candidate -> state.webSources.none { source -> source.channels.any { it.original?.mediaId == candidate.mediaId } } }
            items(candidates, key = { "media-${it.mediaId}" }) { media ->
                TvButton(media.originalTitle, { selector.select(media); onSelected() })
            }
            if (candidates.isEmpty() && state.webSources.none { it.channels.any { channel -> channel.original != null } }) {
                item { Text("暂无可播放资源。可重试查询，或返回设置检查数据源订阅。") }
            }
        }
    }
}

@Composable
private fun TvSubtitleMenu(vm: EpisodeViewModel, onBack: () -> Unit) {
    val tracks = vm.player.subtitleTracks
    val candidates = tracks?.candidates?.collectAsStateWithLifecycle(emptyList())?.value.orEmpty()
    val selected = tracks?.selected?.collectAsStateWithLifecycle()?.value
    TvPlayerMenu("字幕", onBack) {
        item { TvButton("关闭字幕", { tracks?.select(null); onBack() }, enabled = tracks != null) }
        if (candidates.isEmpty()) item { Text("此资源暂无可选字幕轨道。") }
        items(candidates, key = { it.id }) { track ->
            TvButton("${if (selected?.id == track.id) "✓ " else ""}${track.subtitleLanguage}", { tracks?.select(track); onBack() })
        }
    }
}

@Composable
private fun TvAudioMenu(vm: EpisodeViewModel, onBack: () -> Unit) {
    val tracks = vm.player.audioTracks
    val candidates = tracks?.candidates?.collectAsStateWithLifecycle(emptyList())?.value.orEmpty()
    val selected = tracks?.selected?.collectAsStateWithLifecycle()?.value
    TvPlayerMenu("音轨", onBack) {
        if (candidates.isEmpty()) item { Text("此资源暂无可选音轨。") }
        items(candidates, key = { it.id }) { track ->
            TvButton("${if (selected?.id == track.id) "✓ " else ""}${track.audioName}", { tracks?.select(track); onBack() })
        }
    }
}

internal fun tvTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "${seconds / 3600}:${(seconds / 60 % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}
