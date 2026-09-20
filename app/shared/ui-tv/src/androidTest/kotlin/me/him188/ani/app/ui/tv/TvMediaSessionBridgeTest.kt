/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import android.os.Looper
import androidx.annotation.OptIn as AndroidOptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.videoplayer.media.TvMediaSessionBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.playerFeaturesOf
import org.openani.mediamp.metadata.MediaProperties
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@AndroidOptIn(UnstableApi::class)
class TvMediaSessionBridgeTest {
    @Test
    fun controllerConnectsAndForwardsTransportToTheExistingPlayer() = runBlocking {
        withSession {
            awaitController { it.isConnected && it.playbackState == Player.STATE_READY }
            onMain { controller.play() }
            expectCommand(Command.Play)
            awaitController { it.playWhenReady }

            onMain { controller.pause() }
            expectCommand(Command.Pause)
            awaitController { !it.playWhenReady }

            onMain { controller.seekTo(42_000L) }
            expectCommand(Command.Seek(42_000L))
            awaitController { it.currentPosition == 42_000L }

            onMain { controller.seekBack() }
            expectCommand(Command.Seek(32_000L))
            awaitController { it.currentPosition == 32_000L }

            onMain { controller.seekForward() }
            expectCommand(Command.Seek(42_000L))
            awaitController { it.currentPosition == 42_000L }

            onMain { controller.seekToNextMediaItem() }
            withTimeout(TIMEOUT_MILLIS) { nextEpisodeRequests.receive() }
            onMain {
                assertEquals(1, nextEpisodeCount)
                assertEquals(
                    listOf(Command.Play, Command.Pause, Command.Seek(42_000L), Command.Seek(32_000L), Command.Seek(42_000L)),
                    player.commands,
                )
                assertEquals("Transport commands must not open another media", 1, player.backend.openCallCount)
                assertEquals(0, player.closeCount)
            }
        }
    }

    @Test
    fun metadataBufferingAndPlaybackSpeedReachTheConnectedController() = runBlocking {
        withSession(playWhenReady = true) {
            awaitController {
                it.isPlaying && it.currentMediaItem?.mediaId == "episode-1" &&
                    it.mediaMetadata.title.toString() == "Test subject"
            }
            onMain { player.backend.injectStall(true) }
            awaitController { it.playbackState == Player.STATE_BUFFERING && !it.isPlaying }

            onMain { player.backend.injectStall(false) }
            awaitController { it.playbackState == Player.STATE_READY && it.isPlaying }

            onMain { player.speed.set(1.75f) }
            awaitController { it.playbackParameters.speed == 1.75f }

            onMain {
                bridge.updateMetadata("episode-2", "Another subject", "Episode two", null, hasNext = false)
            }
            awaitController {
                it.currentMediaItem?.mediaId == "episode-2" &&
                    it.mediaMetadata.title.toString() == "Another subject" &&
                    it.mediaMetadata.subtitle.toString() == "Episode two" &&
                    !it.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
        }
    }

    @Test
    fun commandsFollowDurationAndNextEpisodeWithoutAllowingMediaReplacement() = runBlocking {
        withSession(durationMillis = null, hasNext = false) {
            awaitController { it.isConnected && it.duration == C.TIME_UNSET }
            onMain {
                assertTrue(controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
                assertFalse(controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
                assertFalse(controller.isCommandAvailable(Player.COMMAND_SEEK_BACK))
                assertFalse(controller.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))
                assertFalse(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
                assertFalse(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
                assertFalse(controller.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM))
                assertFalse(controller.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
            }

            onMain {
                player.backend.injectProperties(MediaProperties(durationMillis = 90_000L))
                bridge.updateMetadata("episode-1", "Test subject", "Episode one", null, hasNext = true)
            }
            awaitController {
                it.duration == 90_000L &&
                    it.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                    it.isCommandAvailable(Player.COMMAND_SEEK_BACK) &&
                    it.isCommandAvailable(Player.COMMAND_SEEK_FORWARD) &&
                    it.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) &&
                    it.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)
            }

            onMain {
                player.backend.injectProperties(MediaProperties(durationMillis = null))
                bridge.updateMetadata("episode-1", "Test subject", "Episode one", null, hasNext = false)
            }
            awaitController {
                it.duration == C.TIME_UNSET &&
                    !it.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                    !it.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            onMain {
                assertFalse(controller.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM))
                assertFalse(controller.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
            }
        }
    }

    @Test
    fun pausedPositionChangePublishesDiscontinuityWithoutPlaybackStateChange() = runBlocking {
        withSession {
            awaitController { it.currentPosition == 5_000L && !it.playWhenReady }
            val pausedState = onMain { player.state.value }
            val discontinuity = CompletableDeferred<Long>()
            onMain {
                controller.addListener(object : Player.Listener {
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) {
                        if (newPosition.positionMs == 45_000L) discontinuity.complete(newPosition.positionMs)
                    }
                })
                // 后端在暂停 seek 后上报位置；状态和元数据保持不变。
                player.backend.injectPosition(45_000L)
            }
            assertEquals(45_000L, withTimeout(TIMEOUT_MILLIS) { discontinuity.await() })
            awaitController { it.currentPosition == 45_000L && !it.playWhenReady }
            onMain {
                assertEquals(pausedState, player.state.value)
                assertTrue("Publishing a position must not issue a transport command", player.commands.isEmpty())
            }
        }
    }

    @Test
    fun closingBridgeDisconnectsControllerAndLeavesUnderlyingPlayerUsable() = runBlocking {
        withSession {
            awaitController { it.isConnected }
            onMain { closeBridge() }
            withTimeout(TIMEOUT_MILLIS) { disconnected.await() }
            onMain {
                assertFalse(controller.isConnected)
                assertEquals("The bridge must not own Mediamp lifetime", 0, player.closeCount)
                assertEquals(MediaStatus.Ready, player.state.value.mediaStatus)
                player.play()
            }
            expectCommand(Command.Play)
            withTimeout(TIMEOUT_MILLIS) { player.state.first { it.isPlaying } }
            onMain { assertEquals(0, player.closeCount) }
        }
    }

    private suspend fun withSession(
        durationMillis: Long? = 120_000L,
        playWhenReady: Boolean = false,
        hasNext: Boolean = true,
        test: suspend SessionFixture.() -> Unit,
    ) {
        val fixture = onMain { SessionFixture() }
        try {
            onMain {
                fixture.player.backend.defaultMediaProperties = MediaProperties(durationMillis = durationMillis)
                fixture.player.backend.setMediaData(
                    UriMediaData("file:///media-session-test.mp4"),
                    playWhenReady = playWhenReady,
                    startPositionMillis = 5_000L,
                )
                fixture.bridge.updateMetadata("episode-1", "Test subject", "Episode one", null, hasNext)
            }
            fixture.connect()
            fixture.test()
        } finally {
            onMain { fixture.close() }
        }
    }

    private class SessionFixture {
        val player = RecordingPlayer()
        val nextEpisodeRequests = Channel<Unit>(Channel.UNLIMITED)
        val disconnected = CompletableDeferred<Unit>()
        private val controllerEvents = Channel<Unit>(Channel.CONFLATED)
        private var connectedController: MediaController? = null
        private var bridgeClosed = false
        var nextEpisodeCount = 0
            private set
        val controller: MediaController get() = checkNotNull(connectedController)
        val bridge = TvMediaSessionBridge(InstrumentationRegistry.getInstrumentation().targetContext, player) {
            nextEpisodeCount++
            nextEpisodeRequests.trySend(Unit)
        }

        suspend fun connect() {
            val future = onMain {
                MediaController.Builder(InstrumentationRegistry.getInstrumentation().targetContext, bridge.sessionToken)
                    .setApplicationLooper(Looper.getMainLooper())
                    .setListener(object : MediaController.Listener {
                        override fun onDisconnected(controller: MediaController) {
                            disconnected.complete(Unit)
                            controllerEvents.trySend(Unit)
                        }
                    })
                    .buildAsync()
            }
            connectedController = withTimeout(TIMEOUT_MILLIS) { future.awaitResult() }
            onMain {
                controller.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        controllerEvents.trySend(Unit)
                    }
                })
            }
        }

