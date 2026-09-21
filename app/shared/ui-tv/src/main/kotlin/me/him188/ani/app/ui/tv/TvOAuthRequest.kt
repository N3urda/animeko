/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface TvOAuthRequestState {
    data object Idle : TvOAuthRequestState
    data object Loading : TvOAuthRequestState
    data class Awaiting(val url: String) : TvOAuthRequestState
    data object Expired : TvOAuthRequestState
    data object Failed : TvOAuthRequestState
    data object Success : TvOAuthRequestState
}

/** 每次授权拥有独立代次; 取消或刷新后旧链接和旧结果无法写回当前页面. */
internal class TvOAuthRequest(
    private val scope: CoroutineScope,
    private val authorize: suspend ((String) -> Unit) -> Boolean,
    private val cancelAuthorization: () -> Unit,
    private val timeoutMillis: Long = 120_000,
) {
    private val mutableState = MutableStateFlow<TvOAuthRequestState>(TvOAuthRequestState.Idle)
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L

    fun start() {
        cancel()
        val current = generation
        mutableState.value = TvOAuthRequestState.Loading
        job = scope.launch {
            try {
                val result = withTimeoutOrNull(timeoutMillis) {
                    authorize { url ->
                        if (current == generation) mutableState.value = TvOAuthRequestState.Awaiting(url)
                    }
                }
                if (current == generation) {
                    generation++
                    if (result == null) cancelAuthorization()
                    mutableState.value = when (result) {
                        true -> TvOAuthRequestState.Success
                        false -> TvOAuthRequestState.Failed
                        null -> TvOAuthRequestState.Expired
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (current == generation) {
                    generation++
                    mutableState.value = TvOAuthRequestState.Failed
                }
            }
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        cancelAuthorization()
        mutableState.value = TvOAuthRequestState.Idle
    }
}
