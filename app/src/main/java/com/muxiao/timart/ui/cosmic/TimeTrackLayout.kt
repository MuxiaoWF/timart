package com.muxiao.timart.ui.cosmic

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * 时轨布局纯函数（架构 §2.14，可单测）：
 * - 创建时间 → 轨道半径/角度：内圈早、外圈晚，轨内按序均匀铺开；
 * - 缩放 0.6×–2.5×（clamp）；
 * - 用户自定义星图坐标（layoutX/Y 归一化）覆盖轨道位置；
 * - 焦点球标题显隐与邻近渐进（titleAlpha）。
 *
 * 零 Android 依赖：输入/输出均为纯 Kotlin 类型，JVM 单测直接覆盖。
 */
object TimeTrackLayout {

    /** 缩放下限 */
    const val MIN_ZOOM = 0.6f

    /** 缩放上限 */
    const val MAX_ZOOM = 2.5f

    /** 缩放钳制 */
    fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** 内圈轨道半径占 baseRadius 比例（与 [layout] 一致） */
    private const val ORBIT_INNER_RATIO = 0.30f

    /** 相邻轨道间距占 baseRadius 比例（与 [layout] 一致） */
    private const val ORBIT_GAP_RATIO = 0.22f

    /** 最外轨允许占 baseRadius 的最大比例（留 2% 边距防裁边） */
    private const val OUTER_FIT_RATIO = 0.98f

    /** 密度基准数量：单轨满员（6 颗）及更少时不因密度收拢 */
    private const val DENSITY_BASE_COUNT = 6

    /** 密度幂次：数量每 ×4，自动 zoom ×0.71——温和的「越多越小」（16 颗 ≈0.78，约 47 颗起触 [MIN_ZOOM] 下限） */
    private const val DENSITY_EXPONENT = 0.25f

    /**
     * 容量推算：count 颗胶囊所需的轨道数（第 n 轨容量 = 6 + 3n，与 [layout] 分配规则一致）。
     */
    fun orbitCountFor(count: Int): Int {
        if (count <= 0) return 1
        var remaining = count
        var orbits = 0
        while (remaining > 0) {
            remaining -= 6 + orbits * 3
            orbits++
        }
        return orbits
    }

    /**
     * 自动缩放兜底：让 count 颗胶囊的最外轨完整落在画布内所需的最大 zoom；
     * 并叠加密度维度——数量增多时星图整体温和缩小（轨道+球联动等比缩放，
     * 球侧见 TimeTrackCanvas.orbitDotRadius），数量每 ×4，zoom ×0.71。
     * 结果钳制在 [MIN_ZOOM, 1]——自动适配只收拢不放大，用户手动缩放不受限。
     */
    fun fitZoom(count: Int): Float {
        val orbits = orbitCountFor(count)
        val radiusNeed = OUTER_FIT_RATIO / (ORBIT_INNER_RATIO + ORBIT_GAP_RATIO * (orbits - 1))
        val density = (DENSITY_BASE_COUNT.toFloat() / count.coerceAtLeast(1)).pow(DENSITY_EXPONENT)
        return min(radiusNeed, density).coerceIn(MIN_ZOOM, 1f)
    }

    /** 布局输入条目（轻量纯数据，测试构造方便） */
    data class TrackItem(
        val id: String,
        val title: String,
        val createTimestamp: Long,
        /** 用户自定义星图坐标（归一化 -1.5..1.5，相对 baseRadius），null 走轨道算法 */
        val layoutX: Float? = null,
        val layoutY: Float? = null,
    )

    /** 布局输出：星图坐标系下的球位置（画布中心为原点参考） */
    data class PlacedCapsule(
        val id: String,
        val title: String,
        val x: Float,
        val y: Float,
        /** 所在轨道半径（px，已含 zoom） */
        val orbitRadius: Float,
        /** 标题透明度（焦点显隐与邻近渐进） */
        val titleAlpha: Float,
    )

    data class TrackLayoutResult(
        val capsules: List<PlacedCapsule>,
        /** 各轨道半径（由内到外，px，已含 zoom；绘制轨道细线用） */
        val orbitRadii: List<Float>,
    )

    /**
     * 星图布局计算。
     *
     * 轨道规则：第 n 轨容量 = 6 + 3n（内圈小外圈大）；内圈半径 = baseRadius×0.30，
     * 轨距 = baseRadius×0.22；相邻轨道相位错开 0.9rad 避免连珠。
     *
     * @param baseRadius 可用半径（画布短边一半减边距，px，未含 zoom）
     */
    fun layout(
        items: List<TrackItem>,
        centerX: Float,
        centerY: Float,
        baseRadius: Float,
        zoom: Float,
        focusId: String?,
    ): TrackLayoutResult {
        val z = clampZoom(zoom)
        if (items.isEmpty() || baseRadius <= 0f) {
            return TrackLayoutResult(emptyList(), emptyList())
        }
        val sorted = items.sortedBy { it.createTimestamp }

        // 轨道分配（保持时间序）
        val orbits = ArrayList<List<TrackItem>>()
        var remaining = sorted
        var orbitIndex = 0
        while (remaining.isNotEmpty()) {
            val capacity = 6 + orbitIndex * 3
            orbits.add(remaining.take(capacity))
            remaining = remaining.drop(capacity)
            orbitIndex++
        }

        val innerRadius = baseRadius * ORBIT_INNER_RATIO
        val gap = baseRadius * ORBIT_GAP_RATIO
        val radii = orbits.indices.map { (innerRadius + it * gap) * z }

        val placed = ArrayList<PlacedCapsule>(sorted.size)
        orbits.forEachIndexed { oi, group ->
            val r = radii[oi]
            val phase = if (oi % 2 == 0) 0.0 else 0.9
            group.forEachIndexed { j, item ->
                val x: Float
                val y: Float
                if (item.layoutX != null && item.layoutY != null) {
                    // 自定义星图坐标覆盖（同样受 zoom 缩放）
                    x = centerX + item.layoutX * baseRadius * z
                    y = centerY + item.layoutY * baseRadius * z
                } else {
                    val angle = 2.0 * Math.PI * j / group.size + phase
                    x = centerX + (cos(angle) * r).toFloat()
                    y = centerY + (sin(angle) * r).toFloat()
                }
                placed.add(PlacedCapsule(item.id, item.title, x, y, r, DEFAULT_TITLE_ALPHA))
            }
        }

        // 焦点标题显隐与邻近渐进
        val focus = placed.firstOrNull { it.id == focusId }
        val withAlpha = placed.map { p ->
            val alpha = when {
                focus == null -> DEFAULT_TITLE_ALPHA
                p.id == focusId -> 1f
                else -> {
                    val dx = p.x - focus.x
                    val dy = p.y - focus.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    (1f - dist / (baseRadius * z * 0.9f)).coerceIn(NEAR_MIN_ALPHA, NEAR_MAX_ALPHA)
                }
            }
            p.copy(titleAlpha = alpha)
        }
        return TrackLayoutResult(withAlpha, radii)
    }

    /** 无焦点时的标题基础透明度 */
    const val DEFAULT_TITLE_ALPHA = 0.7f

    /** 邻近渐进下限 */
    const val NEAR_MIN_ALPHA = 0.25f

    /** 邻近渐进上限 */
    const val NEAR_MAX_ALPHA = 0.85f
}
