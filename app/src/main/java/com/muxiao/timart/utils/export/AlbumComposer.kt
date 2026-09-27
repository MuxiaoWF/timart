package com.muxiao.timart.utils.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.RadialGradient
import androidx.core.graphics.createBitmap
import com.muxiao.timart.l10n.currentStrings
import java.io.File

/**
 * 纪念册按年合订（纵向拼接 / 分页导出）：
 *
 * 把某一年开启的胶囊海报页（[PosterComposer.composeBitmap] 产物，1080×1620）加上封面页，
 * 纵向拼接成册——每份 JPEG 最多 [MAX_PAGES_PER_FILE] 页（超出自动分卷），控制单图内存与
 * 存在两页位图，避免整册常驻内存。纯本地，零网络。
 *
 * 「按需逐页产出」指 [compose] 的 `pageAt` 参数：解密 + 渲染一页、拼接、回收，
 * 全程至多同时存在两页位图。
 */
class AlbumComposer(private val context: Context) {

    /**
     * 合订并落盘。
     * @param year 年份（封面大字）
     * @param coverSubtitle 封面副题（如「N 颗胶囊」三语文案）
     * @param pageCount 总页数（不含封面）
     * @param pageAt 第 [pageCount] 页的按需渲染（index 0 起）；返回 null = 该页跳过
     * @return 落盘文件列表（按分卷序）；失败页自动跳过，全失败返回空表
     */
    suspend fun compose(
        year: Int,
        coverSubtitle: String,
        pageCount: Int,
        pageAt: suspend (Int) -> Bitmap?,
    ): List<File> = runCatching {
        val dir = context.getExternalFilesDir("Pictures")
            ?: File(context.filesDir, "Pictures").apply { mkdirs() }
        dir.mkdirs()
        val files = ArrayList<File>()
        var volume = 0
        var index = 0
        while (index < pageCount || (index == 0 && pageCount == 0)) {
            val chunkEnd = minOf(index + MAX_PAGES_PER_FILE, pageCount)
            // 全空年份也出一卷：只有封面（保留仪式感，页数 0 时只产一卷）
            val isFirstVolume = volume == 0
            val stitched = stitch(year, coverSubtitle, pageCount, index, chunkEnd, pageAt, withCover = isFirstVolume)
            if (stitched != null) {
                val file = File(dir, "timart_album_${year}_$volume.jpg")
                file.outputStream().use { out ->
                    stitched.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                stitched.recycle()
                files.add(file)
            }
            if (pageCount == 0) break
            index = chunkEnd
            volume++
        }
        files
    }.getOrDefault(emptyList())

    /** 拼接一卷：可选封面 + [from, until) 区间的页；页渲染失败自动跳过；空卷（无封面无页）返回 null */
    private suspend fun stitch(
        year: Int,
        coverSubtitle: String,
        pageCount: Int,
        from: Int,
        until: Int,
        pageAt: suspend (Int) -> Bitmap?,
        withCover: Boolean,
    ): Bitmap? {
        val pageBitmaps = ArrayList<Bitmap>(until - from)
        var drawnPages = 0
        for (i in from until until) {
            val page = pageAt(i) ?: continue
            pageBitmaps.add(page)
            drawnPages++
        }
        if (!withCover && drawnPages == 0) return null
        val rows = drawnPages + if (withCover) 1 else 0
        val bitmap = createBitmap(W, H * rows)
        val canvas = Canvas(bitmap)
        var y = 0
        if (withCover) {
            drawCover(canvas, year, coverSubtitle, if (pageCount == 0) 0 else pageCount, y)
            y += H
        }
        for (page in pageBitmaps) {
            canvas.drawBitmap(page, 0f, y.toFloat(), null)
            y += H
            page.recycle()
        }
        return bitmap
    }

    /** 封面页：深底 + 年份衬线大字 + 副题 + 品牌落款（与海报同视觉语言） */
    private fun drawCover(canvas: Canvas, year: Int, subtitle: String, pageCount: Int, top: Int) {
        val vertical = LinearGradient(
            0f, top.toFloat(), 0f, (top + H).toFloat(),
            0xFF1A1713.toInt(), 0xFF121110.toInt(),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, top.toFloat(), W.toFloat(), (top + H).toFloat(), Paint().apply { shader = vertical })

        val glow = RadialGradient(
            W * 0.5f, (top + 340f), 520f,
            intArrayOf(0x24E8B44A, 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(
            0f, top.toFloat(), W.toFloat(), (top + 900f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glow },
        )

        val yearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = YEAR_SIZE
            color = 0xFFEDE6D8.toInt()
        }
        val yearText = year.toString()
        val yearX = W / 2f - yearPaint.measureText(yearText) / 2f
        canvas.drawText(yearText, yearX, top + H * 0.42f, yearPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SANS_SERIF
            textSize = SUB_SIZE
            color = 0xFFA89F92.toInt()
        }
        val subText = if (pageCount == 0) subtitle else "$subtitle · $pageCount"
        canvas.drawText(
            subText,
            W / 2f - subPaint.measureText(subText) / 2f,
            top + H * 0.52f,
            subPaint,
        )

        val accent = Paint().apply { color = 0xFFE8B44A.toInt(); strokeWidth = 5f }
        val accentY = top + H * 0.47f
        canvas.drawLine(W / 2f - 48f, accentY, W / 2f + 48f, accentY, accent)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = BRAND_SIZE
            color = 0xFF6B655C.toInt()
        }
        val brand = currentStrings().posterBrand
        canvas.drawText(brand, W / 2f - brandPaint.measureText(brand) / 2f, top + H - MARGIN, brandPaint)
    }

    companion object {
        const val W = PosterComposer.W
        const val H = PosterComposer.H

        /** 每份 JPEG 最多拼接的页数（封面 + 3 页 ≈ 28MP 位图，内存与分享兼容性的平衡点） */
        const val MAX_PAGES_PER_FILE = 3

        private const val MARGIN = 84f
        private const val YEAR_SIZE = 200f
        private const val SUB_SIZE = 40f
        private const val BRAND_SIZE = 30f
    }
}
