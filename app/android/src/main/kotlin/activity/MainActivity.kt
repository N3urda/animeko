/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.activity

import android.content.Intent
import android.app.AlertDialog
import android.app.UiModeManager
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.platform.DeviceUiMode
import me.him188.ani.app.domain.mediasource.web.captcha.WebCaptchaDialogHost
import me.him188.ani.android.BuildConfig
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.platform.AniComponentActivity
import me.him188.ani.app.platform.rememberPlatformWindow
import me.him188.ani.app.ui.exprovider.ExternalContentProviderFactory
import me.him188.ani.app.ui.exprovider.LocalExternalContentProvider
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.theme.SystemBarColorEffect
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.main.AniApp
import me.him188.ani.app.ui.main.AniAppContent
import me.him188.ani.app.ui.tv.TvAppContent
import me.him188.ani.app.ui.tv.TvCaptchaDialogHost
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.android.inject

class MainActivity : AniComponentActivity() {
    private val logger = logger<MainActivity>()
    private val aniNavigator = AniNavigator()
    private val settingsRepository: SettingsRepository by inject()
    private var televisionLauncherSeen by mutableStateOf(false)
    private var pendingSubjectId by mutableStateOf<Int?>(null)

    private val externalContentProviderFactory: ExternalContentProviderFactory by inject()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        televisionLauncherSeen = televisionLauncherSeen || intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        setIntent(intent)
        handleStartIntent(intent)
    }

    private fun handleStartIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "ani") return
        if (data.host == "subjects") {
            val id = data.pathSegments.getOrNull(0)?.toIntOrNull() ?: return
            pendingSubjectId = id
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("tv.televisionLauncherSeen", televisionLauncherSeen)
        pendingSubjectId?.let { outState.putInt("tv.pendingSubjectId", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        televisionLauncherSeen = savedInstanceState?.getBoolean("tv.televisionLauncherSeen") == true ||
            intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        if (savedInstanceState == null) handleStartIntent(intent)
        else if (savedInstanceState.containsKey("tv.pendingSubjectId")) {
            pendingSubjectId = savedInstanceState.getInt("tv.pendingSubjectId")
        }

        enableEdgeToEdge(
            // 透明状态栏
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            // 透明导航栏
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )

        // 允许画到 system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val toaster = object : Toaster {
            override fun toast(text: String) {
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
            }
        }

        val externalContentProvider = externalContentProviderFactory.create(this, lifecycleScope)

        setContent {
            val uiSettings by settingsRepository.uiSettings.flow.collectAsStateWithLifecycle(null)
            val deviceUiMode = uiSettings?.deviceUiMode ?: return@setContent
            val televisionMode = deviceUiMode.useTelevisionUi(
                televisionUiMode = getSystemService(UiModeManager::class.java)?.currentModeType ==
                    Configuration.UI_MODE_TYPE_TELEVISION,
                leanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
                televisionLauncher = televisionLauncherSeen,
            )
            val selectMode: (DeviceUiMode) -> Unit = { mode ->
                lifecycleScope.launch { settingsRepository.uiSettings.update { copy(deviceUiMode = mode) } }
            }
            val needsModeChoice = deviceUiMode == DeviceUiMode.Auto && !televisionMode &&
                !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) &&
                resources.configuration.navigation != Configuration.NAVIGATION_NONAV
            if (needsModeChoice) {
                DisposableEffect(Unit) {
                    val dialog = AlertDialog.Builder(this@MainActivity)
                        .setTitle("选择 Animeko 界面")
                        .setMessage("使用方向键遥控器时，请选择电视界面。")
                        .setPositiveButton("电视 · 遥控器") { _, _ -> selectMode(DeviceUiMode.Television) }
                        .setNegativeButton("手机 / 平板") { _, _ -> selectMode(DeviceUiMode.Standard) }
                        .setOnCancelListener { finish() }
                        .show()
                    onDispose { dialog.dismiss() }
                }
                return@setContent
            }
            LaunchedEffect(televisionMode) {
                requestedOrientation = if (televisionMode) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            DisposableEffect(televisionMode) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (televisionMode) controller.hide(WindowInsetsCompat.Type.systemBars())
                onDispose { if (televisionMode) controller.show(WindowInsetsCompat.Type.systemBars()) }
            }
            key(televisionMode) {
                AniApp(webCaptchaHost = { manager ->
                    if (televisionMode) TvCaptchaDialogHost(manager)
                    else WebCaptchaDialogHost(manager)
                }) {
                    val externalComponentProviderUpdated by rememberUpdatedState(externalContentProvider)

                    SystemBarColorEffect()

                    CompositionLocalProvider(
                        LocalToaster provides toaster,
                        LocalPlatformWindow provides rememberPlatformWindow(this),
                        LocalExternalContentProvider provides externalComponentProviderUpdated,
                    ) {
                        // Expose Modifier.testTag as resource-id in accessibility/uiautomator dumps,
                        // so UI-automation agents can locate elements by stable ids (debug only).
                        @OptIn(ExperimentalComposeUiApi::class)
                        val rootModifier = if (BuildConfig.DEBUG) {
                            Modifier.semantics { testTagsAsResourceId = true }
                        } else {
                            Modifier
                        }
                        Box(rootModifier) {
                            if (televisionMode) {
                                TvAppContent(aniNavigator, deviceUiMode, selectMode)
                            } else {
                                AniAppContent(aniNavigator)
                            }
                            LaunchedEffect(pendingSubjectId) {
                                val subjectId = pendingSubjectId ?: return@LaunchedEffect
                                try {
                                    aniNavigator.awaitBackStack()
                                    withFrameNanos { }
                                    aniNavigator.navigateSubjectDetails(subjectId, placeholder = null)
                                    pendingSubjectId = null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logger.error(e) { "Failed to navigate to subject details" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
