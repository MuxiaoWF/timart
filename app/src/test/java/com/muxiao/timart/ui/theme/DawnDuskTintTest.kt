package com.muxiao.timart.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * 晨昏暖色时段判定（N17）纯逻辑：窗口边界、跨窗口、非窗口（null）。
 */
class DawnDuskTintTest {

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun `dawn window inclusive start`() {
        assertEquals(DawnDuskTint.Phase.DAWN, DawnDuskTint.phaseOf(t(5)))
    }

    @Test
    fun `dawn window exclusive end`() {
        assertNull(DawnDuskTint.phaseOf(t(9)))
    }

    @Test
    fun `dusk window inclusive start`() {
        assertEquals(DawnDuskTint.Phase.DUSK, DawnDuskTint.phaseOf(t(17)))
    }

    @Test
    fun `dusk window exclusive end`() {
        assertNull(DawnDuskTint.phaseOf(t(21)))
    }

    @Test
    fun `midday and late night are null`() {
        assertNull(DawnDuskTint.phaseOf(t(12, 30)))
        assertNull(DawnDuskTint.phaseOf(t(0)))
        assertNull(DawnDuskTint.phaseOf(t(23, 59)))
        assertNull(DawnDuskTint.phaseOf(t(4, 59)))
    }

    @Test
    fun `minutes within window resolve to phase`() {
        assertEquals(DawnDuskTint.Phase.DAWN, DawnDuskTint.phaseOf(t(8, 59)))
        assertEquals(DawnDuskTint.Phase.DUSK, DawnDuskTint.phaseOf(t(20, 59)))
    }
}
