/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.data.models.preference.NsfwMode

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).focusProperties { canFocus = enabled }
            .semantics { this.selected = selected },
        enabled = enabled,
        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun tvCardBorder() = CardDefaults.border(
    focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp)),
)

internal fun tvCardScale() = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)

@Composable
fun TvPage(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    focusState: TvFocusState = rememberTvFocusState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalTvFocusState provides focusState) {
        TvRestorePageFocus(focusState)
        BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val horizontalPadding = if (maxWidth < 800.dp) 32.dp else 48.dp
            Column(
                Modifier.fillMaxSize().onPreviewKeyEvent { focusState.onNavigationKey(it); false }
                    .padding(horizontal = horizontalPadding, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (onBack != null) TvButton("返回", onBack, Modifier.tvFocusTarget("page-back", focusState).testTag("tv-back"))
                    Text(title, Modifier.weight(1f), fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    actions()
                }
                content()
            }
        }
    }
}

@Composable
internal fun TvMessage(
    title: String,
    message: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, fontSize = 24.sp)
        if (message.isNotEmpty()) Text(message, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) TvButton(actionLabel, onAction, actionModifier.testTag("tv-message-action"))
    }
}

internal data class TvPoster(
    val id: Int,
    val title: String,
    val imageUrl: String?,
    val subtitle: String = "",
    val nsfwMode: NsfwMode = NsfwMode.DISPLAY,
)

@Composable
internal fun TvPosterCard(
    poster: TvPoster,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (poster.nsfwMode == NsfwMode.HIDE) return
    var revealed by rememberSaveable(poster.id, poster.nsfwMode) { mutableStateOf(false) }
    val masked = poster.nsfwMode == NsfwMode.BLUR && !revealed
    Card(
        onClick = { if (masked) revealed = true else onClick() },
        modifier = modifier.width(if (compact) 240.dp else 148.dp).testTag("tv-subject-${poster.id}"),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = tvCardScale(),
        border = tvCardBorder(),
    ) {
        if (compact) {
            Row(Modifier.height(144.dp), verticalAlignment = Alignment.CenterVertically) {
                TvPosterCover(poster, masked, Modifier.width(96.dp).height(144.dp))
                TvPosterCaption(poster, masked, Modifier.weight(1f).padding(12.dp))
            }
        } else {
            Column {
                TvPosterCover(poster, masked, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                TvPosterCaption(poster, masked, Modifier.fillMaxWidth().padding(10.dp))
            }
        }
    }
}

@Composable
private fun TvPosterCover(poster: TvPoster, masked: Boolean, modifier: Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant).testTag("tv-subject-cover-${poster.id}"),
        contentAlignment = Alignment.Center,
    ) {
        if (masked || poster.imageUrl.isNullOrBlank()) {
            Text(if (masked) "内容已遮盖" else "Animeko", fontSize = 18.sp)
        } else {
            AsyncImage(
                poster.imageUrl, null, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, crossfade = false,
            )
        }
    }
}

