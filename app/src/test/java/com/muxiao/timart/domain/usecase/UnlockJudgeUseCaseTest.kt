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
import com.muxiao.timart.domain.model.WeatherSnapshot
import com.muxiao.timart.domain.model.WeatherType
import com.muxiao.timart.domain.model.unlock.GeoPoint
import com.muxiao.timart.domain.model.unlock.MoonCalc
import com.muxiao.timart.domain.model.unlock.MoonPhaseKind
import com.muxiao.timart.domain.model.unlock.MotionKind
import com.muxiao.timart.domain.model.unlock.JudgeReasons
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.NetType
import com.muxiao.timart.domain.model.unlock.SunCalc
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.repository.CapsuleRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 判定引擎单测：AND/OR 合并、依赖三态、天气失败原因、Worker 跳过 GPS、权限拒绝不满足、全量判定持久化。
 * 全部使用假 ConditionContext（同步纯 Kotlin 接口组的前提）。
 */
class UnlockJudgeUseCaseTest {

    private val judge = UnlockJudgeUseCase()

    private val today: LocalDate = LocalDate.of(2026, 8, 12)

    // ---- 假实现 ----

    private class FakeTime(
        private val fixedToday: LocalDate,
        private val hour: Int = 10,
    ) : TimeProvider {
        override fun nowMillis(): Long = 1_786_000_000_000
        override fun today(): LocalDate = fixedToday
        override fun nowHour(): Int = hour
    }

    private class FakeBattery(private val info: BatteryInfo) : BatteryProvider {
        override fun battery(): BatteryInfo = info
    }

    private class FakeStep(private val steps: Int?) : StepProvider {
        override fun todaySteps(): Int? = steps
    }

    private class FakeNetwork(private val net: NetType) : NetworkProvider {
        override fun current(): NetType = net
    }

    private class FakeWeather(private val snapshot: WeatherSnapshot?) : WeatherProvider {
        override fun currentWeather(cityId: String): WeatherSnapshot? = snapshot
    }

    private class FakeLocation(
        private val permitted: Boolean = true,
        fgOnly: Boolean = false,
        private val point: GeoPoint? = null,
    ) : LocationProvider {
        override val foregroundOnly: Boolean = fgOnly
        override fun isPermitted(): Boolean = permitted
        override fun lastKnown(): GeoPoint? = point
    }

    private class FakeDependency(private val statuses: Map<String, DependencyStatus>) : DependencyChecker {
        override fun statusOf(id: String): DependencyStatus = statuses[id] ?: DependencyStatus.NOT_FOUND
    }

    private class FakeWifi(private val info: WifiSsidInfo) : WifiProvider {
        override fun current(): WifiSsidInfo = info
    }

    private class FakeHistory(private val byDaysAgo: Map<Int, Int>) : StepHistoryProvider {
        override fun daySteps(daysAgo: Int): Int? = byDaysAgo[daysAgo]
    }

    private class FakeAlarm(private val next: Long?) : com.muxiao.timart.domain.context.AlarmProvider {
        override fun nextAlarmMillis(): Long? = next
    }

    private class FakeSystemMode(
        private val powerSave: Boolean = false,
        private val silent: Boolean = false,
        private val headphone: Boolean = false,
    ) : com.muxiao.timart.domain.context.SystemModeProvider {
        override fun isPowerSave(): Boolean = powerSave
        override fun isSilentRinger(): Boolean = silent
        override fun isHeadphoneConnected(): Boolean = headphone
    }

    private class FakeMotion(private val kind: MotionKind?) :
        com.muxiao.timart.domain.context.MotionActivityProvider {
        override fun current(): MotionKind? = kind
    }

    private class FakeCompass(private val deg: Float?) : com.muxiao.timart.domain.context.CompassProvider {
        override fun headingDeg(): Float? = deg
    }

