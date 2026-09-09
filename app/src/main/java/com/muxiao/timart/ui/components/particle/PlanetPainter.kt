package com.muxiao.timart.ui.components.particle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Path
import android.graphics.Shader
import android.util.LruCache
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip

/**
 * 预渲染「星球质感」球核（架构 §2.18；与 GlowPainter 同构的位图烘焙路径）。
 *
 * 解决大球（详情页 88dp / 星库 34dp）球核简陋问题：原三层平涂是按首页 12–18dp
 * 小球标定的，放大后三层硬边色带、1.6px 高光弧不可见、兜底尘点与球核同色隐形。
 *
 * 烘焙内容（一次生成，LruCache 按「像素直径-颜色」键控）：
 * - 偏光径向渐变球核（受光中心偏左上，与原内芯偏移同位）；
 * - 右下明暗终止线；左上柔和高光斑；
 * - 低对比尘带 ×2 + 颜色哈希种子的烘焙微尘（同一胶囊恒定）；
 * - 球缘 rim 反光（受光侧亮弧 + 背光侧微弱反射弧，线宽随球径缩放）。
 *
 * 帧循环合规：每帧仅 drawBitmap + alpha（零 Shader 创建、零对象分配），
 * 不触碰「全库唯一光晕路径 = GlowPainter 预渲染位图」红线——本类是同一模式在球核上的延伸。
 */
object PlanetPainter {

    /** 位图缓存：上限 8 张（详情页大球 ~600px ≈ 1.4MB，其余为小图；超出按 LRU 淘汰） */
    private val cache = object : LruCache<String, Bitmap>(8) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    /** 绘制用 Paint 复用（帧循环零分配） */
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * 绘制预渲染球核。
     * @param alpha 0..1（呼吸动画注入，仅作用于 drawPaint.alpha，位图本身不重建）
     */
    fun drawPlanet(canvas: Canvas, cx: Float, cy: Float, radius: Float, colorArgb: Int, alpha: Float) {
        if (radius < 1f || alpha <= 0.01f) return
        val sizePx = ceil(radius * 2f).toInt().coerceIn(2, 2048)
        val key = "$sizePx-$colorArgb"
        var bmp = cache.get(key)
        if (bmp == null) {
            bmp = render(sizePx, colorArgb)
            cache.put(key, bmp)
        }
        drawPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawBitmap(bmp, cx - bmp.width / 2f, cy - bmp.height / 2f, drawPaint)
    }

    // ================= 烘焙 =================

    private fun render(sizePx: Int, colorArgb: Int): Bitmap {
        val bmp = createBitmap(sizePx, sizePx)
        val c = Canvas(bmp)
        val r = sizePx / 2f

        // 1) 偏光径向渐变球核：受光中心偏左上（-0.10r / -0.13r，与原三层内芯偏移同位）
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                r - r * 0.10f, r - r * 0.13f, r * 1.18f,
                intArrayOf(
                    lighten(colorArgb, 0.30f),
                    colorArgb,
                    darken(colorArgb, 0.40f),
                    darken(colorArgb, 0.64f),
                ),
                floatArrayOf(0f, 0.42f, 0.76f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawCircle(r, r, r, core)

        // 2) 右下明暗终止线（背光面压暗）
        val term = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                r + r * 0.46f, r + r * 0.50f, r * 1.28f,
                intArrayOf(0x5F000000, 0x00000000),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        c.drawCircle(r, r, r, term)

        // 3) 左上柔和高光斑
        val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                r - r * 0.34f, r - r * 0.40f, r * 0.58f,
                intArrayOf(0x40EDE6D8, 0x00EDE6D8),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        c.drawCircle(r, r, r, hl)

        // 4) 表面细节：低对比尘带 ×2 + 烘焙微尘（clip 进球体；种子 = 颜色哈希，同一胶囊恒定）。
        //    小位图（r < 24px，如 11dp 依赖球）跳过——亚像素尘带只会变成噪声，渐变球核已足够
        if (r >= 24f) {
            val clip = Path().apply { addCircle(r, r, r, Path.Direction.CW) }
            c.withClip(clip) {
                val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = 0x12EDE6D8
                }
                band.strokeWidth = r * 0.13f
                drawArc(
                    r - r * 1.12f,
                    r - r * 0.50f,
                    r + r * 1.12f,
                    r + r * 0.10f,
                    12f,
                    156f,
                    false,
                    band
                )
                band.strokeWidth = r * 0.09f
                band.color = 0x0DEDE6D8
                drawArc(
                    r - r * 1.05f,
                    r + r * 0.30f,
                    r + r * 1.05f,
                    r + r * 0.86f,
                    192f,
                    156f,
                    false,
                    band
                )

                val rnd = java.util.Random(colorArgb.toLong())
                val dust = Paint(Paint.ANTI_ALIAS_FLAG)
                repeat(9) {
                    val a = rnd.nextFloat() * 6.2832f
                    val d = r * (0.15f + rnd.nextFloat() * 0.62f)
                    dust.color = lighten(colorArgb, 0.45f)
                    dust.alpha = 26 + rnd.nextInt(30)
                    drawCircle(
                        r + cos(a) * d,
                        r + sin(a) * d,
                        r * (0.012f + rnd.nextFloat() * 0.014f),
                        dust
                    )
                }
            }
        }

        // 5) 球缘 rim 反光：受光侧亮弧（左上）+ 背光侧微弱反射弧（右下）；线宽随球径缩放
        val rimW = (r * 0.022f).coerceAtLeast(1.2f)
        val rimLit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = rimW
            color = 0x8CEDE6D8.toInt()
        }
        c.drawArc(
            r - r * 0.955f, r - r * 0.955f,
            r + r * 0.955f, r + r * 0.955f, 188f, 64f, false, rimLit)
        val rimBack = Paint(rimLit).apply { color = 0x33A89F92 }
        c.drawArc(
            r - r * 0.955f, r - r * 0.955f,
            r + r * 0.955f, r + r * 0.955f, 16f, 52f, false, rimBack)
        return bmp
    }

    // ================= 颜色工具 =================

    private fun lighten(argb: Int, f: Float): Int = mix(argb, 0xFFFFFF, f)

    private fun darken(argb: Int, f: Float): Int = mix(argb, 0x000000, f)

    private fun mix(a: Int, b: Int, t: Float): Int {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        val rr = (ar + (br - ar) * t).toInt().coerceIn(0, 255)
        val gg = (ag + (bg - ag) * t).toInt().coerceIn(0, 255)
        val bl = (ab + (bb - ab) * t).toInt().coerceIn(0, 255)
        return -0x1000000 or (rr shl 16) or (gg shl 8) or bl
    }
}
