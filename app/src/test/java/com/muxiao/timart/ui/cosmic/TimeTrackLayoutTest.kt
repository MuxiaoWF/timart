package com.muxiao.timart.ui.cosmic

import com.muxiao.timart.domain.model.AnimationTier
import com.muxiao.timart.ui.components.particle.ParticleBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时轨布局纯函数单测（架构 §2.19）：
 * 时间→轨道位置单调性、自定义坐标覆盖、缩放边界、焦点标题渐进；
 * 附出口标准③锚点断言：MEDIUM 档背景粒子数 = 40。
 */
class TimeTrackLayoutTest {

    private fun item(id: String, ts: Long, layoutX: Float? = null, layoutY: Float? = null) =
        TimeTrackLayout.TrackItem(id = id, title = "胶囊$id", createTimestamp = ts, layoutX = layoutX, layoutY = layoutY)

    // ---- 1. 时间 → 轨道位置单调性 ----

    @Test
    fun earlierCapsulesNeverFartherThanLater() {
        val result = TimeTrackLayout.layout(
            items = listOf(item("a", 1000L), item("b", 2000L), item("c", 3000L), item("d", 4000L)),
            centerX = 0f,
            centerY = 0f,
            baseRadius = 300f,
            zoom = 1f,
            focusId = null,
        )
        val radiusOf = result.capsules.associate { it.id to it.orbitRadius }
        // 内圈早外圈晚：半径非降
        assertTrue(radiusOf.getValue("a") <= radiusOf.getValue("b"))
        assertTrue(radiusOf.getValue("b") <= radiusOf.getValue("c"))
        assertTrue(radiusOf.getValue("c") <= radiusOf.getValue("d"))
    }

    @Test
    fun orbitCapacityGrowsAndIdsStayOnTrack() {
        // 20 颗：轨道容量 6/9/12 → 三轨
        val items = (1..20).map { item("c$it", it.toLong()) }
        val result = TimeTrackLayout.layout(items, 0f, 0f, 300f, 1f, null)
        assertEquals(3, result.orbitRadii.size)
        assertEquals(20, result.capsules.size)
        // 轨距非降（内圈小）
        assertTrue(result.orbitRadii[0] < result.orbitRadii[1])
        assertTrue(result.orbitRadii[1] < result.orbitRadii[2])
    }

    // ---- 2. 自定义星图坐标覆盖 ----

    @Test
    fun customLayoutOverridesOrbitPosition() {
        val result = TimeTrackLayout.layout(
            items = listOf(item("free", 1L, layoutX = 0.5f, layoutY = -0.25f)),
            centerX = 100f,
            centerY = 200f,
            baseRadius = 300f,
            zoom = 1.5f,
            focusId = null,
        )
        val placed = result.capsules.single()
        assertEquals(100f + 0.5f * 300f * 1.5f, placed.x, 0.01f)
        assertEquals(200f + (-0.25f) * 300f * 1.5f, placed.y, 0.01f)
    }

    // ---- 3. 缩放边界 ----

    @Test
    fun zoomClampsToBounds() {
        assertEquals(0.6f, TimeTrackLayout.clampZoom(0.3f), 0.0001f)
        assertEquals(1f, TimeTrackLayout.clampZoom(1f), 0.0001f)
        assertEquals(2.5f, TimeTrackLayout.clampZoom(3f), 0.0001f)
    }

    @Test
    fun zoomScalesOrbitRadius() {
        val small = TimeTrackLayout.layout(listOf(item("a", 1L)), 0f, 0f, 300f, 0.6f, null)
        val big = TimeTrackLayout.layout(listOf(item("a", 1L)), 0f, 0f, 300f, 2.5f, null)
        assertTrue(big.capsules.single().orbitRadius > small.capsules.single().orbitRadius)
    }

    // ---- 4. 焦点标题显隐与邻近渐进 ----

    @Test
    fun focusTitleFullAlphaAndOthersDecay() {
        val result = TimeTrackLayout.layout(
            items = listOf(item("near", 1L), item("far", 2L)),
            centerX = 0f,
            centerY = 0f,
            baseRadius = 300f,
            zoom = 1f,
            focusId = "near",
        )
        val alphas = result.capsules.associate { it.id to it.titleAlpha }
        assertEquals(1f, alphas.getValue("near"), 0.0001f)
        val farAlpha = alphas.getValue("far")
        assertTrue(farAlpha < 1f)
        assertTrue(farAlpha >= TimeTrackLayout.NEAR_MIN_ALPHA)
    }