@Composable
private fun TvPosterCaption(poster: TvPoster, masked: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (masked) "按确认键临时显示" else poster.title,
            Modifier.testTag("tv-subject-title-${poster.id}"),
            fontSize = 18.sp, lineHeight = 23.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (!masked && poster.subtitle.isNotBlank()) {
            Text(poster.subtitle, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun <T : Any> TvPosterGrid(
    items: LazyPagingItems<T>,
    key: (T) -> Int,
    poster: (T) -> TvPoster,
    onSubject: (Int) -> Unit,
    modifier: Modifier = Modifier,
    emptyTitle: String = "这里还没有番剧",
    focusState: TvFocusState = LocalTvFocusState.current,
    focusGroup: String = "poster:",
    fallbackKey: String = "page-back",
    ready: Boolean = items.loadState.refresh !is LoadState.Loading,
) {
    val gridState = rememberLazyGridState()
    val snapshot = items.itemSnapshotList
    val entries = remember(snapshot, key, poster) {
        snapshot.items.mapIndexedNotNull { index, item ->
            val display = poster(item)
            if (display.nsfwMode == NsfwMode.HIDE) null
            else TvPagedPoster(snapshot.placeholdersBefore + index, key(item), display)
        }
    }
    val hasError = items.loadState.refresh is LoadState.Error || items.loadState.append is LoadState.Error
    val retryKey = "${focusGroup}retry"
    val moreKey = "${focusGroup}more"
    val canLoadMore = entries.isEmpty() && items.itemCount > 0 &&
        !items.loadState.append.endOfPaginationReached && !hasError
    val focusKeys = remember(entries, focusGroup, hasError, canLoadMore) {
        entries.map { "$focusGroup${it.id}" } + when {
            hasError -> listOf(retryKey)
            canLoadMore -> listOf(moreKey)
            else -> emptyList()
        }
    }
    val requestedId = focusState.requestedKey.takeIf { it.startsWith(focusGroup) }
        ?.removePrefix(focusGroup)?.toIntOrNull()
    val needsTargetPage = requestedId != null && snapshot.items.none { key(it) == requestedId } &&
        items.itemCount > 0 && !items.loadState.append.endOfPaginationReached && !hasError
    var recoveryFinished by remember(items, requestedId, needsTargetPage) { mutableStateOf(false) }
    TvLazyFocusGroup(
        focusState, focusGroup, focusKeys, ready && (!needsTargetPage || recoveryFinished), fallbackKey,
        scrollToItem = { gridState.scrollToItem(it) },
        isItemVisible = { target -> gridState.layoutInfo.visibleItemsInfo.any { it.key == target } },
    )
    LaunchedEffect(items, requestedId, needsTargetPage) {
        if (needsTargetPage) {
            val budget = TvPagingFocusBudget()
            withTimeoutOrNull(3_000) {
                snapshotFlow {
                    TvPagingFocusSnapshot(
                        itemCount = items.itemCount,
                        targetPresent = items.itemSnapshotList.items.any { key(it) == requestedId },
                        appendLoading = items.loadState.append is LoadState.Loading,
                        hasMore = !items.loadState.append.endOfPaginationReached,
                        hasError = items.loadState.append is LoadState.Error || items.loadState.refresh is LoadState.Error,
                    )
                }.first { page ->
                    when (budget.next(page)) {
                        TvPagingFocusAction.FOUND, TvPagingFocusAction.FALLBACK -> true
                        TvPagingFocusAction.REQUEST_PAGE -> { items[page.itemCount - 1]; false }
                        TvPagingFocusAction.WAIT -> false
                    }
                }
            }
        }
        recoveryFinished = true
    }
    BoxWithConstraints(modifier.fillMaxSize().testTag("tv-poster-grid-viewport")) {
        val compact = maxHeight < 320.dp
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (compact) 240.dp else 128.dp),
            modifier = Modifier.fillMaxSize().focusRestorer(),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (entries.isEmpty() && !hasError) {
                item(key = if (canLoadMore) moreKey else "${focusGroup}empty", span = { GridItemSpan(maxLineSpan) }) {
                    when {
                        !ready -> TvMessage("正在加载…")
                        canLoadMore -> TvMessage(
                            "当前页没有可显示的番剧", actionLabel = "加载更多",
                            onAction = { items[items.itemCount - 1] },
                            actionModifier = Modifier.tvFocusTarget(moreKey, focusState),
                        )
                        else -> TvMessage(emptyTitle)
                    }
                }
            }
            items(entries, key = { "$focusGroup${it.id}" }) { entry ->
                items[entry.pagingIndex]
                TvPosterCard(
                    entry.poster, { onSubject(entry.poster.id) },
                    Modifier.tvFocusTarget("$focusGroup${entry.id}", focusState), compact = compact,
                )
            }
            if (hasError) {
                item(key = retryKey, span = { GridItemSpan(maxLineSpan) }) {
                    TvMessage(
                        "加载失败", "请检查网络后重试。", "重试",
                        onAction = { focusState.requestFocus(focusGroup); items.retry() },
                        actionModifier = Modifier.tvFocusTarget(retryKey, focusState),
                    )
                }
            }
            if (items.loadState.append is LoadState.Loading) {
                item(key = "${focusGroup}loading", span = { GridItemSpan(maxLineSpan) }) { TvMessage("正在加载更多…") }
            }
        }
    }
}

private data class TvPagedPoster(val pagingIndex: Int, val id: Int, val poster: TvPoster)
