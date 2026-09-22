/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import androidx.paging.PagingConfig

/** 首批覆盖电视海报列表的可见内容, 靠近列表末尾时继续预取. */
internal val tvCataloguePagingConfig = PagingConfig(
    pageSize = 20,
    initialLoadSize = 20,
    prefetchDistance = 5,
)
