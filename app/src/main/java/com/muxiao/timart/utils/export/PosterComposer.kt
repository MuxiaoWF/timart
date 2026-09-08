package com.muxiao.timart.utils.export

import com.muxiao.timart.l10n.currentStrings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import java.util.Locale
import java.util.Random
import kotlin.math.min
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip

/**
 * 纪念海报合成器（架构 §2.12 utils/export）：
 *
 * 品牌视觉（PRD §3.4.5：深底 + 衬线标题 + 尘粒边缘 + 天气信息，减少装饰、突出文字与图片）：
 * - 深底 #121110 + 顶部极轻暖光晕（一次成图，非帧循环，允许静态 Shader）；
 * - 页眉「时粒 · 纪念」品牌行 + 解锁日期；衬线大标题按长度自动降级字号；
 * - 金色短划强调线 + 极细尘线分隔；正文整段 StaticLayout 排版，超长自动省略节选；
 * - 图片 EXIF 已由调用方摆正：等比缩放、圆角裁切、细金描边，剩余空间不足自动降级/略过；
 * - 底部封存凭证区（创建/解锁/天气/标签/备注）+ 品牌落款。
 * 存 app 外部私有目录，经 FileProvider 分享。
 */
class PosterComposer(private val context: Context) {

    /** 海报输入（阅读态内容 + 元信息行） */
    data class PosterContent(
        val title: String,
        val paragraphs: List<String>,
        val images: List<Bitmap> = emptyList(),
        val weatherLine: String? = null,
        val createdLine: String,
        val unlockedLine: String? = null,
        val tagsLine: String? = null,
        val note: String? = null,
    )

