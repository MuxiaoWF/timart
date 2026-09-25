package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.context.BatteryInfo
import com.muxiao.timart.domain.context.BatteryProvider
import com.muxiao.timart.domain.context.ConditionContext
import com.muxiao.timart.domain.context.DependencyChecker
import com.muxiao.timart.domain.context.DependencyStatus
import com.muxiao.timart.domain.context.LocationProvider
import com.muxiao.timart.domain.context.NetworkProvider
import com.muxiao.timart.domain.context.StepHistoryProvider
import com.muxiao.timart.domain.context.StepProvider
import com.muxiao.timart.domain.context.TimeProvider
import com.muxiao.timart.domain.context.WeatherProvider
import com.muxiao.timart.domain.context.WifiProvider
import com.muxiao.timart.domain.context.WifiSsidInfo
import com.muxiao.timart.domain.context.WifiSsidState
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.ConditionGroup
import com.muxiao.timart.domain.model.unlock.GeoPoint
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.NetType
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.model.unlock.unitProgress
import com.muxiao.timart.domain.model.unlock.units
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 规则子群组单测（储备池暂缓项落地）：
 * units() 划分（含非法划分退化）、组语义判定（AND-of-OR 等）、unitProgress、预估器组归并。
 * 无组规则走旧扁平路径的等价性由 UnlockJudgeUseCaseTest 原有用例覆盖，此处不重复。
 */
class RuleSubGroupTest {

