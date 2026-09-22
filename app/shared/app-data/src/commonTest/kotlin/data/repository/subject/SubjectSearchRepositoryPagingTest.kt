/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.network.AniSubjectSearchService
import me.him188.ani.app.data.network.BatchSubjectDetails
import me.him188.ani.app.domain.search.RatingRange
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniFavourite
import me.him188.ani.client.models.AniLightRelatedPersonInfo
import me.him188.ani.client.models.AniPaginatedResponse2SubjectSearch
import me.him188.ani.client.models.AniSubjectSearch
import me.him188.ani.client.models.AniTag
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectSearchRepositoryPagingTest {
    @Test
    fun `default search requests complete preview details`() = pagingTest { presenter, requests ->
        assertEquals(
            listOf(
                "name", "summary", "imageLarge", "nsfw", "airDate", "score", "rank", "ratingTotal",
                "tags", "mainEpisodeCount", "lightRelatedPersonInfo",
            ),
            requests.first().fields(),
        )
        val item = presenter.snapshot().items.first()
        assertEquals("Summary 1", item.subjectInfo.summary)
        assertEquals(listOf("TV"), item.subjectInfo.tags.map { it.name })
        assertEquals(12, item.mainEpisodeCount)
        assertEquals(listOf("Studio 1"), item.lightSubjectRelations.lightRelatedPersonInfoList?.map { it.name })
    }

    @Test
    fun `poster search requests only basic fields and parses empty optional details`() = pagingTest(
        includePreviewDetails = false,
    ) { presenter, requests ->
        assertEquals(posterFields, requests.first().fields())
        val item = presenter.snapshot().items.first()
        with(item.subjectInfo) {
            assertEquals(1, subjectId)
            assertEquals("Subject 1", name)
            assertEquals("番剧 1", nameCn)
            assertEquals("https://test.invalid/1.jpg", imageLarge)
            assertTrue(nsfw)
            assertEquals(PackedDate.parseFromDate("2024-01-01"), airDate)
            assertEquals("8.1", ratingInfo.score)
            assertEquals(100, ratingInfo.total)
            assertEquals(42, ratingInfo.rank)
            assertEquals("", summary)
            assertTrue(tags.isEmpty())
        }
        assertEquals(0, item.mainEpisodeCount)
        assertTrue(item.lightSubjectRelations.lightRelatedPersonInfoList.orEmpty().isEmpty())
    }

    @Test
    fun `poster search preserves query filters and date sorting`() = pagingTest(
        includePreviewDetails = false,
        query = SubjectSearchQuery(
            keywords = "test", sort = SearchSort.DATE, tags = listOf("TV"), year = 2024,
            rating = RatingRange(7, 10), nsfw = false,
        ),
        subjects = (1..20).map { id ->
            subject(id).copy(airDate = when (id) {
                1 -> "2024-01-01"
                2 -> "2024-10-01"
                3 -> "2024-04-01"
                else -> "2023-01-01"
            })
        },
    ) { presenter, requests ->
        val parameters = requests.first().url.parameters
        assertEquals("test", parameters["q"])
        assertEquals("airDateDesc", parameters["sortBy"])
        assertEquals("TV", parameters["tags"])
        assertEquals(">=2024-01-01,<2025-01-01", parameters["airDates"])
        assertEquals(">=7,<10", parameters["ratings"])
        assertEquals("exclude", parameters["include_nsfw"])
        assertEquals(posterFields, requests.first().fields())
        assertEquals(listOf(2, 3, 1), presenter.snapshot().items.take(3).map { it.subjectInfo.subjectId })
    }

    @Test
    fun `poster rank search filters ratings below fifty`() = pagingTest(
        includePreviewDetails = false,
        query = SubjectSearchQuery("test", sort = SearchSort.RANK),
        subjects = (1..20).map { id -> subject(id).copy(ratingTotal = if (id == 1) 49 else 50) },
    ) { presenter, requests ->
        assertEquals("ratingDesc", requests.first().url.parameters["sortBy"])
        assertEquals(posterFields, requests.first().fields())
        assertEquals((2..20).toList(), presenter.snapshot().items.map { it.subjectInfo.subjectId })
    }

    @Test
    fun `poster search preserves collection exclusions`() = pagingTest(
        includePreviewDetails = false,
        excludedSubjectIds = listOf(2, 4),
    ) { presenter, _ ->
        assertEquals((1..20).filter { it != 2 && it != 4 }, presenter.snapshot().items.map { it.subjectInfo.subjectId })
    }

    @Test
    fun `poster search keeps its fields when appending and reaches the end`() = pagingTest(
        includePreviewDetails = false,
    ) { presenter, requests ->
        assertEquals(listOf("0"), requests.map { it.url.parameters["offset"] })
        presenter[15]
        runCurrent()
        assertEquals(listOf("0", "20"), requests.map { it.url.parameters["offset"] })
        assertEquals((1..40).toList(), presenter.snapshot().items.map { it.subjectInfo.subjectId })

        presenter[39]
        runCurrent()
        assertEquals(listOf("0", "20", "40"), requests.map { it.url.parameters["offset"] })
        assertTrue(requests.all { it.fields() == posterFields && it.url.parameters["limit"] == "20" })
        presenter[39]
        runCurrent()
        assertEquals(3, requests.size)
    }

    private fun pagingTest(
        includePreviewDetails: Boolean? = null,
        query: SubjectSearchQuery = SubjectSearchQuery("test"),
        subjects: List<AniSubjectSearch> = (1..40).map(::subject),
        excludedSubjectIds: List<Int> = emptyList(),
        block: suspend TestScope.(PagingDataPresenter<BatchSubjectDetails>, List<HttpRequestData>) -> Unit,
    ) = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = testDispatcher
            addHandler { request ->
                requests += request
                val offset = requireNotNull(request.url.parameters["offset"]).toInt()
                val limit = requireNotNull(request.url.parameters["limit"]).toInt()
                val fields = request.fields()
                val items = subjects.drop(offset).take(limit).map { item ->
                    item.copy(
                        summary = if ("summary" in fields) item.summary else "",
                        tags = if ("tags" in fields) item.tags else emptyList(),
                        mainEpisodeCount = if ("mainEpisodeCount" in fields) item.mainEpisodeCount else 0,
                        lightRelatedPersonInfoList = if ("lightRelatedPersonInfo" in fields) {
                            item.lightRelatedPersonInfoList
                        } else emptyList(),
                    )
                }
                respond(
                    Json.encodeToString(AniPaginatedResponse2SubjectSearch(items)),
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        })
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val subjectsApi = SubjectsAniApi("https://test.invalid", client)
        val invoker = object : ApiInvoker<SubjectsAniApi> {
            override suspend fun <R> invoke(action: suspend SubjectsAniApi.() -> R): R = action(subjectsApi)
        }
        val repository = SubjectSearchRepository(
            AniSubjectSearchService(invoker, ioDispatcher = testDispatcher),
            Collections(excludedSubjectIds),
            defaultDispatcher = testDispatcher,
        )
        val pagingConfig = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 5)
        val ignoreDoneAndDropped = suspend { excludedSubjectIds.isNotEmpty() }
        val pages = if (includePreviewDetails == null) {
            repository.searchSubjects(query, ignoreDoneAndDropped, pagingConfig)
        } else {
            repository.searchSubjects(query, ignoreDoneAndDropped, pagingConfig, includePreviewDetails)
        }
        val presenter = object : PagingDataPresenter<BatchSubjectDetails>(mainContext = testDispatcher) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<BatchSubjectDetails>) = Unit
        }
        val collection = backgroundScope.launch { pages.collectLatest(presenter::collectFrom) }
        try {
            runCurrent()
            block(presenter, requests)
        } finally {
            collection.cancelAndJoin()
            client.close()
            engine.close()
        }
    }

    private class Collections(private val excludedSubjectIds: List<Int>) : SubjectCollectionRepository() {
        override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> {
            assertEquals(listOf(UnifiedCollectionType.DONE, UnifiedCollectionType.DROPPED), types)
            return flowOf(excludedSubjectIds)
        }

        override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> = unused()
        override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> = unused()
        override fun subjectCollectionsPager(query: CollectionsFilterQuery, pagingConfig: PagingConfig): Flow<PagingData<SubjectCollectionInfo>> = unused()
        override fun cachedValidSubjectIds(): Flow<List<Int>> = unused()
        override suspend fun updateRecentlyUpdatedSubjectCollections(limit: Int, type: UnifiedCollectionType?, offset: Int) = unused()
        override fun mostRecentlyUpdatedSubjectCollectionsFlow(limit: Int, types: List<UnifiedCollectionType>?): Flow<List<SubjectCollectionInfo>> = unused()
        override suspend fun updateRating(subjectId: Int, score: Int?, comment: String?, tags: List<String>?, isPrivate: Boolean?) = unused()
        override suspend fun setSubjectCollectionTypeOrDelete(subjectId: Int, type: UnifiedCollectionType?) = unused()
        override fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?> = unused()
        override fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?> = unused()
        override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> = unused()
        override suspend fun performBangumiFullSync() = unused()
        override suspend fun getBangumiFullSyncState(): BangumiSyncState? = unused()
        override suspend fun invalidateCache(subjectIds: List<Int>) = unused()
        override suspend fun invalidateAllCaches() = unused()
        private fun unused(): Nothing = error("Collection operation is not used by search")
    }

    private fun subject(id: Int) = AniSubjectSearch(
        id = id.toLong(),
        name = "Subject $id",
        nameCn = "番剧 $id",
        summary = "Summary $id",
        imageLarge = "https://test.invalid/$id.jpg",
        nsfw = true,
        airDate = "2024-01-01",
        score = "8.1",
        rank = 42,
        ratingTotal = 100,
        favorite = AniFavourite(0, 0, 0, 0, 0),
        tags = listOf(AniTag("TV", 1)),
        mainEpisodeCount = 12,
        lightRelatedPersonInfoList = listOf(AniLightRelatedPersonInfo("Studio $id", 1)),
    )

    private fun HttpRequestData.fields(): List<String> = url.parameters["fields"].orEmpty().split(',')

    private val posterFields = listOf("name", "imageLarge", "nsfw", "airDate", "score", "rank", "ratingTotal")
}