    /**
     * 合成海报并落盘（JPEG）。
     * @param seed 尘粒随机种子（同种子同边缘，视觉可复现）
     * @return 成功返回文件；失败返回 null（不抛出）
     */
    fun compose(content: PosterContent, seed: Long = System.currentTimeMillis()): File? = runCatching {
        val bitmap = createBitmap(W, H)
        val canvas = Canvas(bitmap)
        drawBackground(canvas)

        // ---- 页眉 ----
        var y = MARGIN.toFloat()
        y = drawHeader(canvas, content, y)
        y = drawTitle(canvas, content.title, y + 34f)
        y = drawAccent(canvas, y + 30f)
        content.weatherLine?.let { y = drawCaptionLine(canvas, it, y + 22f) }

        // ---- 正文（整段排版，超长节选） ----
        y = drawBody(canvas, content, y + 26f)

        // ---- 图片（等比 + 圆角 + 细金边；空间不足自动缩小或略过） ----
        drawImages(canvas, content.images, y + 10f)

        drawFooter(canvas, content)
        drawEdgeDust(canvas, seed)

        val dir = context.getExternalFilesDir("Pictures")
            ?: File(context.filesDir, "Pictures").apply { mkdirs() }
        dir.mkdirs()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        val file = File(dir, "timart_poster_$stamp.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bitmap.recycle()
        file
    }.getOrNull()

    // ---- 背景与氛围 ----

    /** 深底 + 纵向微渐变 + 标题区暖光晕（海报是一次性离屏合成，可用静态 Shader） */
    private fun drawBackground(canvas: Canvas) {
        val vertical = LinearGradient(
            0f, 0f, 0f, H.toFloat(),
            0xFF1A1713.toInt(), 0xFF121110.toInt(),
            Shader.TileMode.CLAMP,
        )
        val paint = Paint().apply { shader = vertical }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)

        val glow = RadialGradient(
            W * 0.5f, 340f, 520f,
            intArrayOf(0x24E8B44A, 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glow }
        canvas.drawRect(0f, 0f, W.toFloat(), 900f, glowPaint)
    }

    /** 种子随机尘粒：四周 140px 边带内的低透明暖灰/金微尘 */
    private fun drawEdgeDust(canvas: Canvas, seed: Long) {
        val rnd = Random(seed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val colors = intArrayOf(0x33A89F92, 0x2A7A7268, 0x30E8B44A)
        repeat(DUST_COUNT) {
            val onHorizontalEdge = rnd.nextBoolean()
            val x: Float
            val y: Float
            if (onHorizontalEdge) {
                x = rnd.nextFloat() * W
                y = if (rnd.nextBoolean()) rnd.nextFloat() * EDGE_BAND else H - rnd.nextFloat() * EDGE_BAND
            } else {
                x = if (rnd.nextBoolean()) rnd.nextFloat() * EDGE_BAND else W - rnd.nextFloat() * EDGE_BAND
                y = rnd.nextFloat() * H
            }
            paint.color = colors[rnd.nextInt(colors.size)]
            canvas.drawCircle(x, y, 1.2f + rnd.nextFloat() * 2.4f, paint)
        }
    }

    // ---- 页眉与标题 ----

    /** 品牌行：左侧金色尘核 +「时粒 · 纪念」，右侧解锁（或创建）日期 */
    private fun drawHeader(canvas: Canvas, content: PosterContent, top: Float): Float {
        val baseline = top + CAPTION_SIZE
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = GOLD
            canvas.drawCircle(MARGIN + 7f, baseline - CAPTION_SIZE * 0.32f, 7f, this)
        }
        val paint = captionPaint(INK_SECONDARY)
        canvas.drawText(brandLine(), MARGIN + 30f, baseline, paint)
        val dateLine = content.unlockedLine ?: content.createdLine
        val dateWidth = paint.measureText(dateLine)
        canvas.drawText(dateLine, W - MARGIN - dateWidth, baseline, paint)
        return baseline
    }

    /** 衬线大标题（按长度降级字号，最多 3 行，超长省略）；返回底部 y */
    private fun drawTitle(canvas: Canvas, title: String, top: Float): Float {
        val size = when {
            title.length <= 12 -> TITLE_SIZE
            title.length <= 26 -> TITLE_SIZE * 0.82f
            else -> TITLE_SIZE * 0.66f
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = size
            color = INK_PRIMARY
        }
        val maxTitleLines = 3
        val lineH = size * 1.16f
        val maxLinesBySpace = ((FOOTER_ZONE * 2.2f) / lineH).toInt().coerceAtMost(maxTitleLines)
        val layout = StaticLayout.Builder
            .obtain(title, 0, title.length, paint, CONTENT_W)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLinesBySpace)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.16f)
            .build()
        canvas.withTranslate(MARGIN.toFloat(), top) { layout.draw(this) }
        return top + layout.height
    }

    /** 金色短划强调线 + 全宽极细尘线；返回底部 y */
    private fun drawAccent(canvas: Canvas, y: Float): Float {
        val paint = Paint().apply { color = GOLD; strokeWidth = 5f }
        canvas.drawLine(MARGIN.toFloat(), y, MARGIN + 96f, y, paint)
        val hairline = Paint().apply { color = HAIRLINE; strokeWidth = 2f }
        canvas.drawLine(MARGIN + 116f, y, (W - MARGIN).toFloat(), y, hairline)
        return y
    }

    /** 信息行（天气等）；返回底部 y */
    private fun drawCaptionLine(canvas: Canvas, text: String, top: Float): Float {
        canvas.drawText(text, MARGIN.toFloat(), top + CAPTION_SIZE, captionPaint(INK_SECONDARY))
        return top + CAPTION_SIZE * 1.4f
    }

    // ---- 正文与图片 ----

    /** 正文：段落合并整段排版；超出底部预留区自动省略并加节选标记；返回底部 y */
    private fun drawBody(canvas: Canvas, content: PosterContent, top: Float): Float {
        val bottomLimit = H - FOOTER_ZONE
        val bodyText = content.paragraphs
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
            .ifBlank { currentStrings().letterNoText }

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SANS_SERIF
            textSize = BODY_SIZE
            color = INK_BODY
        }
        val available = (bottomLimit - top).coerceAtLeast(BODY_SIZE * 2f)
        val lineHeight = BODY_SIZE * 1.55f
        val maxLines = (available / lineHeight).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder
            .obtain(bodyText, 0, bodyText.length, paint, CONTENT_W)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1.55f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.withTranslate(MARGIN.toFloat(), top) { layout.draw(this) }
        var endY = top + layout.height

        // 被省略时补一行节选说明（极小字，避免读者误以为内容缺损）
        val omitted = layout.lineCount >= maxLines && layout.getEllipsisCount(layout.lineCount - 1) > 0
        if (omitted) {
            canvas.drawText(currentStrings().posterLongBodyNote, MARGIN.toFloat(), endY + 34f, captionPaint(INK_DISABLED))
            endY += 34f + CAPTION_SIZE
        }
        return endY
    }

    /** 图片带：等比缩放（宽度优先、高度上限 IMAGE_MAX_H），逐张圆角落位；返回底部 y */
    private fun drawImages(canvas: Canvas, images: List<Bitmap>, top: Float): Float {
        val bottomLimit = H - FOOTER_ZONE
        var y = top
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = 0x3DE8B44A
        }
        for (image in images.take(IMAGE_MAX)) {
            if (y >= bottomLimit - IMAGE_MIN_H) break
            val aspect = image.width.toFloat() / image.height.toFloat().coerceAtLeast(1f)
            var drawW = CONTENT_W.toFloat()
            var drawH = drawW / aspect
            if (drawH > IMAGE_MAX_H) {
                drawH = min(IMAGE_MAX_H, bottomLimit - y - 12f).coerceAtLeast(IMAGE_MIN_H)
                drawW = drawH * aspect
            }
            drawH = drawH.coerceAtMost(bottomLimit - y)
            if (drawH < IMAGE_MIN_H) break
            val left = MARGIN + (CONTENT_W - drawW) / 2f

            val path = Path()
            path.addRoundRect(
                left, y, left + drawW, y + drawH,
                floatArrayOf(IMAGE_RADIUS, IMAGE_RADIUS, IMAGE_RADIUS, IMAGE_RADIUS, IMAGE_RADIUS, IMAGE_RADIUS, IMAGE_RADIUS, IMAGE_RADIUS),
                Path.Direction.CW,
            )
            canvas.withClip(path) {
                val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(image, null, RectF(left, y, left + drawW, y + drawH), bitmapPaint)
            }
            canvas.drawRoundRect(left, y, left + drawW, y + drawH, IMAGE_RADIUS, IMAGE_RADIUS, borderPaint)

            y += drawH + 30f
        }
        return y
    }

    // ---- 封存凭证区 ----

    /** 底部凭证：极细尘线 + 键值行（创建/解锁/天气/标签/备注）+ 品牌落款 */
    private fun drawFooter(canvas: Canvas, content: PosterContent) {
        val hairline = Paint().apply { color = HAIRLINE; strokeWidth = 2f }
        val dividerY = (H - FOOTER_ZONE)
        canvas.drawLine(MARGIN.toFloat(), dividerY, (W - MARGIN).toFloat(), dividerY, hairline)

        val rows = buildList {
            content.note?.takeIf { it.isNotBlank() }?.let { currentStrings().noteLabel to it }
            content.tagsLine?.takeIf { it.isNotBlank() }?.let { currentStrings().tagsLabel to it }
            content.weatherLine?.let { currentStrings().weatherLabel to it }
            content.unlockedLine?.let { currentStrings().unlockedLabel to it }
            add(currentStrings().createdLabel to content.createdLine)
        }

        val labelPaint = captionPaint(INK_DISABLED)
        val valuePaint = captionPaint(INK_SECONDARY)
        var y = dividerY + 52f
        for ((label, value) in rows) {
            canvas.drawText(label, MARGIN.toFloat(), y, labelPaint)
            canvas.drawText(value, MARGIN + LABEL_W, y, valuePaint)
            y += FOOTER_SIZE * 1.75f
        }

        // 品牌落款：金色尘核 +「时粒 TIMART」
        val brandY = H - MARGIN + 14f
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            style = Paint.Style.FILL
            canvas.drawCircle(MARGIN + 6f, brandY - FOOTER_SIZE * 0.34f, 6f, this)
        }
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = FOOTER_SIZE
            color = INK_DISABLED
        }
        canvas.drawText(currentStrings().posterBrand, MARGIN + 26f, brandY, brandPaint)
    }

    // ---- 公共小件 ----

    private fun captionPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF
        textSize = CAPTION_SIZE
        this.color = color
    }

    /** Canvas translate 便捷包装 */
    private inline fun Canvas.withTranslate(x: Float, y: Float, block: Canvas.() -> Unit) {
        val checkpoint = save()
        translate(x, y)
        try {
            block()
        } finally {
            restoreToCount(checkpoint)
        }
    }

    companion object {
        /** 画布尺寸（2:3 竖版） */
        const val W = 1080
        const val H = 1620

        /** 内容边距 */
        private const val MARGIN = 84
        private const val CONTENT_W = W - MARGIN * 2

        private const val TITLE_SIZE = 84f
        private const val BODY_SIZE = 40f
        private const val CAPTION_SIZE = 32f
        private const val FOOTER_SIZE = 30f

        /** 底部凭证区高度（正文与图片不得侵入） */
        private const val FOOTER_ZONE = 330f

        /** 图片约束 */
        private const val IMAGE_MAX = 3
        private const val IMAGE_MAX_H = 640f
        private const val IMAGE_MIN_H = 220f
        private const val IMAGE_RADIUS = 22f

        /** 凭证行标签列宽 */
        private const val LABEL_W = 92f

        private const val DUST_COUNT = 110
        private const val EDGE_BAND = 140f

        private fun brandLine(): String = currentStrings().posterBrandLine

        private const val GOLD = 0xFFE8B44A.toInt()
        private const val INK_PRIMARY = 0xFFEDE6D8.toInt()
        private const val INK_BODY = 0xFFD8D0C0.toInt()
        private const val INK_SECONDARY = 0xFFA89F92.toInt()
        private const val INK_DISABLED = 0xFF6B655C.toInt()
        private const val HAIRLINE = 0xFF2E2A25.toInt()
    }
}
