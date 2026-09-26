/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.CanonicalTagKind
import me.him188.ani.app.data.models.subject.kind
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.subject.renderSubjectSeason

/** 焦点介绍保持固定高度, 详情加载只更新文本而不挤动下方内容. */
@Composable
internal fun TvHomePreview(
    poster: TvPoster?,
    source: String,
    details: TvHomeSubjectDetails,
    preference: NsfwMode,
    compact: Boolean,
) {
    val info = details.info?.takeIf { it.subjectId == poster?.id }
    val masked = poster?.nsfwMode != null && poster.nsfwMode != NsfwMode.DISPLAY ||
        (info?.nsfw == true && preference != NsfwMode.DISPLAY)
    val surface = MaterialTheme.colorScheme.surface
    Box(
        Modifier.fillMaxWidth().height(if (compact) 88.dp else 140.dp)
            .clip(RoundedCornerShape(14.dp)).background(surface).testTag("tv-home-preview"),
    ) {
        if (!masked && !poster?.imageUrl.isNullOrBlank()) {
            Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.34f).fillMaxHeight()) {
                AsyncImage(poster?.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, crossfade = false)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(surface, surface.copy(alpha = 0.1f)))))
            }
        }
        Column(
            Modifier.fillMaxWidth(0.79f).padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp),
        ) {
            Text(
                if (masked) "内容偏好保护" else source.ifBlank { "发现你的下一部好番" },
                fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (masked) "内容已遮盖" else poster?.title ?: "今晚，想看点什么？",
                Modifier.testTag("tv-home-preview-title"), fontSize = if (compact) 22.sp else 26.sp,
                fontWeight = FontWeight.Bold, lineHeight = if (compact) 26.sp else 31.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val metadata = if (masked) "遵循搜索中的内容显示偏好" else buildList {
                info?.ratingInfo?.takeIf { it.total > 0 && it.score.toDoubleOrNull()?.let { score -> score > 0 } == true }
                    ?.let { add("Bangumi ${it.score}") }
                info?.airDate?.takeIf { it.isValid }?.let { add(renderSubjectSeason(it)) }
                info?.tags?.filter { it.kind in listOf(CanonicalTagKind.Genre, CanonicalTagKind.Setting, CanonicalTagKind.Emotion, CanonicalTagKind.Source) }
                    ?.take(3)?.map { it.name }?.let { addAll(it) }
            }.joinToString("  ·  ").ifBlank { "左右选番  ·  上下切换栏目  ·  确认查看详情" }
            Text(metadata, Modifier.testTag("tv-home-preview-metadata"), fontSize = 13.sp, lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!compact) Text(
                when {
                    masked -> "可在设置中调整内容显示偏好。"
                    poster == null -> "浏览热门、推荐和今日更新，或向下按类型寻找喜欢的故事。"
                    !info?.summary.isNullOrBlank() -> info.summary
                    details.subjectId == poster.id && details.failed -> "简介暂时未能加载，按确认仍可打开详情。"
                    details.subjectId == poster.id && details.loading -> "正在读取番剧简介…"
                    else -> "按确认打开番剧详情与选集。"
                },
                Modifier.testTag("tv-home-preview-summary"), fontSize = 14.sp, lineHeight = 18.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 首页横卡同时展示封面与两行标题, 封面保持 2:3 比例. */
@Composable
internal fun TvHomePosterCard(
    poster: TvPoster,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    classificationPending: Boolean = false,
    onCheckContent: () -> Unit = {},
) {
    if (poster.nsfwMode == NsfwMode.HIDE) return
    var revealed by rememberSaveable(poster.id, poster.nsfwMode) { mutableStateOf(false) }
    val masked = classificationPending || (poster.nsfwMode == NsfwMode.BLUR && !revealed)
    val height = if (compact) 90.dp else 102.dp
    Card(
        onClick = { if (classificationPending) onCheckContent() else if (masked) revealed = true else onClick() },
        modifier = modifier.width(if (compact) 192.dp else 208.dp).testTag("tv-subject-${poster.id}"),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)), scale = tvCardScale(), border = tvCardBorder(),
    ) {
        Row(Modifier.height(height), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(height * 2 / 3).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("tv-subject-cover-${poster.id}"), contentAlignment = Alignment.Center) {
                if (!masked && !poster.imageUrl.isNullOrBlank()) {
                    AsyncImage(poster.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, crossfade = false)
                } else Text(if (masked) "遮盖" else "ANI", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (classificationPending) "选中查看番剧" else if (masked) "按确认键临时显示" else poster.title, Modifier.testTag("tv-subject-title-${poster.id}"),
                    fontSize = 16.sp, lineHeight = 20.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!masked && poster.subtitle.isNotBlank()) Text(poster.subtitle, fontSize = 12.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
