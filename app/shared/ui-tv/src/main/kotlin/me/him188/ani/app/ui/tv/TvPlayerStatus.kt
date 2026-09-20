/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.subject.episode.EpisodePageLoadError
import org.openani.mediamp.MediaStatus

internal enum class TvPlayerPhase(val waiting: Boolean = false) {
    Playing, Paused, Searching(true), Selecting(true), Resolving(true), Buffering(true), Failed, Ended, Unavailable,
}

internal data class TvPlayerStatus(val phase: TvPlayerPhase, val message: String) {
    val recoverable: Boolean get() = phase == TvPlayerPhase.Failed || phase == TvPlayerPhase.Unavailable
}

/** 状态来自资源查询、解析与播放器, 等待时间只用于展示恢复入口. */
internal fun tvPlayerStatus(
    loadingState: VideoLoadingState,
    mediaStatus: MediaStatus,
    isBuffering: Boolean,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    sourceLoading: Boolean,
    hasSelectedMedia: Boolean,
    pageLoading: Boolean,
    pageError: EpisodePageLoadError?,
): TvPlayerStatus = when {
    pageError is EpisodePageLoadError.SubjectError -> TvPlayerStatus(TvPlayerPhase.Failed, "番剧信息加载失败")
    loadingState is VideoLoadingState.Failed -> TvPlayerStatus(
        TvPlayerPhase.Failed,
        when (loadingState) {
            VideoLoadingState.ResolutionTimedOut -> "资源解析超时"
            VideoLoadingState.NetworkError -> "网络连接失败"
            VideoLoadingState.Cancelled -> "资源加载已取消"
            VideoLoadingState.UnsupportedMedia -> "播放器不支持此资源"
            VideoLoadingState.NoMatchingFile -> "资源中未找到本集视频"
            is VideoLoadingState.UnknownError -> "资源加载失败"
        },
    )
    mediaStatus is MediaStatus.Error -> TvPlayerStatus(TvPlayerPhase.Failed, "播放器无法打开此资源")
    pageLoading -> TvPlayerStatus(TvPlayerPhase.Searching, "正在加载番剧信息…")
    !hasSelectedMedia && sourceLoading -> TvPlayerStatus(TvPlayerPhase.Searching, "正在查询播放资源…")
    !hasSelectedMedia -> TvPlayerStatus(TvPlayerPhase.Unavailable, "尚未选中可播放资源")
    loadingState == VideoLoadingState.ResolvingSource -> TvPlayerStatus(TvPlayerPhase.Resolving, "正在解析播放地址…")
    loadingState is VideoLoadingState.DecodingData -> TvPlayerStatus(
        TvPlayerPhase.Buffering, if (loadingState.isBt) "正在读取种子与视频信息…" else "正在连接视频…",
    )
    mediaStatus == MediaStatus.Ended -> TvPlayerStatus(TvPlayerPhase.Ended, "本集播放结束")
    isBuffering -> TvPlayerStatus(TvPlayerPhase.Buffering, "正在缓冲视频…")
    isPlaying -> TvPlayerStatus(TvPlayerPhase.Playing, "正在播放")
    mediaStatus == MediaStatus.Ready && !playWhenReady -> TvPlayerStatus(TvPlayerPhase.Paused, "已暂停")
    loadingState == VideoLoadingState.Initial -> TvPlayerStatus(TvPlayerPhase.Selecting, "正在准备所选资源…")
    else -> TvPlayerStatus(TvPlayerPhase.Buffering, "正在准备播放…")
}

internal data class TvPlayerUiState(
    val title: String,
    val episodeTitle: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val status: TvPlayerStatus,
    val sourceName: String = "",
    val hasNextEpisode: Boolean = false,
    val mediaKey: String? = null,
)

internal fun tvPlayerSourceMenuMessage(isPlaceholder: Boolean, isQuerying: Boolean, hasAvailableMedia: Boolean): String = when {
    isPlaceholder || (isQuerying && !hasAvailableMedia) -> "正在查询播放资源…"
    isQuerying -> "仍在查询更多资源，可先选择已找到的线路。"
    !hasAvailableMedia -> "暂无可播放资源，可重试查询或返回设置检查数据源。"
    else -> "选择线路即可播放；当前线路已标记。"
}
