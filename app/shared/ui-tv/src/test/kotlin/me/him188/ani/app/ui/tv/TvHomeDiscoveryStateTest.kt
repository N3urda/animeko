/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import me.him188.ani.app.data.models.subject.LightEpisodeInfo
import me.him188.ani.app.data.models.subject.LightSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.episode.AiringScheduleForDate
import me.him188.ani.app.domain.episode.EpisodeWithAiringTime
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeDiscoveryStateTest {
    private val today = LocalDate(2026, 9, 26)
    private val tomorrow = LocalDate(2026, 9, 27)
    private val zone = TimeZone.of("Asia/Shanghai")

    @Test
    fun onlySettledFocusLoadsDetailsAndSelectionImmediatelyClearsPreviousInfo() = runTest {
        val calls = mutableListOf<Int>()
        val state = state(loadSubject = { calls += it; subject(it) })
        runCurrent()
        assertTrue(calls.isEmpty())

        state.selectSubject(1)
        runCurrent()
        advanceTimeBy(200)
        state.selectSubject(2)
        runCurrent()
        advanceTimeBy(249)
        assertTrue(calls.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(2), calls)
        assertEquals(2, state.details.value.info?.subjectId)

        state.selectSubject(3)
        assertEquals(3, state.details.value.subjectId)
        assertNull(state.details.value.info)
        assertTrue(state.details.value.loading)
    }

    @Test
    fun leavingSubjectCancelsLoadAndIgnoresNonCooperativeLateResult() = runTest {
        val lateResult = CompletableDeferred<SubjectInfo>()
        val state = state(loadSubject = { id ->
            if (id == 1) withContext(NonCancellable) { lateResult.await() } else subject(id)
        })
        state.selectSubject(1)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        state.selectSubject(2)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, state.details.value.info?.subjectId)
        lateResult.complete(subject(1))
        runCurrent()
        assertEquals(2, state.details.value.info?.subjectId)

        state.selectSubject(null)
        assertEquals(TvHomeSubjectDetails(), state.details.value)
    }

    @Test
    fun successfulCacheIsBoundedAndUsesRecentFocusOrder() = runTest {
        val calls = mutableListOf<Int>()
        val state = state(loadSubject = { calls += it; subject(it) }, cacheSize = 2)
        for (id in listOf(1, 2, 1, 3, 1, 2)) {
            state.selectSubject(id)
            runCurrent()
            advanceTimeBy(250)
            runCurrent()
            assertEquals(id, state.details.value.info?.subjectId)
        }
        assertEquals(listOf(1, 2, 3, 2), calls)
    }

    @Test
    fun failedDetailsAreKeyedAndCanRetrySameSubject() = runTest {
        var attempts = 0
        val state = state(loadSubject = { if (++attempts == 1) error("offline") else subject(it) })
        state.selectSubject(1)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(1, state.details.value.subjectId)
        assertTrue(state.details.value.failed)
        assertFalse(state.details.value.loading)
        assertNull(state.details.value.info)

        state.selectSubject(1)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, attempts)
        assertEquals(1, state.details.value.info?.subjectId)
        assertFalse(state.details.value.failed)
    }

    @Test
    fun unresponsiveSubjectTimesOutAsFailure() = runTest {
        var cancelled = false
        val state = state(loadSubject = {
            try { awaitCancellation() } finally { cancelled = true }
        })
        state.selectSubject(1)
        runCurrent()
        advanceTimeBy(15_251)
        runCurrent()
        assertTrue(cancelled)
        assertTrue(state.details.value.failed)
        assertFalse(state.details.value.loading)
    }

    @Test
    fun todayScheduleFiltersActualLocalDateSortsKnownTimesAndDeduplicatesSubjects() = runTest {
        val state = state(loadSchedules = { _, _ ->
            flowOf(
                listOf(
                    AiringScheduleForDate(today, listOf(
                        episode(3, "2026-09-25T16:00:00Z", timeKnown = false),
                        episode(1, "2026-09-26T13:00:00Z", sort = 13),
                        episode(2, "2026-09-26T12:30:00Z", sort = 2),
                        episode(1, "2026-09-26T14:00:00Z", sort = 14),
                        episode(4, "2026-09-26T16:00:00Z"),
                    )),
                    AiringScheduleForDate(tomorrow, listOf(episode(5, "2026-09-27T10:00:00Z"))),
                ),
            )
        })
        assertTrue(state.schedule.value.loading)
        runCurrent()
        val schedule = state.schedule.value
        assertFalse(schedule.loading)
        assertFalse(schedule.failed)
        assertEquals(today, schedule.date)
        assertEquals(listOf(2, 1, 3), schedule.items.map { it.subjectId })
        assertEquals("20:30 · 第 2 集", schedule.items[0].subtitle)
        assertEquals("21:00 · 第 13 集", schedule.items[1].subtitle)
        assertEquals("时间未定 · 第 1 集", schedule.items[2].subtitle)
        assertEquals("中文 2", schedule.items[0].title)
        assertEquals("image-2", schedule.items[0].imageUrl)
    }

    @Test
    fun scheduleIsBoundedAndContinuesCollectingRepositoryUpdates() = runTest {
        val source = MutableStateFlow(listOf(AiringScheduleForDate(today, (1..45).map {
            episode(it, "2026-09-26T12:00:00Z")
        })))
        val state = state(loadSchedules = { _, _ -> source })
        runCurrent()
        assertEquals(30, state.schedule.value.items.size)
        source.value = listOf(AiringScheduleForDate(today, listOf(episode(51, "2026-09-26T13:00:00Z"))))
        runCurrent()
        assertEquals(listOf(51), state.schedule.value.items.map { it.subjectId })
    }

    @Test
    fun scheduleFailureRetryAndEmptyAreDistinctStates() = runTest {
        var attempts = 0
        val state = state(loadSchedules = { _, _ -> flow {
            if (++attempts == 1) error("offline")
            emit(emptyList())
        } })
        runCurrent()
        assertTrue(state.schedule.value.failed)
        assertFalse(state.schedule.value.loading)
        state.retrySchedule()
        assertTrue(state.schedule.value.loading)
        assertFalse(state.schedule.value.failed)
        runCurrent()
        assertEquals(2, attempts)
        assertTrue(state.schedule.value.items.isEmpty())
        assertFalse(state.schedule.value.failed)
        assertFalse(state.schedule.value.loading)
    }

    @Test
    fun resumingOnAnotherDateImmediatelyClearsOldScheduleAndCancelsOldRequest() = runTest {
        var now = Instant.parse("2026-09-26T04:00:00Z")
        val requests = mutableListOf<LocalDate>()
        var firstCancelled = false
        val newResponse = CompletableDeferred<Unit>()
        val state = state(clock = object : Clock { override fun now() = now }, loadSchedules = { date, requestedZone ->
            assertEquals(zone, requestedZone)
            requests += date
            flow {
                if (date == today) {
                    try {
                        emit(listOf(AiringScheduleForDate(today, listOf(episode(1, "2026-09-26T12:00:00Z")))))
                        awaitCancellation()
                    } finally { firstCancelled = true }
                } else {
                    newResponse.await()
                    emit(emptyList())
                }
            }
        })
        runCurrent()
        assertEquals(1, state.schedule.value.items.size)
        state.refreshDate()
        runCurrent()
        assertEquals(listOf(today), requests)
        now = Instant.parse("2026-09-27T01:00:00Z")
        state.refreshDate()
        assertEquals(tomorrow, state.schedule.value.date)
        assertTrue(state.schedule.value.loading)
        assertTrue(state.schedule.value.items.isEmpty())
        runCurrent()
        assertTrue(firstCancelled)
        assertEquals(listOf(today, tomorrow), requests)
        newResponse.complete(Unit)
        runCurrent()
        assertFalse(state.schedule.value.loading)
    }

    @Test
    fun localMidnightAutomaticallyStartsNewScheduleDate() = runTest {
        val initialTime = Instant.parse("2026-09-26T15:59:59Z")
        val clock = object : Clock { override fun now() = initialTime + testScheduler.currentTime.milliseconds }
        val requests = mutableListOf<LocalDate>()
        val state = state(clock = clock, loadSchedules = { date, _ -> requests += date; flowOf(emptyList()) })
        runCurrent()
        assertEquals(today, state.schedule.value.date)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(tomorrow, state.schedule.value.date)
        assertEquals(listOf(today, tomorrow), requests)
    }

    private fun TestScope.state(
        loadSubject: suspend (Int) -> SubjectInfo = { subject(it) },
        loadSchedules: (LocalDate, TimeZone) -> Flow<List<AiringScheduleForDate>> = { _, _ -> flowOf(emptyList()) },
        cacheSize: Int = 24,
        clock: Clock = object : Clock { override fun now() = Instant.parse("2026-09-26T04:00:00Z") },
    ) = TvHomeDiscoveryState(backgroundScope, loadSubject, loadSchedules, clock, { zone }, cacheSize = cacheSize)

    private fun subject(id: Int) = SubjectInfo.Empty.copy(subjectId = id, nameCn = "中文 $id")

    private fun episode(id: Int, instant: String, sort: Int = 1, timeKnown: Boolean = true) = EpisodeWithAiringTime(
        subject = LightSubjectInfo(id, "Original $id", "中文 $id", "image-$id"),
        episode = LightEpisodeInfo(id * 100 + sort, "Episode", "", PackedDate.Invalid, zone, EpisodeSort(sort), null),
        airingTime = Instant.parse(instant),
        timeKnown = timeKnown,
    )
}
