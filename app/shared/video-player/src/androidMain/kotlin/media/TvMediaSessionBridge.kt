/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.media

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed

/** 系统媒体命令经过 Mediamp 包装层；会话不拥有底层播放器的释放职责。 */
@OptIn(UnstableApi::class)
class TvMediaSessionBridge(
    context: Context,
    private val player: MediampPlayer,
    private val onNextEpisode: () -> Unit,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaId = "tv-playback"
    private var title = "Animeko"
    private var subtitle = ""
    private var artworkUrl: String? = null
    private var hasNext = false
    private val adapter = Adapter()
    private val session = MediaSession.Builder(context, adapter).setId("animeko-tv-${System.identityHashCode(player)}").build()

    val sessionToken: SessionToken get() = session.token

    init {
        scope.launch { merge(player.state, player.mediaProperties, player.currentPositionMillis).collect { adapter.refresh() } }
        player.features[PlaybackSpeed]?.let { speed -> scope.launch { speed.valueFlow.collect { adapter.refresh() } } }
    }

    fun updateMetadata(id: String, title: String, subtitle: String, artworkUrl: String?, hasNext: Boolean) {
        if (mediaId == id && this.title == title && this.subtitle == subtitle && this.artworkUrl == artworkUrl && this.hasNext == hasNext) return
        mediaId = id
        this.title = title
        this.subtitle = subtitle
        this.artworkUrl = artworkUrl
        this.hasNext = hasNext
        adapter.refresh()
    }

    override fun close() {
        scope.cancel()
        session.release()
        adapter.release()
    }

    private inner class Adapter : SimpleBasePlayer(Looper.getMainLooper()) {
        fun refresh() = invalidateState()

        override fun getState(): State {
            val playback = player.state.value
            val duration = player.mediaProperties.value?.durationMillis ?: 0L
            val commands = Player.Commands.Builder().addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            ).apply {
                if (duration > 0) addAll(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD)
                if (hasNext) addAll(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT)
            }.build()
            val metadata = MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle)
                .setArtworkUri(artworkUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse)).build()
            val item = MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata).build()
            val playlist = buildList {
                add(MediaItemData.Builder(mediaId).setMediaItem(item)
                    .setDurationUs(if (duration > 0) duration * 1000 else C.TIME_UNSET)
                    .setIsSeekable(duration > 0).build())
                if (hasNext) add(MediaItemData.Builder("$mediaId-next")
                    .setMediaItem(MediaItem.Builder().setMediaId("$mediaId-next").build()).build())
            }
            val state = State.Builder()
                .setAvailableCommands(commands)
                .setPlaylist(playlist)
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(player.currentPositionMillis.value.coerceAtLeast(0))
                .setPlaybackParameters(PlaybackParameters(player.features[PlaybackSpeed]?.value ?: 1f))
                .setPlayWhenReady(playback.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .setPlaybackState(when {
                    playback.isBuffering || playback.mediaStatus == MediaStatus.Opening -> Player.STATE_BUFFERING
                    playback.mediaStatus == MediaStatus.Ready -> Player.STATE_READY
                    playback.mediaStatus == MediaStatus.Ended -> Player.STATE_ENDED
                    else -> Player.STATE_IDLE
                })
                .setPlaybackSuppressionReason(
                    if (playback.playWhenReady && playback.mediaStatus == MediaStatus.Ready && !playback.isBuffering && !playback.isPlaying) {
                        Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
                    } else Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                )
            if (playback.mediaStatus is MediaStatus.Error) {
                state.setPlayerError(PlaybackException("播放失败，请选择其他数据源", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            }
            return state.build()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            if (playWhenReady) player.play() else player.pause()
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            if (seekCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM || seekCommand == Player.COMMAND_SEEK_TO_NEXT) {
                if (hasNext) onNextEpisode()
            } else {
                val duration = player.mediaProperties.value?.durationMillis ?: 0L
                if (duration > 0 && positionMs != C.TIME_UNSET) player.seekTo(positionMs.coerceIn(0, duration))
            }
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    }
}
