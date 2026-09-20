/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.app.ui.subject.episode.list.EpisodeListUiState
import me.him188.ani.datasources.api.EpisodeSort

internal data class TvCatalogueEpisodeGroup(
    val key: String,
    val title: String,
    val episodes: List<EpisodeListItem>,
)

internal fun tvCatalogueEpisodeGroups(episodes: EpisodeListUiState): List<TvCatalogueEpisodeGroup> = buildList {
    fun addGroups(items: List<EpisodeListItem>, name: String, keyPrefix: String) {
        items.chunked(24).forEach { group ->
            val first = tvCatalogueEpisodeNumber(group.first())
            val last = tvCatalogueEpisodeNumber(group.last())
            add(TvCatalogueEpisodeGroup(
                "$keyPrefix-${group.first().episodeId}",
                "$name · ${if (first == last) first else "$first – $last"}",
                group,
            ))
        }
    }
    addGroups(episodes.mainEpisodes, "正片", "main")
    addGroups(episodes.otherEpisodes, "特别篇", "special")
}

internal fun tvCataloguePlaybackEpisode(episodes: EpisodeListUiState, preferredId: Int?): EpisodeListItem? {
    val all = episodes.mainEpisodes + episodes.otherEpisodes
    return all.firstOrNull { it.episodeId == preferredId && it.isBroadcast }
        ?: all.firstOrNull { it.isBroadcast }
}

internal fun tvCatalogueEpisodeNumber(episode: EpisodeListItem): String =
    (if (episode.sort is EpisodeSort.Normal) episode.ep ?: episode.sort else episode.sort).toString()

internal fun tvCatalogueEpisodeLabel(episode: EpisodeListItem): String =
    if (episode.sort is EpisodeSort.Normal) "第 ${tvCatalogueEpisodeNumber(episode)} 集" else tvCatalogueEpisodeNumber(episode)

internal fun tvCataloguePlaybackLabel(episode: EpisodeListItem): String {
    val action = if ((episode.playProgress ?: 0f) > 0f && !episode.isDoneOrDropped) "继续观看" else "播放"
    return "$action · ${tvCatalogueEpisodeLabel(episode)}"
}

/** 剧集按业务 ID 分组; 当前分组与焦点均随详情页保存. */
@Composable
internal fun TvCatalogueEpisodeGrid(
    episodes: EpisodeListUiState,
    playEpisodeId: Int?,
    headerFocusKeys: List<String>,
    onEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
) {
    val focus = LocalTvFocusState.current
    val grid = rememberLazyGridState()
    val groups = tvCatalogueEpisodeGroups(episodes)
    var selectedGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    val rememberedEpisode = focus.requestedKey.removePrefix("subject:episode-").toIntOrNull()
    val groupIndex = groups.indexOfFirst { it.key == selectedGroupKey }.takeIf { it >= 0 }
        ?: groups.indexOfFirst { group -> group.episodes.any { it.episodeId == (rememberedEpisode ?: playEpisodeId) } }
            .coerceAtLeast(0)
    val group = groups.getOrNull(groupIndex)
    val currentEpisode = tvCataloguePlaybackEpisode(episodes, playEpisodeId)
    val visibleEpisodes = group?.episodes.orEmpty()
    val targets = buildList {
        headerFocusKeys.forEach { add(it to 0) }
        if (currentEpisode != null) add("subject:locate" to 1)
        if (groupIndex > 0) add("subject:previous-group" to 1)
        if (groupIndex < groups.lastIndex) add("subject:next-group" to 1)
        visibleEpisodes.forEachIndexed { index, episode ->
            if (episode.isBroadcast) add("subject:episode-${episode.episodeId}" to index + 2)
        }
    }
    TvLazyFocusGroup(
        focus, "subject:", targets.map { it.first }, !episodes.isPlaceholder, "page-back",
        scrollToItem = { grid.scrollToItem(targets[it].second) },
        isItemVisible = { key ->
            val index = targets.firstOrNull { it.first == key }?.second
            grid.layoutInfo.visibleItemsInfo.any { it.index == index }
        },
    )
    fun selectGroup(index: Int) {
        val target = groups[index]
        selectedGroupKey = target.key
        focus.requestFocus(target.episodes.firstOrNull { it.isBroadcast }?.let { "subject:episode-${it.episodeId}" }
            ?: if (index > 0) "subject:previous-group" else "subject:next-group")
    }
    LazyVerticalGrid(
        GridCells.Adaptive(220.dp),
        modifier.fillMaxSize().focusRestorer().testTag("tv-episode-grid"),
        state = grid,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "info", span = { GridItemSpan(maxLineSpan) }) { header() }
        item(key = "episode-controls", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(group?.title ?: "选择剧集", Modifier.weight(1f), fontSize = 22.sp)
                    if (currentEpisode != null) TvButton(
                        "定位 ${tvCatalogueEpisodeNumber(currentEpisode)}",
                        {
                            selectedGroupKey = groups.first { page -> page.episodes.any { it.episodeId == currentEpisode.episodeId } }.key
                            focus.requestFocus("subject:episode-${currentEpisode.episodeId}")
                        },
                        Modifier.tvFocusTarget("subject:locate", focus).testTag("tv-episode-locate"),
                    )
                    if (groups.size > 1) {
                        TvButton("上一组", { selectGroup(groupIndex - 1) },
                            Modifier.tvFocusTarget("subject:previous-group", focus).testTag("tv-episode-previous-group"),
                            enabled = groupIndex > 0)
                        TvButton("下一组", { selectGroup(groupIndex + 1) },
                            Modifier.tvFocusTarget("subject:next-group", focus).testTag("tv-episode-next-group"),
                            enabled = groupIndex < groups.lastIndex)
                    }
                }
                if (groups.size > 1) Text("第 ${groupIndex + 1} / ${groups.size} 组 · 每组最多 24 集", fontSize = 16.sp)
                if (visibleEpisodes.isNotEmpty() && visibleEpisodes.none { it.isBroadcast }) {
                    Text("本组剧集尚未播出", fontSize = 18.sp)
                }
            }
        }
        items(visibleEpisodes, key = { "episode-${it.episodeId}" }) { episode ->
            TvEpisodeCard(
                episode, { onEpisode(episode.episodeId) },
                Modifier.tvFocusTarget("subject:episode-${episode.episodeId}", focus),
            )
        }
        if (episodes.isPlaceholder || groups.isEmpty()) {
            item(key = "episodes-empty", span = { GridItemSpan(maxLineSpan) }) {
                TvMessage(if (episodes.isPlaceholder) "正在加载剧集…" else "暂时没有剧集信息")
            }
        }
    }
}
