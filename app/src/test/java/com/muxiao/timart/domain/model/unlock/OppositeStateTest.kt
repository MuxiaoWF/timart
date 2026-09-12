package com.muxiao.timart.domain.model.unlock

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 状态类条件取反（oppositeState）：六个二元开关态条件取反翻转自身布尔字段；
 * 其余条件无唯一否定态返回 null。时间线"当前状态"附注依赖该语义。
 */
class OppositeStateTest {

    @Test
    fun `六个状态类条件取反翻转布尔字段`() {
        assertEquals(
            UnlockCondition.HeadphoneConnected(false),
            UnlockCondition.HeadphoneConnected(true).oppositeState(),
        )
        assertEquals(
            UnlockCondition.ChargingState(true),
            UnlockCondition.ChargingState(false).oppositeState(),
        )
        assertEquals(
            UnlockCondition.PowerSaveMode(false),
            UnlockCondition.PowerSaveMode(true).oppositeState(),
        )
        assertEquals(
            UnlockCondition.SilentMode(true),
            UnlockCondition.SilentMode(false).oppositeState(),
        )
        assertEquals(
            UnlockCondition.AirplaneMode(false),
            UnlockCondition.AirplaneMode(true).oppositeState(),
        )
        assertEquals(
            UnlockCondition.MusicPlaying(false),
            UnlockCondition.MusicPlaying(true).oppositeState(),
        )
    }

    @Test
    fun `非状态类条件无否定态`() {
        assertNull(UnlockCondition.GoldenHour.oppositeState())
        assertNull(UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1)).oppositeState())
        assertNull(UnlockCondition.StepCount(1000, 8000).oppositeState())
        assertNull(UnlockCondition.QuestionAnswer("c1", "问", "答").oppositeState())
    }
}
