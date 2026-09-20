/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.danmaku.PlayerDanmakuHost
import me.him188.ani.app.ui.foundation.effects.ScreenOnEffect
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.subject.episode.EpisodePageState
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.app.ui.subject.episode.statistics.VideoStatistics
import me.him188.ani.app.videoplayer.media.TvMediaSessionBridge
import me.him188.ani.app.videoplayer.ui.VideoPlayer
import me.him188.ani.app.videoplayer.ui.progress.audioName
import me.him188.ani.app.videoplayer.ui.progress.subtitleLanguage
import me.him188.ani.datasources.api.Media
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.audioTracks
import org.openani.mediamp.features.subtitleTracks
import androidx.compose.ui.platform.LocalContext as AndroidLocalContext

@Composable
fun TvPlayerScreen(subjectId: Int, initialEpisodeId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel(key = "tv-player-$subjectId-$initialEpisodeId") {
        EpisodeViewModel(subjectId, initialEpisodeId, initialIsFullscreen = true, context = context)
    }
    vm.mediaResolver.ComposeContent()
    // 常驻订阅维持资源查询、自动选择与播放会话.
    val page by vm.pageState.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val properties by vm.player.mediaProperties.collectAsStateWithLifecycle()
    val position by vm.player.currentPositionMillis.collectAsStateWithLifecycle()
    val statistics by vm.videoStatisticsFlow.collectAsStateWithLifecycle(VideoStatistics.Placeholder)
    val selector = page?.mediaSelectorState
    val selection = selector?.presentationFlow?.collectAsStateWithLifecycle()?.value
    val speedFeature = vm.player.features[PlaybackSpeed]
    val speed = speedFeature?.valueFlow?.collectAsStateWithLifecycle(speedFeature.value)?.value ?: 1f
    val selectedMedia = selection?.selected?.mediaId
    var input by remember(vm) { mutableStateOf(TvPlayerInputState()) }
    var moreOption by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val status = tvPlayerStatus(
        loadingState = statistics.videoLoadingState,
        mediaStatus = playback.mediaStatus,
        isBuffering = playback.isBuffering,
        isPlaying = playback.isPlaying,
        playWhenReady = playback.playWhenReady,
        sourceLoading = statistics.mediaSourceLoading || selection?.isPlaceholder != false,
        hasSelectedMedia = selectedMedia != null,
        pageLoading = page == null || page?.isLoading == true || statistics.isPlaceholder,
        pageError = page?.loadError,
    )
    fun back() {
        val result = tvPlayerBack(input)
        input = result.state
        if (result.exitPlayer) onBack()
    }
    fun retry() {
        val loadError = page?.loadError
        if (loadError != null) vm.retryLoad(loadError)
        else scope.launch {
            vm.switchEpisode(vm.episodeSelectorState.current?.episodeId ?: initialEpisodeId)
        }
        input = input.copy(overlay = TvPlayerOverlay.Controls, previewMillis = null)
    }
    LaunchedEffect(vm) { vm.onUIReady(); vm.collectDanmakuConfig() }
    LaunchedEffect(selectedMedia, page?.episodePresentation?.episodeId) { input = input.mediaChanged() }
    TvPlaybackLifecycle(vm, page) { input = input.copy(overlay = TvPlayerOverlay.Controls, previewMillis = null) }
    if (playback.playWhenReady) ScreenOnEffect()
    TvPlayerChrome(
        state = TvPlayerUiState(
            title = page?.subjectPresentation?.title.orEmpty(),
            episodeTitle = page?.episodePresentation?.title.orEmpty(),
            positionMillis = position,
            durationMillis = properties?.durationMillis ?: 0L,
            playWhenReady = playback.playWhenReady,
            isPlaying = playback.isPlaying,
            status = status,
            sourceName = selection?.selectedWebSource?.let { source ->
                listOfNotNull(source.name, selection.selectedWebSourceChannel?.name).joinToString(" · ")
            } ?: selection?.selected?.originalTitle.orEmpty(),
            hasNextEpisode = vm.episodeSelectorState.hasNextEpisode,
            mediaKey = "${page?.episodePresentation?.episodeId}:$selectedMedia",
        ),
        input = input,
        onInputChange = { input = it },
        onTogglePlayback = {
            when {
                vm.player.state.value.mediaStatus == MediaStatus.Ended -> {
                    vm.player.seekTo(0)
                    vm.player.play()
                }
                vm.player.state.value.playWhenReady -> vm.player.pause()
                else -> vm.player.play()
            }
        },
        onSeek = vm.player::seekTo,
        onNextEpisode = { if (vm.episodeSelectorState.hasNextEpisode) vm.episodeSelectorState.selectNext() },
        onRetry = ::retry,
        onBack = onBack,
    ) {
        VideoPlayer(vm.player, Modifier.fillMaxSize())
        if (page?.danmakuEnabled == true) {
            PlayerDanmakuHost(vm.player, vm.danmakuHostState, vm.uiDanmakuEventFlow, Modifier.fillMaxSize())
        }
    }
    // 各面板拥有独立的滚动和焦点作用域.
    key(input.overlay) {
        when (input.overlay) {
            TvPlayerOverlay.Sources -> TvSourceMenu(vm, selector, selection, statistics.mediaSourceLoading, ::back) {
                input = input.copy(overlay = TvPlayerOverlay.Hidden)
            }
            TvPlayerOverlay.Episodes -> TvPlayerOptionPanel(
                title = "选集",
                options = vm.episodeSelectorState.items.map { episode ->
                    TvPlayerOption(
                        id = episode.episodeId.toString(),
                        title = "${episode.ep.ifBlank { episode.sort }} · ${episode.title}",
                        detail = if (episode.isKnownBroadcast) "" else "尚未播出",
                        selected = episode.episodeId == vm.episodeSelectorState.current?.episodeId,
                        enabled = episode.isKnownBroadcast,
                    )
                },
                message = if (vm.episodeSelectorState.items.isEmpty()) "正在加载剧集…" else null,
                onBack = ::back,
                onSelect = { id ->
                    vm.episodeSelectorState.items.firstOrNull { it.episodeId.toString() == id }?.let {
                        vm.episodeSelectorState.select(it)
                        input = input.copy(overlay = TvPlayerOverlay.Hidden)
                    }
                },
            )
            TvPlayerOverlay.Options -> TvPlayerOptionPanel(
                title = "播放设置",
                options = listOf(
                    TvPlayerOption("subtitles", "字幕"),
                    TvPlayerOption("audio", "音轨"),
                    TvPlayerOption("speed", "播放速度", "当前 ${speed}×", enabled = speedFeature != null),
                    TvPlayerOption("danmaku", if (page?.danmakuEnabled == true) "关闭弹幕" else "开启弹幕"),
                    TvPlayerOption("retry", "重新加载本集", "重新打开当前剧集的播放会话"),
                    TvPlayerOption("exit", "退出播放"),
                ),
                initialOptionId = moreOption,
                onBack = ::back,
                onSelect = { id ->
                    moreOption = id
                    when (id) {
                        "subtitles" -> input = input.copy(overlay = TvPlayerOverlay.Subtitles)
                        "audio" -> input = input.copy(overlay = TvPlayerOverlay.Audio)
                        "speed" -> input = input.copy(overlay = TvPlayerOverlay.Speed)
                        "danmaku" -> vm.setDanmakuEnabled(page?.danmakuEnabled != true)
                        "retry" -> retry()
                        "exit" -> onBack()
                    }
                },
            )
            TvPlayerOverlay.Subtitles -> TvSubtitleMenu(vm, ::back)
            TvPlayerOverlay.Audio -> TvAudioMenu(vm, ::back)
            TvPlayerOverlay.Speed -> TvPlayerOptionPanel(
                title = "播放速度",
                options = (listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f) + speed).distinct().sorted().map { value ->
                    TvPlayerOption(value.toString(), "${value}×", selected = value == speed, enabled = value in vm.playbackSpeedRange)
                },
                onBack = ::back,
                onSelect = { vm.setPlaybackSpeed(it.toFloat()); back() },
            )
            else -> Unit
        }
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
private fun TvSourceMenu(
    vm: EpisodeViewModel,
    selector: MediaSelectorState?,
    state: MediaSelectorState.Presentation?,
    isQuerying: Boolean,
    onBack: () -> Unit,
    onSelected: () -> Unit,
) {
    val mediaById = mutableMapOf<String, Media>()
    val options = buildList {
        add(TvPlayerOption("refresh", "重新查询全部资源"))
        state?.webSources?.forEach { source ->
            val sourceStatus = when {
                source.isResolvingCaptcha -> "正在处理网页验证"
                source.isCaptchaRequired -> "需要网页验证，可选择其他来源"
                source.isRateLimited -> "请求频繁，正在等待重试"
                source.isLoading -> "正在查询"
                source.isError -> "查询失败"
                source.channels.none { it.original != null } -> "未找到本集资源"
                else -> "${source.channels.count { it.original != null }} 条可用线路"
            }
            source.channels.forEachIndexed { index, channel ->
                val media = channel.original
                val id = "channel:${source.instanceId}:$index:${media?.mediaId.orEmpty()}"
                if (media != null) mediaById[id] = media
                add(
                    TvPlayerOption(
                        id = id,
                        title = "${source.name} · ${channel.name}",
                        detail = media?.let { listOf(it.properties.resolution, sourceStatus).filter(String::isNotBlank).joinToString(" · ") }
                            ?: "此线路暂无本集资源",
                        selected = media != null && media.mediaId == state.selected?.mediaId,
                        enabled = media != null,
                    ),
                )
            }
            add(TvPlayerOption("source:${source.instanceId}", "重查 ${source.name}", sourceStatus))
        }
        state?.filteredCandidates?.mapNotNull { it.result }?.distinctBy { it.mediaId }
            ?.filter { candidate -> mediaById.values.none { it.mediaId == candidate.mediaId } }
            ?.forEach { media ->
                val id = "media:${media.mediaId}"
                mediaById[id] = media
                add(TvPlayerOption(id, media.originalTitle, media.properties.resolution, selected = media.mediaId == state.selected?.mediaId))
            }
    }
    TvPlayerOptionPanel(
        title = "播放资源", options = options,
        message = tvPlayerSourceMenuMessage(
            isPlaceholder = state == null || state.isPlaceholder,
            isQuerying = isQuerying || state?.webSources?.any { it.isLoading } == true,
            hasAvailableMedia = mediaById.isNotEmpty(),
        ),
        onBack = onBack,
        onSelect = { id ->
            when {
                id == "refresh" -> vm.refreshFetch()
                id.startsWith("source:") -> vm.restartSource(id.removePrefix("source:"))
                else -> mediaById[id]?.let { media -> selector?.select(media); onSelected() }
            }
        },
    )
}

@Composable
private fun TvSubtitleMenu(vm: EpisodeViewModel, onBack: () -> Unit) {
    val tracks = vm.player.subtitleTracks
    val candidates = tracks?.candidates?.collectAsStateWithLifecycle(emptyList())?.value.orEmpty()
    val selected = tracks?.selected?.collectAsStateWithLifecycle()?.value
    TvPlayerOptionPanel(
        title = "字幕",
        initialOptionId = selected?.id?.toString() ?: "off".takeIf { tracks != null },
        options = listOf(TvPlayerOption("off", "关闭字幕", selected = selected == null, enabled = tracks != null)) + candidates.map { track ->
            TvPlayerOption(track.id.toString(), track.subtitleLanguage, selected = selected?.id == track.id)
        },
        message = if (candidates.isEmpty()) "此资源暂无可选字幕轨道。" else null,
        onBack = onBack,
        onSelect = { id -> tracks?.select(candidates.firstOrNull { it.id.toString() == id }); onBack() },
    )
}

@Composable
private fun TvAudioMenu(vm: EpisodeViewModel, onBack: () -> Unit) {
    val tracks = vm.player.audioTracks
    val candidates = tracks?.candidates?.collectAsStateWithLifecycle(emptyList())?.value.orEmpty()
    val selected = tracks?.selected?.collectAsStateWithLifecycle()?.value
    TvPlayerOptionPanel(
        title = "音轨",
        initialOptionId = selected?.id?.toString(),
        options = candidates.map { track -> TvPlayerOption(track.id.toString(), track.audioName, selected = selected?.id == track.id) },
        message = if (candidates.isEmpty()) "此资源暂无可选音轨。" else null,
        onBack = onBack,
        onSelect = { id -> candidates.firstOrNull { it.id.toString() == id }?.let { tracks?.select(it) }; onBack() },
    )
}

internal fun tvTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "${seconds / 3600}:${(seconds / 60 % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}
