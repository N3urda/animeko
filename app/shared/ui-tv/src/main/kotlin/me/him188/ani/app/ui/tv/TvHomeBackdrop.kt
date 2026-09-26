/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.MaterialTheme
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.foundation.AsyncImage

/** 内容保护模式下, 背景等待当前条目明确的安全分类; 过期详情不能用于放行图片. */
internal fun tvHomeBackdropImageUrl(
    poster: TvPoster?,
    details: TvHomeSubjectDetails,
    preference: NsfwMode,
): String? {
    if (poster == null || poster.nsfwMode != NsfwMode.DISPLAY) return null
    val info = details.info?.takeIf { it.subjectId == poster.id }
    if (preference != NsfwMode.DISPLAY && info?.nsfw != false) return null
    return poster.imageUrl?.takeIf { it.isNotBlank() }
}

/** 静态封面与渐变提供空间层次; 图片失败时保留低成本的色光背景. */
@Composable
internal fun TvHomeBackdrop(
    poster: TvPoster?,
    details: TvHomeSubjectDetails,
    preference: NsfwMode,
    modifier: Modifier = Modifier,
) {
    val base = MaterialTheme.colorScheme.background
    val imageUrl = tvHomeBackdropImageUrl(poster, details, preference)
    Box(modifier.fillMaxSize().background(base).testTag("tv-home-backdrop")) {
        if (imageUrl != null) key(imageUrl) {
            AsyncImage(
                imageUrl, null,
                Modifier.align(Alignment.TopEnd).fillMaxWidth(0.76f).fillMaxHeight()
                    .testTag("tv-home-backdrop-art"),
                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, crossfade = false,
            )
        }
        Box(Modifier.fillMaxSize().drawWithCache {
            val horizontalShade = Brush.horizontalGradient(
                0f to base, 0.30f to base.copy(alpha = 0.96f),
                0.66f to base.copy(alpha = 0.64f), 1f to base.copy(alpha = 0.42f),
            )
            val verticalShade = Brush.verticalGradient(
                0f to base.copy(alpha = 0.08f), 0.42f to base.copy(alpha = 0.28f), 1f to base.copy(alpha = 0.94f),
            )
            val blueGlow = Brush.radialGradient(
                listOf(Color(0xFF497CA9).copy(alpha = 0.25f), Color.Transparent),
                center = Offset(size.width * 0.10f, size.height * 0.20f), radius = size.width * 0.55f,
            )
            val violetGlow = Brush.radialGradient(
                listOf(Color(0xFF745696).copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height), radius = size.width * 0.50f,
            )
            onDrawBehind {
                drawRect(horizontalShade)
                drawRect(verticalShade)
                drawRect(blueGlow)
                drawRect(violetGlow)
            }
        })
    }
}