    private val judge = UnlockJudgeUseCase()
    private val passedDate = UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1))
    private val failedBattery = UnlockCondition.BatteryLevel(min = 90, max = null) // 默认电量 80%
    private val okBattery = UnlockCondition.BatteryLevel(min = 70, max = null)

    // ---- 极简假件 ----

    private class FakeTime : TimeProvider {
        override fun nowMillis(): Long = 1_786_000_000_000
        override fun today(): LocalDate = LocalDate.of(2026, 8, 12)
        override fun nowHour(): Int = 10
        override fun zoneId(): String = "Asia/Shanghai"
    }

    private class FakeBattery(private val level: Int) : BatteryProvider {
        override fun battery(): BatteryInfo = BatteryInfo(level, false)
    }

    private class FakeStep : StepProvider {
        override fun todaySteps(): Int = 0
    }

    private class FakeNetwork : NetworkProvider {
        override fun current(): NetType = NetType.WIFI
    }

    private class FakeWeather : WeatherProvider {
        override fun currentWeather(cityId: String) = null
    }

    private class FakeLocation : LocationProvider {
        override val foregroundOnly = false
        override fun isPermitted() = true
        override fun lastKnown(): GeoPoint? = null
        override fun speedMps(): Float? = null
    }

    private class FakeDependency : DependencyChecker {
        override fun statusOf(id: String): DependencyStatus = DependencyStatus.NOT_FOUND
    }

    private class FakeWifi : WifiProvider {
        override fun current(): WifiSsidInfo = WifiSsidInfo(WifiSsidState.NOT_CONNECTED)
    }

    private class FakeHistory : StepHistoryProvider {
        override fun daySteps(daysAgo: Int): Int? = null
        override fun stepsSince(sinceDate: LocalDate): Long? = null
    }

    private class FakeAlarm : com.muxiao.timart.domain.context.AlarmProvider {
        override fun nextAlarmMillis(): Long? = null
    }

    private class FakeSystemMode : com.muxiao.timart.domain.context.SystemModeProvider {
        override fun isPowerSave() = false
        override fun isSilentRinger() = false
        override fun isHeadphoneConnected() = false
        override fun isAirplaneModeOn() = false
        override fun isMusicPlaying() = false
    }

    private class FakeMotion : com.muxiao.timart.domain.context.MotionActivityProvider {
        override fun current(): com.muxiao.timart.domain.model.unlock.MotionKind? = null
    }

    private class FakeCompass : com.muxiao.timart.domain.context.CompassProvider {
        override fun headingDeg(): Float? = null
    }

    private class FakeAltitude : com.muxiao.timart.domain.context.AltitudeProvider {
        override fun altitudeMeters(): Double? = null
    }

    private class FakeUsage : com.muxiao.timart.domain.context.UsageStatsProvider {
        override fun openCount() = 0
        override fun openStreakDays() = 0
        override fun lastOpenMillis(): Long? = null
    }

    private class FakeMeta : com.muxiao.timart.domain.context.CapsuleMetaProvider {
        override fun capsuleCount() = 0
        override fun isRead(capsuleId: String) = false
        override fun viewCount(capsuleId: String) = 0
    }

    private class FakeAmbient : com.muxiao.timart.domain.context.AmbientLightProvider {
        override fun lux(): Float? = null
    }

    private fun context(batteryLevel: Int = 80) = ConditionContext(
        time = FakeTime(),
        battery = FakeBattery(batteryLevel),
        step = FakeStep(),
        network = FakeNetwork(),
        weather = FakeWeather(),
        location = FakeLocation(),
        dependency = FakeDependency(),
        wifi = FakeWifi(),
        stepHistory = FakeHistory(),
        alarm = FakeAlarm(),
        systemMode = FakeSystemMode(),
        motion = FakeMotion(),
        compass = FakeCompass(),
        altitude = FakeAltitude(),
        usage = FakeUsage(),
        meta = FakeMeta(),
        ambientLight = FakeAmbient(),
    )

    private fun capsule(rule: UnlockRule) = Capsule(
        id = "cap-1",
        title = "分组测试",
        contentCipher = null,
        createTimestamp = 0L,
        weather = null,
        unlockRule = rule,
        state = CapsuleState.LOCKED,
    )

    // ---- units() 划分 ----

    @Test
    fun unitsWithNoGroupsAreAllSingletons() {
        val rule = UnlockRule(LogicType.AND, listOf(passedDate, failedBattery))
        val unitList = units(rule)
        assertEquals(2, unitList.size)
        unitList.forEach { assertNull(it.name) }
    }

    @Test
    fun unitsKeepFlatOrderWithGroupExpandedAtFirstMember() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, failedBattery, okBattery),
            groups = listOf(ConditionGroup(name = "电量", logicType = LogicType.OR, indexes = listOf(1, 2))),
        )
        val unitList = units(rule)
        assertEquals(2, unitList.size)
        assertEquals(listOf(0), unitList[0].indexes)
        assertEquals(listOf(1, 2), unitList[1].indexes)
        assertEquals("电量", unitList[1].name)
    }

    @Test
    fun unitsWithOverlappingGroupsFallBackToSingletons() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, failedBattery, okBattery),
            groups = listOf(
                ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(0, 1)),
                ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(1, 2)),
            ),
        )
        val unitList = units(rule)
        assertEquals(3, unitList.size) // 重叠 → 全部退化为单例（等价旧扁平语义）
    }

    @Test
    fun unitsWithOutOfRangeIndexFallBackToSingletons() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, failedBattery),
            groups = listOf(ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(0, 5))),
        )
        assertEquals(2, units(rule).size)
    }

    @Test
    fun emptyGroupIsIgnored() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, failedBattery),
            groups = listOf(ConditionGroup(name = null, logicType = LogicType.OR, indexes = emptyList())),
        )
        assertEquals(2, units(rule).size)
    }

    // ---- 组语义判定 ----

    @Test
    fun andOfOrGroupNeedsOnlyOneGroupMemberSatisfied() {
        // （过去日期 或 电量≥90）且 电量≥70 —— 扁平 AND 会让 failedBattery 一票否决；分组后组内 OR 即满足
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, failedBattery, okBattery),
            groups = listOf(ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(0, 1))),
        )
        val result = judge.judge(capsule(rule), context(batteryLevel = 80))
        assertTrue(result.overallOk) // 组（0 或 1）：passedDate 满足即可；顶层 AND 的 okBattery 也满足
        assertTrue(result.items[1].reason != null || !result.items[1].satisfied)
    }

    @Test
    fun andOfOrGroupFailsWhenBothMembersFail() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(UnlockCondition.BatteryLevel(min = 95, max = null), okBattery),
            groups = listOf(ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(0, 1))),
        )
        // 组内两条都要 ≥95/≥70，电量 80：0 不满足；但 1（okBattery ≥70）满足 → 组通过
        // 改为组内都不过：电量 60
        val strict = UnlockRule(
            LogicType.AND,
            listOf(UnlockCondition.BatteryLevel(min = 95, max = null), UnlockCondition.BatteryLevel(min = 90, max = null)),
            groups = listOf(ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(0, 1))),
        )
        assertTrue(judge.judge(capsule(rule), context(batteryLevel = 80)).overallOk)
        assertFalse(judge.judge(capsule(strict), context(batteryLevel = 80)).overallOk)
    }

    @Test
    fun orOverAndGroups() {
        // 组1 = 过去日期 且 电量≥90（80 → 不满足）；组2 = 过去日期 且 电量≥70（满足）→ 顶层 OR 通过
        val rule = UnlockRule(
            LogicType.OR,
            listOf(passedDate, failedBattery, passedDate, okBattery),
            groups = listOf(
                ConditionGroup(name = null, logicType = LogicType.AND, indexes = listOf(0, 1)),
                ConditionGroup(name = null, logicType = LogicType.AND, indexes = listOf(2, 3)),
            ),
        )
        assertTrue(judge.judge(capsule(rule), context(batteryLevel = 80)).overallOk)
    }

    @Test
    fun atLeastTopLevelCountsUnitsNotConditions() {
        // 三个单元（一个两元 OR 组 + 两个单例），要求 ≥2 个单元满足：
        // 单元1（0 或 1）满足（passedDate）；单元2（2 失败）不满足；单元3（3 满足）→ 2/3 ≥ 2
        val rule = UnlockRule(
            LogicType.AT_LEAST,
            listOf(passedDate, failedBattery, failedBattery, passedDate),
            threshold = 2,
            groups = listOf(ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(0, 1))),
        )
        assertTrue(judge.judge(capsule(rule), context(batteryLevel = 80)).overallOk)

        // 阈值 3 个单元 → 不可达（只有 2 个单元满足）
        val strict = rule.copy(threshold = 3)
        assertFalse(judge.judge(capsule(strict), context(batteryLevel = 80)).overallOk)
    }

    @Test
    fun groupAtLeastThresholdCountsMembers() {
        // 组内 3 条任选 1（FixedDate 均已过期但 okBattery 也过 —— 电量 80 ≥ 70）→ 全部满足也行；
        // 收紧到 95 时 3 条全不过 → 组不满足 → 顶层 AND 不通过
        val rule = UnlockRule(
            LogicType.AND,
            listOf(failedBattery, UnlockCondition.BatteryLevel(min = 96, max = null), UnlockCondition.BatteryLevel(min = 97, max = null)),
            groups = listOf(ConditionGroup(name = null, logicType = LogicType.AT_LEAST, threshold = 1, indexes = listOf(0, 1, 2))),
        )
        // 80 < 90/96/97 → 组 0/3 → 不通过
        assertFalse(judge.judge(capsule(rule), context(batteryLevel = 80)).overallOk)
        // 电量 96 → 1 条满足 ≥ 1 → 通过
        assertTrue(judge.judge(capsule(rule), context(batteryLevel = 96)).overallOk)
    }

    // ---- unitProgress ----

    @Test
    fun unitProgressCountsGroupsAsSingleUnits() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, failedBattery, failedBattery, passedDate),
            groups = listOf(ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(0, 1))),
        )
        val (satisfied, total) = unitProgress(rule, listOf(true, false, false, true))
        // 单元 = [组(0,1) OR → 满足, 单例2 → 不满足, 单例3 → 满足]
        assertEquals(3, total)
        assertEquals(2, satisfied)
    }

    // ---- 预估器组归并 ----

    @Test
    fun estimatorMergesGroupEarliestTimes() {
        // 组 =（满 10 天 或 过去日期）：OR 取可估中最早 → 过去日期（锚定封存日 2026-01-01 之后任意时刻）
        // 顶层 AND 再加「满 100 天」→ 预估 = 两者最晚
        val created = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val capsule = Capsule(
            id = "cap-est",
            title = "预估",
            contentCipher = null,
            createTimestamp = created,
            weather = null,
            unlockRule = UnlockRule(
                LogicType.AND,
                listOf(
                    UnlockCondition.MinElapsedDay(days = 10),
                    passedDate,
                    UnlockCondition.MinElapsedDay(days = 100),
                ),
                groups = listOf(ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(0, 1))),
            ),
            state = CapsuleState.LOCKED,
        )
        val now = LocalDate.of(2026, 8, 12).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val estimate = EarliestUnlockEstimator.estimate(capsule, now, ZoneId.systemDefault())
        // 组下界 = min(满10天, 已过期) = 已过期（当天零点）；顶层 AND = max(组下界, 满100天) = 满100天零点
        val expected = LocalDate.of(2026, 1, 1).plusDays(100).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, estimate)
    }

    @Test
    fun estimatorReturnsNullWhenGroupHasNoEstimableMemberForAnd() {
        val created = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val capsule = Capsule(
            id = "cap-est2",
            title = "预估",
            contentCipher = null,
            createTimestamp = created,
            weather = null,
            unlockRule = UnlockRule(
                LogicType.AND,
                listOf(UnlockCondition.BatteryLevel(min = 50, max = null), passedDate),
                groups = listOf(ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(0, 1))),
            ),
            state = CapsuleState.LOCKED,
        )
        val now = LocalDate.of(2026, 8, 12).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // 组内电量条件不可估、日期条件可估 → OR 组下界仍存在；顶层 AND 只有一个单元 → 有值
        // 改为 AND 组内含不可估成员 → 整体 null（宁可不算不猜测）
        val strict = Capsule(
            id = "cap-est3",
            title = "预估",
            contentCipher = null,
            createTimestamp = created,
            weather = null,
            unlockRule = UnlockRule(
                LogicType.AND,
                listOf(UnlockCondition.BatteryLevel(min = 50, max = null), passedDate),
                groups = listOf(ConditionGroup(name = null, logicType = LogicType.AND, indexes = listOf(0, 1))),
            ),
            state = CapsuleState.LOCKED,
        )
        assertNull(EarliestUnlockEstimator.estimate(strict, now, ZoneId.systemDefault()))
        assertTrue(EarliestUnlockEstimator.estimate(capsule, now, ZoneId.systemDefault()) != null)
    }

    // ---- 判定与预估共享划分（RuleUnit 类型冒烟） ----

    @Test
    fun ruleUnitCarriesGroupMeta() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, failedBattery, okBattery),
            groups = listOf(ConditionGroup(name = "备用钥匙", logicType = LogicType.AT_LEAST, threshold = 2, indexes = listOf(0, 1, 2))),
        )
        val unit = units(rule).single()
        assertTrue(true)
        assertEquals("备用钥匙", unit.name)
        assertEquals(LogicType.AT_LEAST, unit.logicType)
        assertEquals(2, unit.threshold)
        assertEquals(listOf(0, 1, 2), unit.indexes)
    }
}
