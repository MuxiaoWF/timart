package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.ConditionKind
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.model.unlock.LogicType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 星库志统计（N6）纯逻辑单测：口径见 [StarLedgerStatsCalculator] 文档。
 * 条件类别分布 / 标签 Top / 最长等待 / 平均封存 / 开启率。
 */
class StarLedgerStatsTest {

    private fun capsule(
        id: String,
        state: CapsuleState,
        create: Long,
        unlock: Long? = null,
        conditions: List<UnlockCondition> = emptyList(),
        tags: List<String> = emptyList(),
    ) = Capsule(
        id = id,
        title = id,
        contentCipher = null,
        createTimestamp = create,
        unlockTimestamp = unlock,
        unlockRule = UnlockRule(LogicType.AND, conditions),
        state = state,
        tags = tags,
    )

    private val now = 1_700_000_000_000L

    @Test
    fun `empty library yields zeros and null aggregates`() {
        val stats = StarLedgerStatsCalculator.compute(emptyList(), 0, now)
        assertEquals(0, stats.totalSealed)
        assertEquals(0, stats.currentlyLocked)
        assertEquals(0, stats.currentlyUnlocked)
        assertEquals(0, stats.openRatePercent)
        assertNull(stats.longestWaitMs)
        assertNull(stats.avgSealToOpenMs)
        assertEquals(emptyMap<ConditionKind, Int>(), stats.kindCounts)
        assertEquals(emptyList<String>(), stats.topTags)
    }

    @Test
    fun `totalSealed counts capsules plus destroyed archives`() {
        val stats = StarLedgerStatsCalculator.compute(
            listOf(
                capsule("a", CapsuleState.LOCKED, now - 1000),
                capsule("b", CapsuleState.UNLOCKED, now - 1000, now - 500),
            ),
            destroyedCount = 3,
            now = now,
        )
        assertEquals(5, stats.totalSealed)
        assertEquals(3, stats.destroyedArchives)
    }

    @Test
    fun `open rate is unlocked over existing capsules`() {
        val stats = StarLedgerStatsCalculator.compute(
            listOf(
                capsule("a", CapsuleState.LOCKED, now - 1000),
                capsule("b", CapsuleState.UNLOCKED, now - 1000, now - 500),
                capsule("c", CapsuleState.UNLOCKED, now - 1000, now - 500),
            ),
            destroyedCount = 0,
            now = now,
        )
        // 2 / 3 → 66%（整数向下取整）
        assertEquals(66, stats.openRatePercent)
    }

    @Test
    fun `open rate ignores destroyed archives`() {
        val stats = StarLedgerStatsCalculator.compute(
            listOf(capsule("a", CapsuleState.UNLOCKED, now - 1000, now - 500)),
            destroyedCount = 9,
            now = now,
        )
        assertEquals(100, stats.openRatePercent)
    }

    @Test
    fun `longest wait uses unlock timestamp for opened capsules`() {
        val day = 24L * 60 * 60 * 1000
        val stats = StarLedgerStatsCalculator.compute(
            listOf(
                // LOCKED：now − create = 10 天
                capsule("a", CapsuleState.LOCKED, now - 10 * day),
                // UNLOCKED：unlock − create = 30 天（更长，应胜出）
                capsule("b", CapsuleState.UNLOCKED, now - 30 * day, now),
                // UNLOCKED 无 unlockTimestamp：不参与
                capsule("c", CapsuleState.UNLOCKED, now - 20 * day),
            ),
            destroyedCount = 0,
            now = now,
        )
        assertEquals(30 * day, stats.longestWaitMs)
    }

    @Test
    fun `avg seal-to-open averages over unlocked samples only`() {
        val day = 24L * 60 * 60 * 1000
        val stats = StarLedgerStatsCalculator.compute(
            listOf(
                capsule("a", CapsuleState.UNLOCKED, now - 10 * day, now - 5 * day),
                capsule("b", CapsuleState.UNLOCKED, now - 7 * day, now - 4 * day),
                // LOCKED 不参与均值
                capsule("c", CapsuleState.LOCKED, now - 100 * day),
            ),
            destroyedCount = 0,
            now = now,
        )
        assertEquals(4 * day, stats.avgSealToOpenMs)
    }

    @Test
    fun `kind counts flatten every condition across capsules`() {
        val stats = StarLedgerStatsCalculator.compute(
            listOf(
                capsule(
                    "a",
                    CapsuleState.LOCKED,
                    now - 1000,
                    conditions = listOf(
                        UnlockCondition.FixedDate(targetDate = java.time.LocalDate.of(2030, 1, 1)),
                        UnlockCondition.WeekDay(weekSet = setOf(java.time.DayOfWeek.MONDAY)),
                    ),
                ),
                capsule(
                    "b",
                    CapsuleState.LOCKED,
                    now - 1000,
                    conditions = listOf(
                        UnlockCondition.FixedDate(targetDate = java.time.LocalDate.of(2031, 6, 15)),
                    ),
                ),
            ),
            destroyedCount = 0,
            now = now,
        )
        assertEquals(3, stats.kindCounts[ConditionKind.TIME])
        assertEquals(null, stats.kindCounts[ConditionKind.DEVICE])
    }

    @Test
    fun `top tags sorted by count desc then name`() {
        val stats = StarLedgerStatsCalculator.compute(
            listOf(
                capsule("a", CapsuleState.LOCKED, now - 1000, tags = listOf("旅行", "家人", "旅行")),
                capsule("b", CapsuleState.LOCKED, now - 1000, tags = listOf("家人", "坚持")),
            ),
            destroyedCount = 0,
            now = now,
        )
        // 旅行 2 · 家人 2 · 坚持 1；同数按字典序
        assertEquals(listOf("家人", "旅行", "坚持"), stats.topTags)
    }

    @Test
    fun `top tags respect limit`() {
        val stats = StarLedgerStatsCalculator.compute(
            listOf(
                capsule(
                    "a",
                    CapsuleState.LOCKED,
                    now - 1000,
                    tags = listOf("t1", "t2", "t3", "t4", "t5", "t6"),
                ),
            ),
            destroyedCount = 0,
            now = now,
            topTagsLimit = 3,
        )
        assertEquals(3, stats.topTags.size)
    }

    @Test
    fun `capsule without unlock timestamp does not affect longest wait`() {
        val stats = StarLedgerStatsCalculator.compute(
            listOf(capsule("a", CapsuleState.UNLOCKED, now - 1000)),
            destroyedCount = 0,
            now = now,
        )
        assertNull(stats.longestWaitMs)
    }
}
