/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.paging.PagingConfig
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.client.apis.HomeAniApi
import me.him188.ani.client.models.AniHomeRecommendationsResponse
import me.him188.ani.client.models.AniSubjectRecommendation
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationRepositoryPagingTest {
    @Test
    fun `default paging preserves its initial request size`() = pagingTest { presenter, requests ->
        assertEquals(listOf(0 to 90), requests)
        assertEquals(90, presenter.size)
    }

    @Test
    fun `small pages wait for the prefetch boundary and append without gaps`() = pagingTest(
        pagingConfig = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 5),
    ) { presenter, requests ->
        assertEquals(listOf(0 to 20), requests)
        assertEquals(20, presenter.size)

        presenter[0]
        presenter[14]
        runCurrent()
        assertEquals(listOf(0 to 20), requests)

        presenter[15]
        runCurrent()
        assertEquals(listOf(0 to 20, 20 to 20), requests)
        assertEquals((1..40).toList(), presenter.snapshot().items.map { (it as RecommendedSubjectInfo).bangumiId })
    }

    @Test
    fun `small pages stop after the final partial page`() = pagingTest(
        pagingConfig = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 5),
        total = 25,
    ) { presenter, requests ->
        presenter[19]
        runCurrent()
        assertEquals(listOf(0 to 20, 20 to 20), requests)
        assertEquals(25, presenter.size)

        presenter[24]
        runCurrent()
        assertEquals(listOf(0 to 20, 20 to 20), requests)
        assertEquals((1..25).toList(), presenter.snapshot().items.map { (it as RecommendedSubjectInfo).bangumiId })
    }

    private fun pagingTest(
        pagingConfig: PagingConfig? = null,
        total: Int = 200,
        block: suspend TestScope.(PagingDataPresenter<RecommendedItemInfo>, List<Pair<Int, Int>>) -> Unit,
    ) = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<Pair<Int, Int>>()
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = testDispatcher
            addHandler { request ->
                val offset = requireNotNull(request.url.parameters["offset"]).toInt()
                val limit = requireNotNull(request.url.parameters["limit"]).toInt()
                requests += offset to limit
                val items = (offset until minOf(offset + limit, total)).map { index ->
                    AniSubjectRecommendation(
                        subjectId = index.toLong() + 1,
                        subjectName = "Subject ${index + 1}",
                        subjectNameCn = "",
                        imageUrl = "",
                        desc1 = "",
                        desc2 = "",
                    )
                }
                respond(
                    Json.encodeToString(AniHomeRecommendationsResponse(total.toLong(), items)),
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        })
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val homeApi = HomeAniApi("https://test.invalid", client)
        val invoker = object : ApiInvoker<HomeAniApi> {
            override suspend fun <R> invoke(action: suspend HomeAniApi.() -> R): R = action(homeApi)
        }
        val repository = RecommendationRepository(invoker, ioDispatcher = testDispatcher)
        val presenter = object : PagingDataPresenter<RecommendedItemInfo>(mainContext = testDispatcher) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<RecommendedItemInfo>) = Unit
        }
        val pages = if (pagingConfig == null) repository.recommendedSubjectsPager()
        else repository.recommendedSubjectsPager(pagingConfig)
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
}
