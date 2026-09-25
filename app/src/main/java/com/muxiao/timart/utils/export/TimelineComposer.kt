package com.muxiao.timart.utils.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
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

/**
 * 尘迹生平时间线长图合成器（N12，架构 §2.12 utils/export）：
 *
 * 「封存 → 条件逐个达成 → 开启 → 归尘」纵向时间线，数据来自尘迹生平弹层同一套
 * meta 刻度（`capsule.condMet.*` / 开启与归尘时间戳），**仅元数据与时间戳，
 * 正文密文永不进图**。视觉沿用海报 Token 体系（深底 + 金色刻点 + 衬线标题 + 极细尘线），
 * 高度按内容动态计算，存 app 外部私有目录经 FileProvider 分享（与 PosterComposer 同路）。
 */
class TimelineComposer(private val context: Context) {

    /** 时间线单刻：阶段标签（封存/达成/开启/归尘）+ 时刻 + 可选条件句 */
    data class Moment(
        val label: String,
        val value: String,
        val sentence: String? = null,
    )

    /** 长图输入（标题 + 时间跨度 + 刻度序列 + 可选元信息块） */
    data class TimelineContent(
        val title: String,
        val spanLine: String,
        val moments: List<Moment>,
        /** 追加元信息块（凝视/回信/笔记）：(标签, 文本)；文本整段排版 */
        val noteBlocks: List<Pair<String, String>> = emptyList(),
        val tagsLine: String? = null,
    )

    /**
     * 合成时间线长图并落盘（JPEG）。
     * @return 成功返回文件；失败返回 null（不抛出，调用方按海报失败口径提示）
     */
    fun compose(content: TimelineContent): File? = runCatching {
        // ---- 测量pass：标题 / 每刻 value / 追加块文本先排版定高，再建画布 ----
        val titleLayout = staticLayout(content.title, titlePaint(), CONTENT_W, maxLines = 2)
        val valueLayouts = content.moments.map { moment ->
            staticLayout(moment.value, valuePaint(), (CONTENT_W - MOMENT_INDENT).toInt(), maxLines = 3)
        }
        val sentenceLayouts = content.moments.map { moment ->
            moment.sentence?.let { staticLayout(it, sentencePaint(), (CONTENT_W - MOMENT_INDENT).toInt(), maxLines = 2) }
        }
        val noteTextLayouts = content.noteBlocks.map { (_, text) ->
            staticLayout(text, valuePaint(), (CONTENT_W - MOMENT_INDENT).toInt(), maxLines = 6)
        }

        var h = MARGIN + HEADER_H + titleLayout.height + SPAN_GAP + CAPTION_SIZE
        val firstDotY = h + MOMENT_TOP_GAP + DOT_R
        var lastDotY = firstDotY
        var i = 0
        content.moments.forEach { _ ->
            val blockH = CAPTION_SIZE + valueLayouts[i].height +
                (sentenceLayouts[i]?.height ?: 0) + MOMENT_GAP
            lastDotY = h + MOMENT_TOP_GAP + DOT_R
            h += MOMENT_TOP_GAP + blockH
            i++
        }
        content.noteBlocks.forEachIndexed { index, (_, _) ->
            h += if (index == 0) SECTION_GAP else 0f
            h += CAPTION_SIZE + noteTextLayouts[index].height + NOTE_GAP
        }
        content.tagsLine?.takeIf { it.isNotBlank() }?.let { h += TAGS_GAP + CAPTION_SIZE }
        val hFinal = (h + FOOTER_ZONE + MARGIN).toInt().coerceAtLeast(MIN_H)

        // ---- 绘制 ----
        val bitmap = createBitmap(W, hFinal)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, hFinal)