    private class FakeAltitude(private val meters: Double?) : com.muxiao.timart.domain.context.AltitudeProvider {
        override fun altitudeMeters(): Double? = meters
    }

    private class FakeUsage(
        private val count: Int = 0,
        private val streak: Int = 0,
        private val lastOpen: Long? = null,
    ) : com.muxiao.timart.domain.context.UsageStatsProvider {
        override fun openCount(): Int = count
        override fun openStreakDays(): Int = streak
        override fun lastOpenMillis(): Long? = lastOpen
    }

    private class FakeMeta(private val total: Int) : com.muxiao.timart.domain.context.CapsuleMetaProvider {
        override fun capsuleCount(): Int = total
    }

    private fun context(
        time: TimeProvider = FakeTime(today),
        battery: BatteryProvider = FakeBattery(BatteryInfo(80, false)),
        step: StepProvider = FakeStep(5000),
        network: NetworkProvider = FakeNetwork(NetType.WIFI),
        weather: WeatherProvider = FakeWeather(
            WeatherSnapshot("city-1", "东京", WeatherType.RAIN, 21.5, 0L),
        ),
        location: LocationProvider = FakeLocation(point = GeoPoint(35.6812, 139.7671)),
        dependency: DependencyChecker = FakeDependency(emptyMap()),
        wifi: WifiProvider = FakeWifi(WifiSsidInfo(WifiSsidState.NOT_CONNECTED)),
        stepHistory: StepHistoryProvider = FakeHistory(emptyMap()),
        alarm: com.muxiao.timart.domain.context.AlarmProvider = FakeAlarm(2_000_000_000_000L),
        systemMode: com.muxiao.timart.domain.context.SystemModeProvider = FakeSystemMode(),
        motion: com.muxiao.timart.domain.context.MotionActivityProvider = FakeMotion(
            MotionKind.STILL,
        ),
        compass: com.muxiao.timart.domain.context.CompassProvider = FakeCompass(90f),
        altitude: com.muxiao.timart.domain.context.AltitudeProvider = FakeAltitude(50.0),
        usage: com.muxiao.timart.domain.context.UsageStatsProvider = FakeUsage(5, 3, 1_000_000_000_000L),
        meta: com.muxiao.timart.domain.context.CapsuleMetaProvider = FakeMeta(4),
    ) = ConditionContext(
        time, battery, step, network, weather, location, dependency, wifi, stepHistory,
        alarm, systemMode, motion, compass, altitude, usage, meta,
    )

    private fun capsule(
        rule: UnlockRule,
        dependCapsuleId: String? = null,
        state: CapsuleState = CapsuleState.LOCKED,
        snapshotCityId: String? = "city-1",
        id: String = "cap-1",
    ) = Capsule(
        id = id,
        title = "测试胶囊",
        contentCipher = null,
        createTimestamp = 0L,
        weather = snapshotCityId?.let {
            WeatherSnapshot(it, "东京", WeatherType.CLEAR, 20.0, 0L)
        },
        unlockRule = rule,
        state = state,
        dependCapsuleId = dependCapsuleId,
    )

    private class FakeRepo(seed: List<Capsule>) : CapsuleRepository {
        val items = seed.toMutableList()
        val unlockedIds = mutableListOf<String>()

        override fun observeAll(): Flow<List<Capsule>> = flow { emit(items.toList()) }
        override fun observeById(id: String): Flow<Capsule?> = flow { emit(items.find { it.id == id }) }
        override fun byIdSync(id: String): Capsule? = items.find { it.id == id }
        override suspend fun allSync(): List<Capsule> = items.toList()
        override suspend fun allLockedSync(): List<Capsule> = items.filter { it.state == CapsuleState.LOCKED }
        override suspend fun insert(capsule: Capsule) { items.add(capsule) }
        override suspend fun update(capsule: Capsule) = Unit
        override suspend fun updateState(id: String, state: CapsuleState, atMillis: Long?) {
            unlockedIds.add(id)
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(state = state, unlockTimestamp = atMillis)
        }
        override suspend fun updateLayout(id: String, layoutX: Float?, layoutY: Float?) = Unit
        override suspend fun updateAutoDestroyAfterRead(id: String, value: Boolean) = Unit
        override suspend fun updateUnlockRule(id: String, rule: UnlockRule) = Unit
        override suspend fun delete(id: String) = Unit
    }

