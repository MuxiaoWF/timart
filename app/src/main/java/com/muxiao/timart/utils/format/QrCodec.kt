package com.muxiao.timart.utils.format

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.createBitmap

/**
 * 二维码编解码（N7 模板分享；zxing core，纯 Java，不引入 View 库）：
 *
 * - 编码：[QRCodeWriter] 出 BitMatrix → 自绘到 Bitmap（黑码白底，扫码对比度优先）；
 * - 解码：bitmap → RGBLuminanceSource → [QRCodeReader]（TRY_HARDER），失败返回 null；
 * - 模板分享物 = [TEMPLATE_PREFIX] + 规则 JSON，**零密文零内容**（只有条件结构）。
 */
object QrCodec {

    /** 模板二维码载荷前缀：非此前缀的码一律拒收（防误导任意文本） */
    const val TEMPLATE_PREFIX = "TIMART-TPL1:"

    /**
     * 文本 → 二维码 Bitmap。
     * @param sizePx 输出边长（正方形；560px 足够屏显与分享，体积可控）
     * @return 编码失败（内容超长等）返回 null
     */
    fun encodeBitmap(text: String, sizePx: Int = 560): Bitmap? = runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        createBitmap(width, height, Bitmap.Config.RGB_565)
            .also { it.setPixels(pixels, 0, width, 0, 0, width, height) }
    }.getOrNull()

    /** bitmap → QR 解码文本（失败 / 非二维码返回 null） */
    fun decodeText(bitmap: Bitmap): String? = runCatching {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binarized = com.google.zxing.BinaryBitmap(HybridBinarizer(source))
        val hints = mapOf(com.google.zxing.DecodeHintType.TRY_HARDER to true)
        QRCodeReader().decode(binarized, hints).text
    }.getOrNull()
}
