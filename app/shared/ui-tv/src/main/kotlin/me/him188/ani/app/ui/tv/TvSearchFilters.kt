/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.schedule.AnimeSeason
import me.him188.ani.app.data.models.schedule.AnimeSeasonId
import me.him188.ani.app.data.models.subject.CanonicalTagKind
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.domain.search.withYearFilter
import me.him188.ani.app.ui.exploration.search.buildSearchFilterState

/** 筛选草稿只在应用时提交; 返回关闭不会修改页面查询. */
@Composable
internal fun TvSearchFilterDialog(
    initialQuery: SubjectSearchQuery,
    seasons: List<AnimeSeasonId>,
    onApply: (SubjectSearchQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initialQuery) }
    var category by remember { mutableStateOf("类型") }
    val chips = buildSearchFilterState(draft.tags.orEmpty()).chips
    val categories = chips.map { it.kind.tvFilterLabel } + listOf("年份", "季度", "排序")
    val effectiveCategory = category.takeIf { it in categories } ?: categories.first()
    val chip = chips.firstOrNull { it.kind.tvFilterLabel == effectiveCategory }
    val choices = when (effectiveCategory) {
        "年份" -> listOf(TvFilterChoice("全部年份", draft.year == null) { draft = draft.withYearFilter(null) }) +
            (seasons.map { it.year } + listOfNotNull(draft.year)).distinct().sortedDescending().map { year ->
                TvFilterChoice("${year}年", draft.year == year) { draft = draft.withYearFilter(year) }
            }
        "季度" -> listOf(TvFilterChoice("全部季度", draft.season == null) { draft = draft.copy(season = null) }) +
            AnimeSeason.entries.map { season ->
                TvFilterChoice(season.tvFilterLabel, draft.season == season, draft.year != null) { draft = draft.copy(season = season) }
            }
        "排序" -> SearchSort.entries.map { sort -> TvFilterChoice(sort.tvFilterLabel, draft.sort == sort) { draft = draft.copy(sort = sort) } }
        else -> chip?.values.orEmpty().map { value ->
            TvFilterChoice(value, value in draft.tags.orEmpty()) {
                val tags = draft.tags.orEmpty().let { if (value in it) it - value else it + value }
                draft = draft.copy(tags = tags.ifEmpty { null })
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = rememberTvFocusState("filter-category:类型")
        val categoryList = rememberLazyListState()
        val valuesList = rememberLazyListState()
        val valueKeys = choices.map { "filter-value:$effectiveCategory:${it.label}" }
        CompositionLocalProvider(LocalTvFocusState provides focus) {
            TvRestorePageFocus(focus)
            TvLazyFocusGroup(focus, "filter-category:", categories.map { "filter-category:$it" }, true, "filter-cancel",
                scrollToItem = { categoryList.scrollToItem(it) },
                isItemVisible = { key -> categoryList.layoutInfo.visibleItemsInfo.any { it.key == key } })
            TvLazyFocusGroup(focus, "filter-value:", choices.indices.filter { choices[it].enabled }.map { valueKeys[it] }, true, "filter-category:$effectiveCategory",
                scrollToItem = { enabledIndex ->
                    val index = choices.indices.filter { choices[it].enabled }[enabledIndex]
                    valuesList.scrollToItem(index)
                },
                isItemVisible = { key -> valuesList.layoutInfo.visibleItemsInfo.any { it.key == key } })
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .onPreviewKeyEvent { focus.onNavigationKey(it); false }
                    .padding(horizontal = 32.dp, vertical = 24.dp).testTag("tv-filter-dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("筛选番剧", fontSize = 26.sp)
                Text("类型、设定等可多选 · 右键选择条件 · 应用后搜索", fontSize = 15.sp)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    LazyColumn(Modifier.width(148.dp).testTag("tv-filter-categories"), state = categoryList, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(categories, key = { _, label -> "filter-category:$label" }) { _, label ->
                            TvButton(label, { category = label }, Modifier.fillMaxWidth()
                                .tvFocusTarget("filter-category:$label", focus)
                                .onFocusChanged { if (it.isFocused) category = label }
                                .onPreviewKeyEvent {
                                    if (it.key == Key.DirectionRight) {
                                        if (it.type == KeyEventType.KeyDown) {
                                            val index = choices.indexOfFirst { choice -> choice.selected && choice.enabled }
                                                .takeIf { index -> index >= 0 } ?: choices.indexOfFirst { choice -> choice.enabled }
                                            if (index >= 0) focus.requestFocus(valueKeys[index])
                                        }
                                        true
                                    } else false
                                }.testTag("tv-filter-category:$label"), selected = effectiveCategory == label)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (effectiveCategory == "季度" && draft.year == null) Text("先选择年份，再选择季度", fontSize = 16.sp)
                        if (effectiveCategory == "年份" && seasons.isEmpty()) Text("年份列表暂未加载，可使用类型或设定筛选。", fontSize = 16.sp)
                        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("tv-filter-values").onPreviewKeyEvent {
                            if (it.key == Key.DirectionLeft) {
                                if (it.type == KeyEventType.KeyDown) focus.requestFocus("filter-category:$effectiveCategory")
                                true
                            } else false
                        }, state = valuesList, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(choices, key = { index, _ -> valueKeys[index] }) { index, choice ->
                                TvButton(choice.label, choice.onClick,
                                    Modifier.fillMaxWidth().tvFocusTarget(valueKeys[index], focus).testTag("tv-filter-value:${choice.label}"),
                                    enabled = choice.enabled, selected = choice.selected)
                            }
                        }
                    }
                }
                Text(tvSearchFilterSummary(draft), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton("清空筛选", { draft = SubjectSearchQuery(draft.keywords, type = draft.type) }, Modifier.tvFocusTarget("filter-reset", focus).testTag("tv-filter-reset"))
                    TvButton("取消", onDismiss, Modifier.tvFocusTarget("filter-cancel", focus).testTag("tv-filter-cancel"))
                    TvButton("应用筛选", { onApply(draft) }, Modifier.tvFocusTarget("filter-apply", focus).testTag("tv-filter-apply"))
                }
            }
        }
    }
}