    @Test
    fun noFocusUsesDefaultAlpha() {
        val result = TimeTrackLayout.layout(
            items = listOf(item("a", 1L), item("b", 2L)),
            0f, 0f, 300f, 1f, null,
        )
        result.capsules.forEach {
            assertEquals(TimeTrackLayout.DEFAULT_TITLE_ALPHA, it.titleAlpha, 0.0001f)
        }
    }

    // ---- 5. 空列表安全 ----

    @Test
    fun emptyItemsReturnEmptyResult() {
        val result = TimeTrackLayout.layout(emptyList(), 0f, 0f, 300f, 1f, null)
        assertTrue(result.capsules.isEmpty())
        assertTrue(result.orbitRadii.isEmpty())
    }

    // ---- 6. 出口标准③：预算表常量化锚点断言 ----

    @Test
    fun mediumTierBackgroundParticlesIs40() {
        assertEquals(40, ParticleBudget.of(AnimationTier.MEDIUM).background)
        assertEquals(80, ParticleBudget.of(AnimationTier.HIGH).background)
        assertEquals(0, ParticleBudget.of(AnimationTier.LOW).background)
        assertEquals(350, ParticleBudget.of(AnimationTier.MEDIUM).unsealPeak)
        assertEquals(420, ParticleBudget.of(AnimationTier.MEDIUM).poolCapacity)
    }

    // ---- 7. 自动收拢：轨道容量推算与 fitZoom ----

    @Test
    fun orbitCountMatchesCapacityRule() {
        // 第 n 轨容量 = 6 + 3n：6 颗 1 轨、7 颗 2 轨（6+9）、16 颗 3 轨（6+9+12）
        assertEquals(1, TimeTrackLayout.orbitCountFor(6))
        assertEquals(2, TimeTrackLayout.orbitCountFor(7))
        assertEquals(3, TimeTrackLayout.orbitCountFor(16))
        assertEquals(1, TimeTrackLayout.orbitCountFor(0))
    }

    @Test
    fun fitZoomShrinksForMoreCapsulesAndStaysWithinBounds() {
        // 单轨无需收拢（0.98/0.30 > 1 → 钳到 1）
        assertEquals(1f, TimeTrackLayout.fitZoom(6), 0.0001f)
        // 胶囊越多 fitZoom 越小：密度维度（数量每 ×4 → zoom ×0.71）+ 半径适配取更严者
        assertTrue(TimeTrackLayout.fitZoom(16) < TimeTrackLayout.fitZoom(6))
        assertTrue(TimeTrackLayout.fitZoom(60) < TimeTrackLayout.fitZoom(16))
        // 密度公式锚点：24 = 6×4 → zoom = 4^-0.25 = 1/√2
        assertEquals(0.7071f, TimeTrackLayout.fitZoom(24), 0.001f)
        // 全程单调不增（密度单调降、半径适配随轨数单调降，取 min 仍单调）
        var prev = TimeTrackLayout.fitZoom(6)
        for (count in 7..100 step 7) {
            val zoom = TimeTrackLayout.fitZoom(count)
            assertTrue("count=$count 应单调不增", zoom <= prev + 0.0001f)
            prev = zoom
        }
        // 永不低于缩放下限、不高于 1
        assertTrue(TimeTrackLayout.fitZoom(10000) >= TimeTrackLayout.MIN_ZOOM)
        assertTrue(TimeTrackLayout.fitZoom(10000) <= 1f)
        // fitZoom 恰好让最外轨落回画布内：orbits 轨时 (0.30+0.22(orbits-1))×fit ≤ 0.98
        val count = 30 // orbits = 4（6+9+12+15）
        val orbits = TimeTrackLayout.orbitCountFor(count)
        val outer = 0.30f + 0.22f * (orbits - 1)
        assertTrue(outer * TimeTrackLayout.fitZoom(count) <= 0.98f + 0.0001f)
    }
}
