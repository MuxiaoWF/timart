package com.muxiao.timart.ui.components.particle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import androidx.core.graphics.createBitmap

/**
 * 预渲染光晕绘制器（全项目唯一光晕路径，架构 §2.18）：
 * 启动期按颜色预渲染径向渐变 Bitmap 并缓存；绘制只做 alpha 与缩放。
 * 禁 AGSL / 每帧 Shader / 每帧分配——paint 与 RectF 均为复用字段。
 * 兼容 API 24（无硬件 Shader 依赖）。
 */
object GlowPainter {

    /** 预渲染位图边长（px，绘制时缩放） */
    private const val BITMAP_SIZE = 96

    /** 缓存容量：颜色种类有限（主题 Token 十余种） */
    private const val CACHE_SIZE = 24

    private val cache = object : LruCache<Int, Bitmap>(CACHE_SIZE) {
        override fun create(key: Int): Bitmap = renderGlow(key)
    }

    // isFilterBitmap：预渲染位图（96px）会被放大绘制（星云底光 / 火漆金晕可达数百 px），
    // 不过滤时最近邻采样会产生色带与块状边缘——双线性过滤一次性开启，paint 为复用字段
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        .apply { style = Paint.Style.FILL }

    private val rect = android.graphics.RectF()

    /** 取预渲染光晕位图（缓存命中复用） */
    private fun renderGlow(colorArgb: Int): Bitmap {
        val bmp = createBitmap(BITMAP_SIZE, BITMAP_SIZE)
        val c = Canvas(bmp)
        val half = BITMAP_SIZE / 2f
        val colors = intArrayOf(colorArgb, (colorArgb and 0x00FFFFFF) or 0x66000000, colorArgb and 0x00FFFFFF)
        val stops = floatArrayOf(0f, 0.45f, 1f)
        val shader = RadialGradient(half, half, half, colors, stops, Shader.TileMode.CLAMP)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        c.drawCircle(half, half, half, p)
        return bmp
    }

    /**
     * 绘制光晕：以 (cx, cy) 为中心、radius 为光晕半径。
     * @param alpha 0..1，内部换算 0..255
     */
    fun drawGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, colorArgb: Int, alpha: Float) {
        if (radius <= 0f || alpha <= 0.01f) return
        val bmp = cache.get(colorArgb)
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        paint.shader = null
        canvas.drawBitmap(bmp, null, rect, paint)
    }

    /** 绘制实心尘点（小粒子省光晕路径） */
    fun drawDot(canvas: Canvas, cx: Float, cy: Float, radius: Float, colorArgb: Int, alpha: Float) {
        if (radius <= 0f || alpha <= 0.01f) return
        paint.shader = null
        paint.color = colorArgb
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawCircle(cx, cy, radius, paint)
    }
}
