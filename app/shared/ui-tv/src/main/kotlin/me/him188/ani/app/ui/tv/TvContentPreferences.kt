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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class TvContentPreferencesViewModel : AbstractViewModel(), KoinComponent {
    private val settings: SettingsRepository by inject()
    val nsfwMode = settings.uiSettings.flow.map { it.searchSettings.nsfwMode }.stateInBackground(NsfwMode.HIDE)
}

@Composable
internal fun rememberTvNsfwMode(): NsfwMode {
    val vm = viewModel { TvContentPreferencesViewModel() }
    val mode by vm.nsfwMode.collectAsStateWithLifecycle()
    return mode
}
