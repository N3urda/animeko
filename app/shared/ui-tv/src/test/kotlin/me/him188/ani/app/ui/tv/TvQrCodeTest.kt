/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.tv

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class TvQrCodeTest {
    @Test
    fun authorizationUrlRoundTripsIncludingQueryAndEscaping() {
        val url = "https://bgm.tv/oauth/authorize?client_id=sample&response_type=code&redirect_uri=https%3A%2F%2Fapi.example.org%2Fcallback&state=test-123"
        val matrix = encodeTvQrCode(url)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)))
        val decoded = runCatching { QRCodeReader().decode(bitmap).text }.getOrNull()
        assertNotNull(decoded, "An ordinary QR reader must be able to read the TV authorization code")
        assertEquals(url, decoded)
    }
}
