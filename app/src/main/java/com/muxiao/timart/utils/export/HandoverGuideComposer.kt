package com.muxiao.timart.utils.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import com.muxiao.timart.l10n.currentStrings
import java.io.File
import java.util.Locale
import androidx.core.graphics.withTranslation

/**
 * 遗产移交指引图合成器（一页本地渲染的开启指引，与移交包一起交给信任的人）：
 *
 * 品牌视觉与 [PosterComposer] 同语言（深底 + 衬线标题 + 金色强调线）：
 * 页眉「时粒 · 移交」+ 封存日期 → 胶囊标题 → 开启指引编号步骤 → 解锁条件摘要 →
 * 底部封存信息与落款。纯本地 Canvas 离屏一次成图，零网络。
 * 存 app 外部私有目录 Pictures，经 FileProvider 分享。
 */
class HandoverGuideComposer(private val context: Context) {

    /** 指引图输入（移交的胶囊元信息 + 三语指引文案，由调用方注入） */
    data class GuideContent(
        val title: String,
        val createdLine: String,
        /** 开启指引步骤（编号渲染） */
        val steps: List<String>,
        /** 解锁条件句摘要（空 = 显示「开启方式见移交时说明」由调用方处理） */
        val unlockSummary: List<String>,
        /** 口令来源说明行（null = 不显示） */
        val noteLine: String? = null,
    )