    // ---- 1. AND 合并 ----

    @Test
    fun andMergeRequiresAllSatisfied() {
        val passedDate = UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1))
        val failedBattery = UnlockCondition.BatteryLevel(min = 90, max = null) // 当前电量 80%
        val rule = UnlockRule(LogicType.AND, listOf(passedDate, failedBattery))

        val partial = judge.judge(capsule(rule), context())
        assertFalse(partial.overallOk)
        assertFalse(partial.items[1].satisfied)

        val allPass = judge.judge(
            capsule(rule),
            context(battery = FakeBattery(BatteryInfo(95, false))),
        )
        assertTrue(allPass.overallOk)
    }

    @Test
    fun andMergeWithEmptyConditionsPasses() {
        val rule = UnlockRule(LogicType.AND, emptyList())
        assertTrue(judge.judge(capsule(rule), context()).overallOk)
    }

    // ---- 2. OR 合并 ----

    @Test
    fun orMergeNeedsAnySatisfied() {
        val passedDate = UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1))
        val failedBattery = UnlockCondition.BatteryLevel(min = 90, max = null)
        val rule = UnlockRule(LogicType.OR, listOf(passedDate, failedBattery))

        assertTrue(judge.judge(capsule(rule), context()).overallOk)

        val nonePass = UnlockRule(LogicType.OR, listOf(failedBattery))
        assertFalse(judge.judge(capsule(nonePass), context()).overallOk)
    }

    // ---- 3. 依赖三态 ----

    @Test
    fun dependencyStatesGateOverall() {
        val rule = UnlockRule(LogicType.AND, listOf(UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1))))

        val lockedDep = judge.judge(
            capsule(rule, dependCapsuleId = "dep"),
            context(dependency = FakeDependency(mapOf("dep" to DependencyStatus.LOCKED))),
        )
        assertFalse(lockedDep.overallOk)
        assertFalse(lockedDep.dependencyOk)
        assertEquals(DependencyStatus.LOCKED, lockedDep.dependencyStatus)

        val destroyedDep = judge.judge(
            capsule(rule, dependCapsuleId = "dep"),
            context(dependency = FakeDependency(mapOf("dep" to DependencyStatus.DESTROYED))),
        )
        assertFalse(destroyedDep.overallOk)
        assertEquals(DependencyStatus.DESTROYED, destroyedDep.dependencyStatus)
        assertEquals(
            "依赖胶囊已销毁，此胶囊无法再解锁",
            judge.dependencyReason(destroyedDep.dependencyStatus),
        )

        val notFoundDep = judge.judge(
            capsule(rule, dependCapsuleId = "dep"),
            context(dependency = FakeDependency(emptyMap())),
        )
        assertFalse(notFoundDep.overallOk)
        assertEquals(DependencyStatus.NOT_FOUND, notFoundDep.dependencyStatus)

        val unlockedDep = judge.judge(
            capsule(rule, dependCapsuleId = "dep"),
            context(dependency = FakeDependency(mapOf("dep" to DependencyStatus.UNLOCKED))),
        )
        assertTrue(unlockedDep.overallOk)
        assertTrue(unlockedDep.dependencyOk)
    }

    // ---- 4. 天气失败原因 ----

    @Test
    fun weatherFailureHasFixedReason() {
        val rule = UnlockRule(LogicType.AND, listOf(UnlockCondition.WeatherType(setOf("RAIN"))))
        val result = judge.judge(capsule(rule), context(weather = FakeWeather(null)))

        assertFalse(result.overallOk)
        assertFalse(result.items[0].satisfied)
        assertEquals(JudgeReasons.WEATHER_FAILED, result.items[0].reason)

        // 城市命中但天气类型不匹配 → 不满足但非"获取失败"原因
        val clearWeather = context(
            weather = FakeWeather(WeatherSnapshot("city-1", "东京", WeatherType.CLEAR, 25.0, 0L)),
        )
        val mismatch = judge.judge(capsule(rule), clearWeather)
        assertFalse(mismatch.overallOk)
        assertNull(mismatch.items[0].reason)

        // 无快照城市 → 专用原因
        val noSnapshot = judge.judge(
            capsule(rule, snapshotCityId = null),
            context(),
        )
        assertEquals(JudgeReasons.WEATHER_NO_SNAPSHOT, noSnapshot.items[0].reason)
    }

    // ---- 5. Worker 跳过 GPS ----

    @Test
    fun workerSkipsGpsCondition() {
        val gps = UnlockCondition.GpsLocation(35.6812, 139.7671, radiusMeter = 300)
        val passedTime = UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1))

        // 后台：GPS skipped，按不满足参与 AND 合并
        val workerCtx = context(
            location = FakeLocation(fgOnly = true, point = GeoPoint(35.6812, 139.7671)),
        )
        val andResult = judge.judge(capsule(UnlockRule(LogicType.AND, listOf(gps, passedTime))), workerCtx)
        assertFalse(andResult.overallOk)
        assertTrue(andResult.items[0].skipped)
        assertEquals(JudgeReasons.GPS_BACKGROUND, andResult.items[0].reason)

        // 同一上下文 OR 合并下，skipped 不阻断另一条满足
        val orResult = judge.judge(capsule(UnlockRule(LogicType.OR, listOf(gps, passedTime))), workerCtx)
        assertTrue(orResult.overallOk)

        // 前台且坐标命中 → 满足
        val foregroundCtx = context(location = FakeLocation(point = GeoPoint(35.6812, 139.7671)))
        val fgResult = judge.judge(capsule(UnlockRule(LogicType.AND, listOf(gps))), foregroundCtx)
        assertTrue(fgResult.overallOk)
    }

    // ---- 6. 权限拒绝 / 硬件缺失 ----

    @Test
    fun permissionDeniedMakesConditionUnsatisfied() {
        val gps = UnlockCondition.GpsLocation(35.6812, 139.7671, radiusMeter = 300)
        val denied = judge.judge(
            capsule(UnlockRule(LogicType.AND, listOf(gps))),
            context(location = FakeLocation(permitted = false, point = GeoPoint(35.6812, 139.7671))),
        )
        assertFalse(denied.items[0].satisfied)
        assertEquals(JudgeReasons.GPS_NO_PERMISSION, denied.items[0].reason)
        assertFalse(denied.overallOk)
    }

    @Test
    fun missingStepHardwareIsUnsatisfied() {
        val steps = UnlockCondition.StepCount(minTodayStep = 1000)
        val result = judge.judge(
            capsule(UnlockRule(LogicType.AND, listOf(steps))),
            context(step = FakeStep(null)),
        )
        assertFalse(result.items[0].satisfied)
        assertEquals(JudgeReasons.STEP_UNAVAILABLE, result.items[0].reason)
    }

    @Test
    fun batteryUnknownIsUnsatisfiedWithReason() {
        val battery = UnlockCondition.BatteryLevel(min = 10, max = null)
        val result = judge.judge(
            capsule(UnlockRule(LogicType.AND, listOf(battery))),
            context(battery = FakeBattery(BatteryInfo(null, false))),
        )
        assertEquals(JudgeReasons.BATTERY_UNKNOWN, result.items[0].reason)
    }

    // ---- 6b. 气温阈值 ----

    @Test
    fun temperatureThresholdCoversBoundedAndFailedPaths() {
        val temp = UnlockCondition.TemperatureThreshold(minC = 20.0, maxC = 25.0)
        val result = judge.judge(capsule(UnlockRule(LogicType.AND, listOf(temp))), context())
        assertTrue(result.items[0].satisfied)
        assertNull(result.items[0].reason)

        // 气温在区间外 → 不满足但无"失败"原因
        val cold = context(weather = FakeWeather(WeatherSnapshot("city-1", "东京", WeatherType.SNOW, 2.0, 0L)))
        val mismatch = judge.judge(capsule(UnlockRule(LogicType.AND, listOf(temp))), cold)
        assertFalse(mismatch.items[0].satisfied)
        assertNull(mismatch.items[0].reason)

        // 天气获取失败 → weatherFailed；无快照城市 → weatherNoSnapshot
        val failed = judge.judge(
            capsule(UnlockRule(LogicType.AND, listOf(temp))),
            context(weather = FakeWeather(null)),
        )
        assertEquals(JudgeReasons.WEATHER_FAILED, failed.items[0].reason)
        val noSnapshot = judge.judge(
            capsule(UnlockRule(LogicType.AND, listOf(temp)), snapshotCityId = null),
            context(),
        )
        assertEquals(JudgeReasons.WEATHER_NO_SNAPSHOT, noSnapshot.items[0].reason)
    }

    // ---- 6c. Wi-Fi SSID ----

    @Test
    fun ssidMatchCoversAllWifiStates() {
        val ssid = UnlockCondition.SsidMatch(setOf("MyHome"))

        val home = context(wifi = FakeWifi(WifiSsidInfo(WifiSsidState.CONNECTED, "MyHome")))
        assertTrue(judge.judge(capsule(UnlockRule(LogicType.AND, listOf(ssid))), home).overallOk)

        val other = context(wifi = FakeWifi(WifiSsidInfo(WifiSsidState.CONNECTED, "Office")))
        val mismatch = judge.judge(capsule(UnlockRule(LogicType.AND, listOf(ssid))), other)
        assertFalse(mismatch.items[0].satisfied)
        assertNull(mismatch.items[0].reason)

        val noPerm = context(wifi = FakeWifi(WifiSsidInfo(WifiSsidState.NO_PERMISSION)))
        assertEquals(JudgeReasons.SSID_NO_PERMISSION, judge.judge(capsule(UnlockRule(LogicType.AND, listOf(ssid))), noPerm).items[0].reason)

        val locationOff = context(wifi = FakeWifi(WifiSsidInfo(WifiSsidState.NO_LOCATION_SERVICE)))
        assertEquals(JudgeReasons.SSID_LOCATION_OFF, judge.judge(capsule(UnlockRule(LogicType.AND, listOf(ssid))), locationOff).items[0].reason)

        val disconnected = context(wifi = FakeWifi(WifiSsidInfo(WifiSsidState.NOT_CONNECTED)))
        assertEquals(JudgeReasons.SSID_NOT_CONNECTED, judge.judge(capsule(UnlockRule(LogicType.AND, listOf(ssid))), disconnected).items[0].reason)
    }

    // ---- 6d. 连续步数 streak ----

    @Test
    fun streakSatisfiedWhenHistoryComplete() {
        // days=3：昨日与前日均 ≥ 6000，今日 7000 → 满足
        val streak = UnlockCondition.StepStreak(days = 3, goal = 6000)
        val ctx = context(
            step = FakeStep(7000),
            stepHistory = FakeHistory(mapOf(1 to 6500, 2 to 6000)),
        )
        assertTrue(judge.judge(capsule(UnlockRule(LogicType.AND, listOf(streak))), ctx).overallOk)

        // days=1：只看今日
        val single = UnlockCondition.StepStreak(days = 1, goal = 6000)
        val singleCtx = context(step = FakeStep(7000), stepHistory = FakeHistory(emptyMap()))
        assertTrue(judge.judge(capsule(UnlockRule(LogicType.AND, listOf(single))), singleCtx).overallOk)
    }

    @Test
    fun streakFailsClosedOnMissingHistory() {
        val streak = UnlockCondition.StepStreak(days = 3, goal = 6000)
        // 第 2 天缺记录 → fail-closed，不满足且给原因
        val ctx = context(
            step = FakeStep(7000),
            stepHistory = FakeHistory(mapOf(1 to 6500)),
        )
        val result = judge.judge(capsule(UnlockRule(LogicType.AND, listOf(streak))), ctx)
        assertFalse(result.items[0].satisfied)
        assertEquals(JudgeReasons.STREAK_NO_RECORD, result.items[0].reason)

        // 今日未达标 → 不满足但无专用原因
        val lowToday = context(
            step = FakeStep(5000),
            stepHistory = FakeHistory(mapOf(1 to 6500, 2 to 6500)),
        )
        val low = judge.judge(capsule(UnlockRule(LogicType.AND, listOf(streak))), lowToday)
        assertFalse(low.items[0].satisfied)
        assertNull(low.items[0].reason)
    }

    @Test
    fun streakUnavailableWithoutStepSensor() {
        val streak = UnlockCondition.StepStreak(days = 2, goal = 6000)
        val result = judge.judge(
            capsule(UnlockRule(LogicType.AND, listOf(streak))),
            context(step = FakeStep(null)),
        )
        assertEquals(JudgeReasons.STEP_UNAVAILABLE, result.items[0].reason)
    }

    // ---- 7. 非 LOCKED 短路 ----

    @Test
    fun nonLockedCapsuleShortCircuits() {
        val rule = UnlockRule(LogicType.AND, listOf(UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1))))
        val unlocked = judge.judge(capsule(rule, state = CapsuleState.UNLOCKED), context())
        assertFalse(unlocked.overallOk)
        assertTrue(unlocked.items.isEmpty())

        val destroyed = judge.judge(capsule(rule, state = CapsuleState.DESTROYED), context())
        assertFalse(destroyed.overallOk)
    }

    // ---- 8. judgeAllLocked：持久化 + 回调 ----

    @Test
    fun judgeAllLockedPersistsAndInvokesCallback() = runBlocking {
        val satisfiable = capsule(
            UnlockRule(LogicType.AND, listOf(UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1)))),
            id = "cap-ok",
        )
        val unsatisfiable = capsule(
            UnlockRule(LogicType.AND, listOf(UnlockCondition.BatteryLevel(min = 99, max = null))),
            id = "cap-no",
        )
        val repo = FakeRepo(listOf(satisfiable, unsatisfiable))
        val unlockedTitles = mutableListOf<String>()

        judge.judgeAllLocked(context(), repo) { unlockedTitles.add(it.title) }

        assertEquals(listOf("cap-ok"), repo.unlockedIds)
        assertEquals(1, unlockedTitles.size)
        assertEquals(CapsuleState.UNLOCKED, repo.items.first { it.id == "cap-ok" }.state)
        assertTrue(repo.items.first { it.id == "cap-ok" }.unlockTimestamp!! > 0)
    }

    // ================= 扩展条件 =================

    private fun judgeOne(condition: UnlockCondition, ctx: ConditionContext, answers: Map<String, String> = emptyMap()) =
        judge.judge(
            capsule(UnlockRule(LogicType.AND, listOf(condition))),
            ctx,
            challengeAnswers = answers,
        ).items.single()

    @Test
    fun `扩展 - 每月N号与每年纪念日`() {
        val ctx = context()
        assertTrue(judgeOne(UnlockCondition.MonthlyDay(12), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.MonthlyDay(13), ctx).satisfied)
        assertTrue(judgeOne(UnlockCondition.YearlyDate(8, 12), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.YearlyDate(8, 13), ctx).satisfied)
    }

    @Test
    fun `扩展 - 满N分钟按创建时刻起算`() {
        // createTimestamp = 0，nowMillis 固定
        val ok = judgeOne(UnlockCondition.MinElapsedMinutes(60), context())
        assertTrue(ok.satisfied)
    }

    @Test
    fun `扩展 - 离开某地是到达某地的反向`() {
        // 现有假定位 = 东京站（35.6812, 139.7671）
        val ctx = context()
        val inside = judgeOne(UnlockCondition.AwayFromLocation(35.6812, 139.7671, 2000), ctx)
        assertFalse(inside.satisfied)
        val away = judgeOne(UnlockCondition.AwayFromLocation(39.9042, 116.4074, 2000), ctx)
        assertTrue(away.satisfied)
    }

    @Test
    fun `扩展 - 闹钟之前`() {
        val before = judgeOne(UnlockCondition.BeforeNextAlarm, context(alarm = FakeAlarm(99_000_000_000_000L)))
        assertTrue(before.satisfied)
        val after = judgeOne(UnlockCondition.BeforeNextAlarm, context(alarm = FakeAlarm(1_000L)))
        assertFalse(after.satisfied)
        val none = judgeOne(UnlockCondition.BeforeNextAlarm, context(alarm = FakeAlarm(null)))
        assertEquals(
            JudgeReasons.forLang(com.muxiao.timart.domain.model.Lang.ZH_HANS).alarmNotSet,
            none.reason,
        )
    }

    @Test
    fun `扩展 - 省电静音耳机匹配`() {
        val ctx = context(systemMode = FakeSystemMode(powerSave = true, silent = true, headphone = false))
        assertTrue(judgeOne(UnlockCondition.PowerSaveMode(true), ctx).satisfied)
        assertTrue(judgeOne(UnlockCondition.SilentMode(true), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.HeadphoneConnected(true), ctx).satisfied)
        assertTrue(judgeOne(UnlockCondition.HeadphoneConnected(false), ctx).satisfied)
    }

    @Test
    fun `扩展 - 运动状态与指南针与海拔`() {
        val ctx = context(
            motion = FakeMotion(MotionKind.WALKING),
            compass = FakeCompass(5f),
            altitude = FakeAltitude(1200.0),
        )
        assertTrue(judgeOne(UnlockCondition.MotionActivity(setOf(MotionKind.WALKING, MotionKind.STILL)), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.MotionActivity(setOf(MotionKind.STILL)), ctx).satisfied)
        // 5° 距离 0° 差 5，容差 30 内
        assertTrue(judgeOne(UnlockCondition.CompassHeading(0, 30), ctx).satisfied)
        // 5° 与 180° 差 175 > 30
        assertFalse(judgeOne(UnlockCondition.CompassHeading(180, 30), ctx).satisfied)
        assertTrue(judgeOne(UnlockCondition.AltitudeRange(1000.0, 2000.0), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.AltitudeRange(0.0, 500.0), ctx).satisfied)
        val noSensor = context(altitude = FakeAltitude(null))
        assertEquals(
            JudgeReasons.forLang(com.muxiao.timart.domain.model.Lang.ZH_HANS).altitudeUnavailable,
            judgeOne(UnlockCondition.AltitudeRange(null, null), noSensor).reason,
        )
    }

    @Test
    fun `扩展 - 使用统计三条件`() {
        val ctx = context(usage = FakeUsage(count = 30, streak = 8, lastOpen = 1_786_000_000_000L - 10L * 86_400_000L))
        assertTrue(judgeOne(UnlockCondition.OpenCountAtLeast(30), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.OpenCountAtLeast(31), ctx).satisfied)
        assertTrue(judgeOne(UnlockCondition.OpenStreak(8), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.OpenStreak(9), ctx).satisfied)
        // lastOpen 距 now(1_786_000_000_000) 10 天 → 超过 9 天满足，11 天不满足
        assertTrue(judgeOne(UnlockCondition.DaysSinceLastOpen(9), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.DaysSinceLastOpen(11), ctx).satisfied)
    }

    @Test
    fun `扩展 - 胶囊数量与联动状态`() {
        val ctx = context(
            meta = FakeMeta(4),
            dependency = FakeDependency(mapOf("other" to DependencyStatus.UNLOCKED, "gone" to DependencyStatus.DESTROYED)),
        )
        assertTrue(judgeOne(UnlockCondition.CapsuleCountAtLeast(4), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.CapsuleCountAtLeast(5), ctx).satisfied)
        assertTrue(judgeOne(UnlockCondition.OtherCapsuleUnlocked("other"), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.OtherCapsuleUnlocked("gone"), ctx).satisfied)
        assertTrue(judgeOne(UnlockCondition.OtherCapsuleDestroyed("gone"), ctx).satisfied)
    }

    @Test
    fun `扩展 - 问答与谜题挑战应答`() {
        val qa = UnlockCondition.QuestionAnswer("c1", "我们初次见面的城市?", "武汉")
        val ctx = context()
        // 无应答 fail-closed
        assertFalse(judgeOne(qa, ctx).satisfied)
        // 忽略首尾空白（trim 后 equals）
        assertTrue(judgeOne(qa, ctx, answers = mapOf("c1" to " 武汉 ")).satisfied)
        assertTrue(judgeOne(qa, ctx, answers = mapOf("c1" to "武汉")).satisfied)
        val wrong = judgeOne(qa, ctx, answers = mapOf("c1" to "东京"))
        assertFalse(wrong.satisfied)

        val hash = UnlockJudgeUseCase.sha256Hex("answer42")
        val puzzle = UnlockCondition.PuzzleAnswer("c2", "谜面", hash)
        assertTrue(judgeOne(puzzle, ctx, answers = mapOf("c2" to " ANSWER42 ")).satisfied)
        assertFalse(judgeOne(puzzle, ctx, answers = mapOf("c2" to "wrong")).satisfied)
    }

    @Test
    fun `扩展 - 感官挑战与NFC按DONE应答`() {
        val ctx = context()
        val shake = UnlockCondition.ShakeCount("c3", 10)
        assertFalse(judgeOne(shake, ctx).satisfied)
        assertTrue(judgeOne(shake, ctx, answers = mapOf("c3" to "DONE")).satisfied)
        val nfc = UnlockCondition.NfcTap("c4")
        assertTrue(judgeOne(nfc, ctx, answers = mapOf("c4" to "DONE")).satisfied)
    }

    @Test
    fun `天文 - 月相纯函数与已知日期`() {
        // 2026-08-12：相位只校验落在 8 相之一且相邻两次计算稳定
        val a = MoonCalc.phaseOf(LocalDate.of(2026, 8, 12))
        val b = MoonCalc.phaseOf(LocalDate.of(2026, 8, 12))
        assertEquals(a, b)
        // 2000-01-06 是基准新月日
        assertEquals(MoonPhaseKind.NEW, MoonCalc.phaseOf(LocalDate.of(2000, 1, 6)))
    }

    @Test
    fun `天文 - 东京夏至日出日落合理且白天判定`() {
        // 东京站 2026-06-21 夏至：日出 ~19:45Z、日落 ~10:40Z（UTC 计算值应在合理区间）
        val times = SunCalc.sunTimesUtcMillis(35.6812, 139.7671, LocalDate.of(2026, 6, 21))!!
        val (rise, set) = times
        assertTrue(set > rise)
        assertTrue(set - rise < 86_400_000L / 2 + 3_600_000L) // 夏至昼长 < 15h（东京纬度）
        // 2026-01-01（北半球冬季昼短）昼长 < 夏至
        val winter = SunCalc.sunTimesUtcMillis(35.6812, 139.7671, LocalDate.of(2026, 1, 1))!!
        assertTrue(winter.second - winter.first < set - rise)
    }
}