private data class TvFilterChoice(val label: String, val selected: Boolean, val enabled: Boolean = true, val onClick: () -> Unit)

internal fun tvSearchHasConstraints(query: SubjectSearchQuery): Boolean = query.keywords.isNotBlank() ||
    !query.tags.isNullOrEmpty() || query.year != null || query.rating != null || query.nsfw != null

internal fun tvSearchFilterSummary(query: SubjectSearchQuery): String = buildList {
    addAll(query.tags.orEmpty())
    query.year?.let { add("${it}年") }
    query.season?.let { add(it.tvFilterLabel) }
    if (query.sort != SearchSort.MATCH) add(query.sort.tvFilterLabel)
}.joinToString(" · ").ifEmpty { "未设置筛选条件" }

private val CanonicalTagKind?.tvFilterLabel: String
    get() = when (this) {
        CanonicalTagKind.Genre -> "类型"
        CanonicalTagKind.Setting -> "设定"
        CanonicalTagKind.Character -> "角色"
        CanonicalTagKind.Region -> "地区"
        CanonicalTagKind.Emotion -> "情绪"
        CanonicalTagKind.Source -> "来源"
        CanonicalTagKind.Audience -> "受众"
        CanonicalTagKind.Rating -> "分级"
        CanonicalTagKind.Category -> "分类"
        CanonicalTagKind.Technology -> "技术"
        CanonicalTagKind.Series -> "系列"
        null -> "自定义标签"
    }

private val AnimeSeason.tvFilterLabel: String
    get() = when (this) {
        AnimeSeason.WINTER -> "冬季（1月）"
        AnimeSeason.SPRING -> "春季（4月）"
        AnimeSeason.SUMMER -> "夏季（7月）"
        AnimeSeason.AUTUMN -> "秋季（10月）"
    }

private val SearchSort.tvFilterLabel: String
    get() = when (this) {
        SearchSort.MATCH -> "综合匹配"
        SearchSort.RANK -> "评分排名"
        SearchSort.COLLECTION -> "收藏人数"
        SearchSort.DATE -> "开播时间"
    }
