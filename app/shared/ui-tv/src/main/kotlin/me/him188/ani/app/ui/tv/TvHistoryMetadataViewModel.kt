/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal sealed interface TvHistorySubjectState {
    data object Loading : TvHistorySubjectState
    data object Failed : TvHistorySubjectState
    data class Ready(val nsfw: Boolean) : TvHistorySubjectState
}

/** 历史记录缺少内容分级字段; 按条目去重读取真实仓库, 未确认的条目不能暴露标题或直接播放. */
internal class TvHistoryMetadataViewModel : AbstractViewModel(), KoinComponent {
    private val subjects: SubjectCollectionRepository by inject()
    private val ids = MutableStateFlow<Set<Int>>(emptySet())
    private val retryVersion = MutableStateFlow(0)
    private val mutableStates = MutableStateFlow<Map<Int, TvHistorySubjectState>>(emptyMap())
    val states = mutableStates.asStateFlow()

    init {
        backgroundScope.launch {
            combine(ids, retryVersion) { subjectIds, _ -> subjectIds }.collectLatest { requested ->
                mutableStates.update { previous ->
                    requested.associateWith { previous[it] as? TvHistorySubjectState.Ready ?: TvHistorySubjectState.Loading }
                }
                val semaphore = Semaphore(4)
                coroutineScope {
                    requested.forEach { subjectId ->
                        if (mutableStates.value[subjectId] is TvHistorySubjectState.Ready) return@forEach
                        launch {
                            val result = semaphore.withPermit {
                                try {
                                    val collection = withTimeout(15_000) { subjects.subjectCollectionFlow(subjectId).first() }
                                    TvHistorySubjectState.Ready(collection.subjectInfo.nsfw)
                                } catch (e: TimeoutCancellationException) {
                                    TvHistorySubjectState.Failed
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    TvHistorySubjectState.Failed
                                }
                            }
                            mutableStates.update { it + (subjectId to result) }
                        }
                    }
                }
            }
        }
    }

    fun load(subjectIds: Set<Int>) { ids.value = subjectIds }

    fun retry(subjectId: Int) {
        mutableStates.update { it + (subjectId to TvHistorySubjectState.Loading) }
        retryVersion.update { it + 1 }
    }
}