        suspend fun awaitController(condition: (MediaController) -> Boolean) {
            withTimeout(TIMEOUT_MILLIS) {
                while (!onMain { condition(controller) }) controllerEvents.receive()
            }
        }

        suspend fun expectCommand(expected: Command) {
            assertEquals(expected, withTimeout(TIMEOUT_MILLIS) { player.commandEvents.receive() })
        }

        fun closeBridge() {
            if (!bridgeClosed) {
                bridgeClosed = true
                bridge.close()
            }
        }

        fun close() {
            try {
                connectedController?.release()
            } finally {
                try {
                    closeBridge()
                } finally {
                    player.close()
                    controllerEvents.close()
                    nextEpisodeRequests.close()
                    player.commandEvents.close()
                }
            }
        }
    }

    private sealed interface Command {
        data object Play : Command
        data object Pause : Command
        data class Seek(val positionMillis: Long) : Command
    }

    @OptIn(InternalForInheritanceMediampApi::class)
    private class RecordingPlayer(
        val backend: TestMediampPlayer = TestMediampPlayer(Dispatchers.Main.immediate),
    ) : MediampPlayer by backend {
        val commands = mutableListOf<Command>()
        val commandEvents = Channel<Command>(Channel.UNLIMITED)
        val speed = MutablePlaybackSpeed()
        override val features = playerFeaturesOf(PlaybackSpeed to speed)
        var closeCount = 0
            private set

        override fun play() {
            record(Command.Play)
            backend.play()
        }

        override fun pause() {
            record(Command.Pause)
            backend.pause()
        }

        override fun seekTo(positionMillis: Long) {
            record(Command.Seek(positionMillis))
            backend.seekTo(positionMillis)
        }

        override fun close() {
            closeCount++
            backend.close()
        }

        private fun record(command: Command) {
            commands += command
            commandEvents.trySend(command)
        }
    }

    @OptIn(InternalForInheritanceMediampApi::class)
    private class MutablePlaybackSpeed : PlaybackSpeed {
        override val valueFlow = MutableStateFlow(1f)
        override val value: Float get() = valueFlow.value
        override fun set(speed: Float) {
            valueFlow.value = speed
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L

        suspend fun <T> onMain(block: suspend () -> T): T = withContext(Dispatchers.Main.immediate) { block() }

        suspend fun <T> ListenableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel(true) }
            addListener(
                {
                    if (continuation.isActive) {
                        try {
                            continuation.resume(get())
                        } catch (error: ExecutionException) {
                            continuation.resumeWithException(error.cause ?: error)
                        } catch (error: Exception) {
                            continuation.resumeWithException(error)
                        }
                    }
                },
                Executor { it.run() },
            )
        }
    }
}