    /** 合成指引图并落盘（JPEG）；失败返回 null（不抛出） */
    fun compose(content: GuideContent): File? = runCatching {
        val bitmap = createBitmap(W, H)
        val canvas = Canvas(bitmap)
        drawBackground(canvas)

        // ---- 页眉 ----
        var y = MARGIN.toFloat()
        y = drawHeader(canvas, content.createdLine, y)
        y = drawTitle(canvas, content.title, y + 34f)
        y = drawAccent(canvas, y + 30f)

        // ---- 开启指引步骤 ----
        y = drawSectionLabel(canvas, currentStrings().handoverGuideSection, y + 42f)
        content.steps.forEachIndexed { index, step ->
            y = drawStep(canvas, index + 1, step, y + 18f)
        }

        // ---- 解锁条件摘要 ----
        if (content.unlockSummary.isNotEmpty()) {
            y = drawSectionLabel(canvas, currentStrings().handoverUnlockSection, y + 44f)
            content.unlockSummary.forEach { sentence ->
                y = drawBodyLine(canvas, sentence, y + 14f)
            }
        }

        content.noteLine?.takeIf { it.isNotBlank() }?.let {
            drawBodyLine(canvas, it, y + 30f)
        }

        drawFooter(canvas, content.createdLine)

        val dir = context.getExternalFilesDir("Pictures")
            ?: File(context.filesDir, "Pictures").apply { mkdirs() }
        dir.mkdirs()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        val file = File(dir, "timart_handover_$stamp.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bitmap.recycle()
        file
    }.getOrNull()

    // ---- 背景与页眉（与 PosterComposer 同视觉语言的静态版） ----

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

    private fun drawHeader(canvas: Canvas, dateLine: String, top: Float): Float {
        val baseline = top + CAPTION_SIZE
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = GOLD
            canvas.drawCircle(MARGIN + 7f, baseline - CAPTION_SIZE * 0.32f, 7f, this)
        }
        val paint = captionPaint(INK_SECONDARY)
        canvas.drawText(currentStrings().handoverGuideBrand, MARGIN + 30f, baseline, paint)
        val width = paint.measureText(dateLine)
        canvas.drawText(dateLine, W - MARGIN - width, baseline, paint)
        return baseline
    }

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
        val layout = StaticLayout.Builder
            .obtain(title, 0, title.length, paint, CONTENT_W)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.16f)
            .build()
        canvas.withContentOffset(top) { layout.draw(this) }
        return top + layout.height
    }

    private fun drawAccent(canvas: Canvas, y: Float): Float {
        val paint = Paint().apply { color = GOLD; strokeWidth = 5f }
        canvas.drawLine(MARGIN.toFloat(), y, MARGIN + 96f, y, paint)
        val hairline = Paint().apply { color = HAIRLINE; strokeWidth = 2f }
        canvas.drawLine(MARGIN + 116f, y, (W - MARGIN).toFloat(), y, hairline)
        return y
    }

    // ---- 正文块 ----

    /** 小节标签（金色衬线小字） */
    private fun drawSectionLabel(canvas: Canvas, text: String, top: Float): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = SECTION_SIZE
            color = GOLD
        }
        canvas.drawText(text, MARGIN.toFloat(), top + SECTION_SIZE, paint)
        return top + SECTION_SIZE * 1.4f
    }

    /** 编号步骤（金色序号 + 正文换行排版）；返回底部 y */
    private fun drawStep(canvas: Canvas, number: Int, text: String, top: Float): Float {
        val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = BODY_SIZE
            color = GOLD
        }
        canvas.drawText("$number.", MARGIN.toFloat(), top + BODY_SIZE, numberPaint)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SANS_SERIF
            textSize = BODY_SIZE
            color = INK_BODY
        }
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, CONTENT_W - STEP_INDENT.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1.45f)
            .build()
        canvas.withTranslation(MARGIN + STEP_INDENT, top) {
            layout.draw(canvas)
        }
        return top + layout.height
    }

    /** 普通正文行（自动换行） */
    private fun drawBodyLine(canvas: Canvas, text: String, top: Float): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SANS_SERIF
            textSize = CAPTION_SIZE
            color = INK_SECONDARY
        }
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, CONTENT_W)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1.4f)
            .build()
        canvas.withContentOffset(top) { layout.draw(this) }
        return top + layout.height
    }

    private fun drawFooter(canvas: Canvas, createdLine: String) {
        val hairline = Paint().apply { color = HAIRLINE; strokeWidth = 2f }
        val dividerY = (H - FOOTER_ZONE)
        canvas.drawLine(MARGIN.toFloat(), dividerY, (W - MARGIN).toFloat(), dividerY, hairline)

        val labelPaint = captionPaint(INK_DISABLED)
        val valuePaint = captionPaint(INK_SECONDARY)
        var y = dividerY + 52f
        canvas.drawText(currentStrings().createdLabel, MARGIN.toFloat(), y, labelPaint)
        canvas.drawText(createdLine, MARGIN + LABEL_W, y, valuePaint)
        y += FOOTER_SIZE * 1.75f
        canvas.drawText(currentStrings().handoverFooterLine, MARGIN.toFloat(), y, labelPaint)

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

    private fun captionPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF
        textSize = CAPTION_SIZE
        this.color = color
    }

    private inline fun Canvas.withContentOffset(y: Float, block: Canvas.() -> Unit) {
        val checkpoint = save()
        translate(MARGIN.toFloat(), y)
        try {
            block()
        } finally {
            restoreToCount(checkpoint)
        }
    }

    companion object {
        /** 画布尺寸（与纪念海报一致 2:3 竖版） */
        const val W = 1080
        const val H = 1620

        private const val MARGIN = 84
        private const val CONTENT_W = W - MARGIN * 2

        private const val TITLE_SIZE = 84f
        private const val SECTION_SIZE = 40f
        private const val BODY_SIZE = 40f
        private const val CAPTION_SIZE = 32f
        private const val FOOTER_SIZE = 30f

        /** 底部封存信息区高度 */
        private const val FOOTER_ZONE = 240f

        /** 步骤序号列宽（正文缩进） */
        private const val STEP_INDENT = 64f

        /** 凭证行标签列宽（与 PosterComposer 同值） */
        private const val LABEL_W = 92f

        private const val GOLD = 0xFFE8B44A.toInt()
        private const val INK_PRIMARY = 0xFFEDE6D8.toInt()
        private const val INK_BODY = 0xFFD8D0C0.toInt()
        private const val INK_SECONDARY = 0xFFA89F92.toInt()
        private const val INK_DISABLED = 0xFF6B655C.toInt()
        private const val HAIRLINE = 0xFF2E2A25.toInt()
    }
}