        var y = MARGIN.toFloat()
        // 品牌行：金色尘核 +「时粒 · 生平」
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            style = Paint.Style.FILL
            canvas.drawCircle(MARGIN + 7f, y + CAPTION_SIZE * 0.68f, 7f, this)
        }
        canvas.drawText(currentStrings().posterBrandLine, MARGIN + 30f, y + CAPTION_SIZE, captionPaint(INK_SECONDARY))
        y += HEADER_H

        // 标题（衬线，≤2 行省略）
        canvas.withContentOffset(y) { titleLayout.draw(this) }
        y += titleLayout.height + SPAN_GAP
        canvas.drawText(content.spanLine, MARGIN.toFloat(), y + CAPTION_SIZE, captionPaint(INK_DISABLED))
        y += CAPTION_SIZE

        // 纵向时间线：细尘线 + 金色刻点 + 刻度文字
        val linePaint = Paint().apply { color = HAIRLINE; strokeWidth = 3f }
        canvas.drawLine(MARGIN + DOT_R, firstDotY, MARGIN + DOT_R, lastDotY, linePaint)

        y += MOMENT_TOP_GAP
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; style = Paint.Style.FILL }
        var mi = 0
        content.moments.forEach { moment ->
            val dotY = y + CAPTION_SIZE * 0.4f
            canvas.drawCircle(MARGIN + DOT_R, dotY, DOT_R, dotPaint)

            val textX = MARGIN + MOMENT_INDENT
            canvas.drawText(moment.label, textX, y + CAPTION_SIZE, captionPaint(GOLD))
            y += CAPTION_SIZE
            val valueLayout = valueLayouts[mi]
            canvas.withContentOffset(textX, y) { valueLayout.draw(this) }
            y += valueLayout.height
            sentenceLayouts[mi]?.let { layout ->
                canvas.withContentOffset(textX, y + 6f) { layout.draw(this) }
                y += layout.height + 6f
            }
            y += MOMENT_GAP
            mi++
        }

        // 追加元信息块（凝视 / 回信 / 笔记）
        var ni = 0
        content.noteBlocks.forEach { (label, _) ->
            y += SECTION_GAP.takeIf { ni == 0 } ?: NOTE_GAP
            canvas.drawText(label, MARGIN.toFloat(), y + CAPTION_SIZE, captionPaint(INK_DISABLED))
            y += CAPTION_SIZE + 6f
            noteTextLayouts[ni].let { layout ->
                canvas.withContentOffset(MARGIN.toFloat(), y) { layout.draw(this) }
                y += layout.height
            }
            y += NOTE_GAP
            ni++
        }

        content.tagsLine?.takeIf { it.isNotBlank() }?.let { tags ->
            y += TAGS_GAP
            canvas.drawText(tags, MARGIN.toFloat(), y + CAPTION_SIZE, captionPaint(GOLD))
        }

        // 页脚：极细尘线 + 品牌落款
        val dividerY = hFinal - FOOTER_ZONE + 40f
        canvas.drawLine(MARGIN.toFloat(), dividerY, (W - MARGIN).toFloat(), dividerY, Paint().apply { color = HAIRLINE; strokeWidth = 2f })
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = CAPTION_SIZE
            color = INK_DISABLED
        }
        canvas.drawText(currentStrings().posterBrand, MARGIN.toFloat(), dividerY + 76f, brandPaint)

        val dir = context.getExternalFilesDir("Pictures")
            ?: File(context.filesDir, "Pictures").apply { mkdirs() }
        dir.mkdirs()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        val file = File(dir, "timart_timeline_$stamp.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bitmap.recycle()
        file
    }.getOrNull()

    // ---- 公共小件（与 PosterComposer 同体系） ----

    private fun drawBackground(canvas: Canvas, h: Int) {
        val vertical = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            0xFF1A1713.toInt(), 0xFF121110.toInt(),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W.toFloat(), h.toFloat(), Paint().apply { shader = vertical })
    }

    private fun titlePaint() = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textSize = TITLE_SIZE
        color = INK_PRIMARY
    }

    private fun valuePaint() = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF
        textSize = BODY_SIZE
        color = INK_BODY
    }

    private fun sentencePaint() = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF
        textSize = CAPTION_SIZE
        color = INK_SECONDARY
    }

    private fun captionPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF
        textSize = CAPTION_SIZE
        this.color = color
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private inline fun Canvas.withContentOffset(x: Float, y: Float, block: Canvas.() -> Unit) {
        val checkpoint = save()
        translate(x, y)
        try {
            block()
        } finally {
            restoreToCount(checkpoint)
        }
    }

    private inline fun Canvas.withContentOffset(y: Float, block: Canvas.() -> Unit) =
        withContentOffset(MARGIN.toFloat(), y, block)

    companion object {
        /** 画布宽（与海报一致 1080）；高按内容动态 */
        const val W = 1080
        private const val MIN_H = 1200

        private const val MARGIN = 84
        private const val CONTENT_W = W - MARGIN * 2

        private const val TITLE_SIZE = 72f
        private const val BODY_SIZE = 38f
        private const val CAPTION_SIZE = 30f

        /** 品牌行高度 / 跨度行间距 / 刻度块间距 / 首刻上距 / 分区间距 / 文本块间距 / 标签行距 */
        private const val HEADER_H = 48f
        private const val SPAN_GAP = 20f
        private const val MOMENT_GAP = 40f
        private const val MOMENT_TOP_GAP = 26f
        private const val SECTION_GAP = 64f
        private const val NOTE_GAP = 24f
        private const val TAGS_GAP = 48f

        /** 时间线几何：刻点半径 / 刻度文字缩进（相对左缘） */
        private const val DOT_R = 9f
        private const val MOMENT_INDENT = 52f

        /** 页脚预留区 */
        private const val FOOTER_ZONE = 160f

        private const val GOLD = 0xFFE8B44A.toInt()
        private const val INK_PRIMARY = 0xFFEDE6D8.toInt()
        private const val INK_BODY = 0xFFD8D0C0.toInt()
        private const val INK_SECONDARY = 0xFFA89F92.toInt()
        private const val INK_DISABLED = 0xFF6B655C.toInt()
        private const val HAIRLINE = 0xFF2E2A25.toInt()
    }
}
