/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.displayName
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.episode.AiringScheduleForDate
import me.him188.ani.app.domain.episode.EpisodeWithAiringTime
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.datasources.api.EpisodeSort
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

internal data class TvHomeSubjectDetails(
    val subjectId: Int? = null,
    val info: SubjectInfo? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

internal data class TvHomeScheduleState(
    val items: List<TvHomeScheduleItem> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val date: LocalDate? = null,
)

internal data class TvHomeScheduleItem(
    val subjectId: Int,
    val title: String,
    val imageUrl: String,
    val subtitle: String,
)

internal class TvHomeDiscoveryViewModel : AbstractViewModel(), KoinComponent {
    private val subjects: SubjectCollectionRepository by inject()
    private val schedules: AnimeScheduleRepository by inject()
    private val state = TvHomeDiscoveryState(
        backgroundScope,
        loadSubject = { subjects.subjectCollectionFlow(it).first().subjectInfo },
        loadSchedules = { date, zone -> schedules.recentAiringSchedulesFlow(date, zone) },
    )

    val details = state.details
    val schedule = state.schedule

    fun selectSubject(subjectId: Int?) = state.selectSubject(subjectId)
    fun retrySchedule() = state.retrySchedule()
    fun refreshDate() = state.refreshDate()
}

/** 焦点详情和今日放送各自拥有请求代次, 过期结果不能写回当前选择或日期. */
internal class TvHomeDiscoveryState(
    private val scope: CoroutineScope,
    private val loadSubject: suspend (Int) -> SubjectInfo,
    private val loadSchedules: (LocalDate, TimeZone) -> Flow<List<AiringScheduleForDate>>,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = TimeZone::currentSystemDefault,
    private val cacheSize: Int = 24,
) {
    private val lock = Any()
    private val mutableDetails = MutableStateFlow(TvHomeSubjectDetails())
    val details = mutableDetails.asStateFlow()
    private var scheduleKey = currentScheduleKey()
    private val mutableSchedule = MutableStateFlow(TvHomeScheduleState(date = scheduleKey.date))
    val schedule = mutableSchedule.asStateFlow()
    private val subjectCache = LinkedHashMap<Int, SubjectInfo>()
    private var subjectJob: Job? = null
    private var subjectGeneration = 0L
    private var scheduleJob: Job? = null
    private var scheduleGeneration = 0L

    init {
        require(cacheSize > 0)
        synchronized(lock) { startSchedule(scheduleKey) }
        scope.launch {
            while (isActive) {
                val now = clock.now()
                val zone = timeZone()
                val nextMidnight = now.toLocalDateTime(zone).date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone)
                // 每分钟复查时钟和时区, 本地午夜无需等待整分钟.
                delay((nextMidnight - now).coerceIn(1.seconds, 60.seconds))
                refreshDate()
            }
        }
    }

    fun selectSubject(subjectId: Int?) = synchronized(lock) {
        if (subjectId == mutableDetails.value.subjectId && !mutableDetails.value.failed) return@synchronized
        val generation = ++subjectGeneration
        subjectJob?.cancel()
        subjectJob = null
        val cached = subjectId?.let { subjectCache.remove(it) }
        if (subjectId != null && cached != null) subjectCache[subjectId] = cached
        mutableDetails.value = TvHomeSubjectDetails(subjectId, cached, loading = subjectId != null && cached == null)
        if (subjectId == null || cached != null) return@synchronized

        subjectJob = scope.launch {
            try {
                delay(250)
                val info = withTimeout(15_000) { loadSubject(subjectId) }
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    if (generation == subjectGeneration) {
                        subjectCache[subjectId] = info
                        while (subjectCache.size > cacheSize) subjectCache.remove(subjectCache.keys.first())
                        mutableDetails.value = TvHomeSubjectDetails(subjectId, info)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                failSubject(subjectId, generation)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failSubject(subjectId, generation)
            }
        }
    }

    private fun failSubject(subjectId: Int, generation: Long) = synchronized(lock) {
        if (generation == subjectGeneration) {
            mutableDetails.value = TvHomeSubjectDetails(subjectId, failed = true)
        }
    }

    fun retrySchedule() = synchronized(lock) { startSchedule(currentScheduleKey()) }

    fun refreshDate() = synchronized(lock) {
        val current = currentScheduleKey()
        if (current != scheduleKey) startSchedule(current)
    }

    private fun currentScheduleKey(): ScheduleKey {
        val zone = timeZone()
        return ScheduleKey(clock.now().toLocalDateTime(zone).date, zone)
    }

    private fun startSchedule(key: ScheduleKey) {
        scheduleKey = key
        val generation = ++scheduleGeneration
        scheduleJob?.cancel()
        mutableSchedule.value = TvHomeScheduleState(date = key.date)
        scheduleJob = scope.launch {
            try {
                loadSchedules(key.date, key.timeZone).collect { schedules ->
                    val items = schedules.todayItems(key)
                    currentCoroutineContext().ensureActive()
                    synchronized(lock) {
                        if (generation == scheduleGeneration) {
                            mutableSchedule.value = TvHomeScheduleState(items, loading = false, date = key.date)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                synchronized(lock) {
                    if (generation == scheduleGeneration) {
                        mutableSchedule.value = TvHomeScheduleState(loading = false, failed = true, date = key.date)
                    }
                }
            }
        }
    }

    private fun List<AiringScheduleForDate>.todayItems(key: ScheduleKey): List<TvHomeScheduleItem> = asSequence()
        .filter { it.date == key.date }
        .flatMap { it.list }
        .filter { it.airingTime.toLocalDateTime(key.timeZone).date == key.date }
        .sortedWith(compareBy<EpisodeWithAiringTime> { !it.timeKnown }.thenBy { it.airingTime })
        .distinctBy { it.subject.subjectId }
        .take(30)
        .map {
            val localTime = it.airingTime.toLocalDateTime(key.timeZone).time
            val time = if (it.timeKnown) {
                "${localTime.hour.toString().padStart(2, '0')}:${localTime.minute.toString().padStart(2, '0')}"
            } else "时间未定"
            val episode = when (val sort = it.episode.sort) {
                is EpisodeSort.Normal -> "第 ${sort.number.toString().removeSuffix(".0")} 集"
                else -> sort.toString()
            }
            TvHomeScheduleItem(it.subject.subjectId, it.subject.displayName, it.subject.imageLarge, "$time · $episode")
        }
        .toList()

    private data class ScheduleKey(val date: LocalDate, val timeZone: TimeZone)
}
