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
import com.muxiao.timart.domain.model.unlock.LunarCalendar
import com.muxiao.timart.domain.model.unlock.MeteorShowerKind
import com.muxiao.timart.domain.model.unlock.MoonCalc
import com.muxiao.timart.domain.model.unlock.MoonPhaseKind
import com.muxiao.timart.domain.model.unlock.MotionKind
import com.muxiao.timart.domain.model.unlock.JudgeReasons
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.NetType
import com.muxiao.timart.domain.model.unlock.SunCalc
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.model.unlock.WeatherMetricKind
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
        private val zone: String = "Asia/Shanghai",
    ) : TimeProvider {
        override fun nowMillis(): Long = 1_786_000_000_000
        override fun today(): LocalDate = fixedToday
        override fun nowHour(): Int = hour
        override fun zoneId(): String = zone
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
        private val speedMpsValue: Float? = null,
    ) : LocationProvider {
        override val foregroundOnly: Boolean = fgOnly
        override fun isPermitted(): Boolean = permitted
        override fun lastKnown(): GeoPoint? = point
        override fun speedMps(): Float? = speedMpsValue
    }

    private class FakeAmbientLight(private val lux: Float?) : com.muxiao.timart.domain.context.AmbientLightProvider {
        override fun lux(): Float? = lux
    }

    private class FakeDependency(private val statuses: Map<String, DependencyStatus>) : DependencyChecker {
        override fun statusOf(id: String): DependencyStatus = statuses[id] ?: DependencyStatus.NOT_FOUND
    }

    private class FakeWifi(private val info: WifiSsidInfo) : WifiProvider {
        override fun current(): WifiSsidInfo = info
    }

    private class FakeHistory(
        private val byDaysAgo: Map<Int, Int>,
        private val sinceTotal: Long? = null,
    ) : StepHistoryProvider {
        override fun daySteps(daysAgo: Int): Int? = byDaysAgo[daysAgo]
        override fun stepsSince(sinceDate: LocalDate): Long? = sinceTotal
    }

    private class FakeAlarm(private val next: Long?) : com.muxiao.timart.domain.context.AlarmProvider {
        override fun nextAlarmMillis(): Long? = next
    }

    private class FakeSystemMode(
        private val powerSave: Boolean = false,
        private val silent: Boolean = false,
        private val headphone: Boolean = false,
        private val airplane: Boolean = false,
        private val music: Boolean = false,
    ) : com.muxiao.timart.domain.context.SystemModeProvider {
        override fun isPowerSave(): Boolean = powerSave
        override fun isSilentRinger(): Boolean = silent
        override fun isHeadphoneConnected(): Boolean = headphone
        override fun isAirplaneModeOn(): Boolean = airplane
        override fun isMusicPlaying(): Boolean = music
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
        private val minutesToday: Long? = null,
    ) : com.muxiao.timart.domain.context.UsageStatsProvider {
        override fun openCount(): Int = count
        override fun openStreakDays(): Int = streak
        override fun lastOpenMillis(): Long? = lastOpen
        override fun foregroundMinutesToday(packageName: String): Long? = minutesToday
    }

    private class FakeMeta(
        private val total: Int = 0,
        private val readIds: Set<String> = emptySet(),
        private val viewCounts: Map<String, Int> = emptyMap(),
    ) : com.muxiao.timart.domain.context.CapsuleMetaProvider {
        override fun capsuleCount(): Int = total
        override fun isRead(capsuleId: String): Boolean = capsuleId in readIds
        override fun viewCount(capsuleId: String): Int = viewCounts[capsuleId] ?: 0
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
        ambientLight: com.muxiao.timart.domain.context.AmbientLightProvider = FakeAmbientLight(null),
    ) = ConditionContext(
        time, battery, step, network, weather, location, dependency, wifi, stepHistory,
        alarm, systemMode, motion, compass, altitude, usage, meta, ambientLight,
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

        judge.judgeAllLocked(context(), repo, onUnlocked = { unlockedTitles.add(it.title) })

        assertEquals(listOf("cap-ok"), repo.unlockedIds)
        assertEquals(1, unlockedTitles.size)
        assertEquals(CapsuleState.UNLOCKED, repo.items.first { it.id == "cap-ok" }.state)
        assertTrue(repo.items.first { it.id == "cap-ok" }.unlockTimestamp!! > 0)
    }

    // ================= 扩展条件 =================

    private fun judgeOne(
        condition: UnlockCondition,
        ctx: ConditionContext,
        answers: Map<String, String> = emptyMap(),
        capsuleOverride: Capsule? = null,
    ) =
        judge.judge(
            capsuleOverride ?: capsule(UnlockRule(LogicType.AND, listOf(condition))),
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
        assertTrue(set - rise < 86_400_000L / 2 + 3 * 3_600_000L) // 夏至昼长 < 15h（东京纬度）
        // 2026-01-01（北半球冬季昼短）昼长 < 夏至
        val winter = SunCalc.sunTimesUtcMillis(35.6812, 139.7671, LocalDate.of(2026, 1, 1))!!
        assertTrue(winter.second - winter.first < set - rise)
    }

    @Test
    fun `天文 - 金色时刻高度角窗口`() {
        val date = LocalDate.of(2026, 8, 12)
        val (rise, set) = SunCalc.sunTimesUtcMillis(35.6812, 139.7671, date)!!
        // 日出/日落前后数分钟：太阳已升起且高度角 ≤ 6° → 金色时刻
        assertTrue(SunCalc.isGoldenHour(rise + 10 * 60_000L, 35.6812, 139.7671, date))
        assertTrue(SunCalc.isGoldenHour(set - 10 * 60_000L, 35.6812, 139.7671, date))
        // 中天附近高度角远超 6° → 非金色时刻
        assertFalse(SunCalc.isGoldenHour((rise + set) / 2, 35.6812, 139.7671, date))
        // 太阳未升（日出前 1 小时）→ 非金色时刻
        assertFalse(SunCalc.isGoldenHour(rise - 3_600_000L, 35.6812, 139.7671, date))
    }

    // ================= 扩展条件第二批 =================

    @Test
    fun `扩展 - 步数区间两端`() {
        val ctx = context(step = FakeStep(5000))
        assertTrue(judgeOne(UnlockCondition.StepCount(3000, 8000), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.StepCount(5001, 8000), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.StepCount(1000, 4999), ctx).satisfied)
        // 任一端可空（与 BatteryLevel 同构）
        assertTrue(judgeOne(UnlockCondition.StepCount(null, 6000), ctx).satisfied)
        assertTrue(judgeOne(UnlockCondition.StepCount(4000, null), ctx).satisfied)
        // 无计步数据 → 专用原因
        assertEquals(
            JudgeReasons.STEP_UNAVAILABLE,
            judgeOne(UnlockCondition.StepCount(null, 6000), context(step = FakeStep(null))).reason,
        )
    }

    @Test
    fun `扩展 - 飞行模式与音乐播放匹配`() {
        val ctx = context(systemMode = FakeSystemMode(airplane = true, music = false))
        assertTrue(judgeOne(UnlockCondition.AirplaneMode(true), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.AirplaneMode(false), ctx).satisfied)
        assertTrue(judgeOne(UnlockCondition.MusicPlaying(false), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.MusicPlaying(true), ctx).satisfied)
    }

    @Test
    fun `扩展 - 已读联动与凝视次数`() {
        // 本胶囊 id = "cap-1"，meta 假实现：read-1 已读、cap-1 凝视 5 次
        val ctx = context(meta = FakeMeta(4, readIds = setOf("read-1"), viewCounts = mapOf("cap-1" to 5)))
        assertTrue(judgeOne(UnlockCondition.OtherCapsuleRead("read-1"), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.OtherCapsuleRead("fresh"), ctx).satisfied)
        assertTrue(judgeOne(UnlockCondition.ViewCountAtLeast(5), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.ViewCountAtLeast(6), ctx).satisfied)
    }

    @Test
    fun `扩展 - 流星雨极大期判定`() {
        // 英仙座极大 8/12-13（±1 天窗口）：默认 today = 2026-08-12 → 满足
        val ctx = context()
        assertTrue(judgeOne(UnlockCondition.MeteorShower(setOf(MeteorShowerKind.PERSEIDS)), ctx).satisfied)
        // 双子座极大 12/13-14 → 8 月不满足
        assertFalse(judgeOne(UnlockCondition.MeteorShower(setOf(MeteorShowerKind.GEMINIDS)), ctx).satisfied)
        // 多选任一命中即满足
        assertTrue(
            judgeOne(
                UnlockCondition.MeteorShower(setOf(MeteorShowerKind.GEMINIDS, MeteorShowerKind.PERSEIDS)),
                ctx,
            ).satisfied,
        )
    }

    @Test
    fun `扩展 - 农历日期随当日农历判定`() {
        val ctx = context()
        val lunarToday = LunarCalendar.solarToLunar(ctx.time.today())!!
        assertTrue(judgeOne(UnlockCondition.LunarDate(lunarToday.month, lunarToday.day), ctx).satisfied)
        assertFalse(judgeOne(UnlockCondition.LunarDate(lunarToday.month, lunarToday.day + 1), ctx).satisfied)
    }

    @Test
    fun `扩展 - 金色时刻随定位与权限判定`() {
        // 默认假时刻（nowMillis 距中天远）在东京为白天高角度 → 非金色时刻
        assertFalse(judgeOne(UnlockCondition.GoldenHour, context()).satisfied)
        // 权限拒绝 → 原因文案；Worker 后台 → skipped（与 SunPhase 同通道）
        assertEquals(
            JudgeReasons.GPS_NO_PERMISSION,
            judgeOne(UnlockCondition.GoldenHour, context(location = FakeLocation(permitted = false, point = GeoPoint(35.6812, 139.7671)))).reason,
        )
        val bg = judgeOne(
            UnlockCondition.GoldenHour,
            context(location = FakeLocation(fgOnly = true, point = GeoPoint(35.6812, 139.7671))),
        )
        assertTrue(bg.skipped)
    }

    // ================= 扩展条件第三批 =================

    @Test
    fun `扩展 - 黑暗中随环境光判定`() {
        assertTrue(judgeOne(UnlockCondition.AmbientLight(10), context(ambientLight = FakeAmbientLight(3f))).satisfied)
        // 阈值边界：≤ 阈值即满足
        assertTrue(judgeOne(UnlockCondition.AmbientLight(10), context(ambientLight = FakeAmbientLight(10f))).satisfied)
        assertFalse(judgeOne(UnlockCondition.AmbientLight(10), context(ambientLight = FakeAmbientLight(300f))).satisfied)
        // 无光感 → 专用原因
        assertEquals(
            JudgeReasons.forLang(com.muxiao.timart.domain.model.Lang.ZH_HANS).ambientLightUnavailable,
            judgeOne(UnlockCondition.AmbientLight(10), context()).reason,
        )
    }

    @Test
    fun `扩展 - 时区变更随当前时区判定`() {
        assertTrue(judgeOne(UnlockCondition.TimezoneChange("Asia/Shanghai"), context(time = FakeTime(today, zone = "Europe/Paris"))).satisfied)
        assertFalse(judgeOne(UnlockCondition.TimezoneChange("Asia/Shanghai"), context(time = FakeTime(today, zone = "Asia/Shanghai"))).satisfied)
    }

    @Test
    fun `扩展 - 移动中速度通道与GPS同语义`() {
        val moving = UnlockCondition.MovingAboveSpeed(20)
        // 前台 + 速度达标（36 km/h）→ 满足
        val ok = judgeOne(moving, context(location = FakeLocation(point = GeoPoint(35.6812, 139.7671), speedMpsValue = 10f)))
        assertTrue(ok.satisfied)
        // 速度不足（18 km/h）→ 不满足
        assertFalse(judgeOne(moving, context(location = FakeLocation(point = GeoPoint(35.6812, 139.7671), speedMpsValue = 5f))).satisfied)
        // 无定位 → gpsNoFix
        assertEquals(JudgeReasons.GPS_NO_FIX, judgeOne(moving, context(location = FakeLocation())).reason)
        // 权限拒绝 → gpsNoPermission
        assertEquals(
            JudgeReasons.GPS_NO_PERMISSION,
            judgeOne(moving, context(location = FakeLocation(permitted = false, speedMpsValue = 10f))).reason,
        )
        // 后台巡检 → skipped
        val bg = judgeOne(moving, context(location = FakeLocation(fgOnly = true, speedMpsValue = 10f)))
        assertTrue(bg.skipped)
    }

    @Test
    fun `扩展 - 长按生物识别拍照按DONE应答`() {
        val ctx = context()
        val hold = UnlockCondition.HoldPress("c5", 5)
        assertFalse(judgeOne(hold, ctx).satisfied)
        assertTrue(judgeOne(hold, ctx, answers = mapOf("c5" to "DONE")).satisfied)
        val bio = UnlockCondition.BiometricUnlock("c6")
        assertTrue(judgeOne(bio, ctx, answers = mapOf("c6" to "DONE")).satisfied)
        val photo = UnlockCondition.PhotoKeepsake("c7")
        assertTrue(judgeOne(photo, ctx, answers = mapOf("c7" to "DONE")).satisfied)
    }

    // ================= 储备池 v5（delta-prd-vs-code.md D-1.5） =================

    private fun snowCtx(snowDaysPast: Int?, weatherType: WeatherType = WeatherType.SNOW) = context(
        weather = FakeWeather(
            WeatherSnapshot(
                cityId = "city-1",
                cityName = "东京",
                weatherType = weatherType,
                tempC = -2.0,
                capturedAt = 0L,
                snowDaysPast = snowDaysPast,
            ),
        ),
    )

    @Test
    fun `扩展v5 - 降雪观测今日下雪即满足`() {
        assertTrue(judgeOne(UnlockCondition.SnowObservation(firstOfSeason = false), snowCtx(snowDaysPast = 5)).satisfied)
        // 今日没下雪 → 不满足（普通语义无需历史窗口）
        assertFalse(
            judgeOne(
                UnlockCondition.SnowObservation(firstOfSeason = false),
                snowCtx(snowDaysPast = 5, weatherType = WeatherType.RAIN),
            ).satisfied,
        )
    }

    @Test
    fun `扩展v5 - 今冬首雪要求此前无雪记录`() {
        // 此前 92 天窗口无雪 → 满足
        assertTrue(judgeOne(UnlockCondition.SnowObservation(firstOfSeason = true), snowCtx(snowDaysPast = 0)).satisfied)
        // 此前已有雪日 → 不满足
        assertFalse(judgeOne(UnlockCondition.SnowObservation(firstOfSeason = true), snowCtx(snowDaysPast = 3)).satisfied)
        // 历史窗口缺失（接口缺字段/解析失败）→ fail-closed 给「指标不可用」
        val missing = judgeOne(UnlockCondition.SnowObservation(firstOfSeason = true), snowCtx(snowDaysPast = null))
        assertFalse(missing.satisfied)
        assertEquals(
            JudgeReasons.forLang(com.muxiao.timart.domain.model.Lang.ZH_HANS).metricUnavailable,
            missing.reason,
        )
    }

    @Test
    fun `扩展v5 - 降雪观测天气链路失败原因`() {
        // 有快照城市但天气获取失败 → weatherFailed
        assertEquals(
            JudgeReasons.WEATHER_FAILED,
            judgeOne(UnlockCondition.SnowObservation(firstOfSeason = false), context(weather = FakeWeather(null))).reason,
        )
        // 无快照城市 → weatherNoSnapshot
        val noCity = judge.judge(
            capsule(UnlockRule(LogicType.AND, listOf(UnlockCondition.SnowObservation(false))), snapshotCityId = null),
            context(),
        )
        assertEquals(JudgeReasons.WEATHER_NO_SNAPSHOT, noCity.items.single().reason)
    }

    @Test
    fun `扩展v5 - 累计步行按自封存累计量判定`() {
        val cond = UnlockCondition.CumulativeSteps(minSteps = 100_000)
        assertTrue(judgeOne(cond, context(stepHistory = FakeHistory(emptyMap(), sinceTotal = 100_000L))).satisfied)
        assertFalse(judgeOne(cond, context(stepHistory = FakeHistory(emptyMap(), sinceTotal = 99_999L))).satisfied)
        // 通道未接入 / 无硬件 / 无权限 → null → 专用原因
        assertEquals(
            JudgeReasons.forLang(com.muxiao.timart.domain.model.Lang.ZH_HANS).stepUnavailable,
            judgeOne(cond, context(stepHistory = FakeHistory(emptyMap()))).reason,
        )
    }

    // ================= 储备池 v6（delta-prd-vs-code.md D-1.6） =================

    private fun rainCtx(
        rainStreakPast: Int?,
        weatherType: WeatherType = WeatherType.RAIN,
        currentTempC: Double = 8.0,
    ) = context(
        weather = FakeWeather(
            WeatherSnapshot(
                cityId = "city-1",
                cityName = "东京",
                weatherType = weatherType,
                tempC = currentTempC,
                capturedAt = 0L,
                rainStreakPast = rainStreakPast,
            ),
        ),
    )

    /** 封存日天气（Capsule.weather，明文字段）：封存那日 [sealTempC]°C 晴 */
    private fun sealedCapsule(sealTempC: Double = 20.0): Capsule =
        capsule(UnlockRule(LogicType.AND, emptyList()))
            .copy(weather = WeatherSnapshot("city-1", "东京", WeatherType.CLEAR, sealTempC, 0L))

    @Test
    fun `扩展v6 - 连续降雨按今日与历史窗口判定`() {
        // 今日有雨 + 此前 4 天雨 → 连续 5 天满足
        assertTrue(judgeOne(UnlockCondition.RainStreak(days = 5, afterRain = false), rainCtx(rainStreakPast = 4)).satisfied)
        // 今日有雨但历史仅 2 天 → 不满足
        assertFalse(judgeOne(UnlockCondition.RainStreak(days = 5, afterRain = false), rainCtx(rainStreakPast = 2)).satisfied)
        // 今日无雨 → 连续降雨不满足
        assertFalse(
            judgeOne(
                UnlockCondition.RainStreak(days = 3, afterRain = false),
                rainCtx(rainStreakPast = 9, weatherType = WeatherType.CLEAR),
            ).satisfied,
        )
        // 毛雨/雷也算雨（与 WmoCodeMapper 映射同口径）
        assertTrue(
            judgeOne(
                UnlockCondition.RainStreak(days = 2, afterRain = false),
                rainCtx(rainStreakPast = 5, weatherType = WeatherType.THUNDER),
            ).satisfied,
        )
    }

    @Test
    fun `扩展v6 - 雨后初晴要求此前连续降雨且今日转晴`() {
        // 此前 3 天雨 + 今日晴 → 满足
        assertTrue(
            judgeOne(
                UnlockCondition.RainStreak(days = 3, afterRain = true),
                rainCtx(rainStreakPast = 3, weatherType = WeatherType.CLEAR),
            ).satisfied,
        )
        // 此前仅 2 天雨 → 不满足
        assertFalse(
            judgeOne(
                UnlockCondition.RainStreak(days = 3, afterRain = true),
                rainCtx(rainStreakPast = 2, weatherType = WeatherType.CLEAR),
            ).satisfied,
        )
        // 今日仍下雨 → 不满足
        assertFalse(judgeOne(UnlockCondition.RainStreak(days = 3, afterRain = true), rainCtx(rainStreakPast = 3)).satisfied)
    }

    @Test
    fun `扩展v6 - 连续降雨历史窗口缺失fail-closed`() {
        val missing = judgeOne(UnlockCondition.RainStreak(days = 3, afterRain = false), rainCtx(rainStreakPast = null))
        assertFalse(missing.satisfied)
        assertEquals(
            JudgeReasons.forLang(com.muxiao.timart.domain.model.Lang.ZH_HANS).metricUnavailable,
            missing.reason,
        )
        // 天气获取失败 → weatherFailed
        assertEquals(
            JudgeReasons.WEATHER_FAILED,
            judgeOne(UnlockCondition.RainStreak(days = 3, afterRain = false), context(weather = FakeWeather(null))).reason,
        )
    }

    @Test
    fun `扩展v6 - 封存日温差按更冷更热判定`() {
        // 封存日 20°C：当前 12°C → 更冷 5°C 满足；27°C → 更热 5°C 满足；18°C → 均不满足
        fun judgeSeal(condition: UnlockCondition.TempVsSealDay, currentTempC: Double) =
            judge.judge(
                sealedCapsule().copy(
                    unlockRule = UnlockRule(LogicType.AND, listOf(condition)),
                ),
                rainCtx(rainStreakPast = 0, weatherType = WeatherType.CLEAR, currentTempC = currentTempC),
            ).items.single()

        assertTrue(judgeSeal(UnlockCondition.TempVsSealDay(5.0, hotter = false), currentTempC = 12.0).satisfied)
        assertTrue(judgeSeal(UnlockCondition.TempVsSealDay(5.0, hotter = true), currentTempC = 27.0).satisfied)
        assertFalse(judgeSeal(UnlockCondition.TempVsSealDay(10.0, hotter = true), currentTempC = 18.0).satisfied)
        assertFalse(judgeSeal(UnlockCondition.TempVsSealDay(10.0, hotter = false), currentTempC = 18.0).satisfied)
    }

    @Test
    fun `扩展v6 - 封存日未记录天气显式报原因`() {
        // 封存时未选天气城市（Capsule.weather == null）→ 专用原因，不静默
        val noSeal = judge.judge(
            capsule(
                UnlockRule(LogicType.AND, listOf(UnlockCondition.TempVsSealDay(5.0, hotter = false))),
                snapshotCityId = null,
            ),
            rainCtx(rainStreakPast = 0),
        )
        assertEquals(
            JudgeReasons.forLang(com.muxiao.timart.domain.model.Lang.ZH_HANS).sealWeatherMissing,
            noSeal.items.single().reason,
        )
    }

    // ================= 储备池 v7（delta-prd-vs-code.md D-1.7） =================

    @Test
    fun `扩展v7 - 每月农历日按农历日判定`() {
        // 2026-02-17 = 农历正月初一（春节）
        val lunarCtx = context(time = FakeTime(LocalDate.of(2026, 2, 17)))
        assertTrue(judgeOne(UnlockCondition.LunarDayOfMonth(setOf(1)), lunarCtx).satisfied)
        assertFalse(judgeOne(UnlockCondition.LunarDayOfMonth(setOf(15)), lunarCtx).satisfied)
        assertTrue(judgeOne(UnlockCondition.LunarDayOfMonth(setOf(1, 15)), lunarCtx).satisfied)
    }

    private fun thunderCtx(thunderDaysPast: Int?, weatherType: WeatherType = WeatherType.THUNDER) = context(
        weather = FakeWeather(
            WeatherSnapshot(
                cityId = "city-1",
                cityName = "东京",
                weatherType = weatherType,
                tempC = 24.0,
                capturedAt = 0L,
                thunderDaysPast = thunderDaysPast,
            ),
        ),
    )

    @Test
    fun `扩展v7 - 今季首雷按历史窗口判定`() {
        // 今日有雷且此前无雷记录 → 满足
        assertTrue(judgeOne(UnlockCondition.ThunderObservation(firstOfSeason = true), thunderCtx(thunderDaysPast = 0)).satisfied)
        // 此前已有雷日 → 不满足
        assertFalse(judgeOne(UnlockCondition.ThunderObservation(firstOfSeason = true), thunderCtx(thunderDaysPast = 2)).satisfied)
        // 普通语义：今日有雷即满足
        assertTrue(judgeOne(UnlockCondition.ThunderObservation(firstOfSeason = false), thunderCtx(thunderDaysPast = 2)).satisfied)
        // 今日无雷：两种语义都不满足
        assertFalse(judgeOne(UnlockCondition.ThunderObservation(true), thunderCtx(thunderDaysPast = 0, weatherType = WeatherType.RAIN)).satisfied)
        // 历史窗口缺失 → fail-closed「指标不可用」
        assertEquals(
            JudgeReasons.forLang(com.muxiao.timart.domain.model.Lang.ZH_HANS).metricUnavailable,
            judgeOne(UnlockCondition.ThunderObservation(firstOfSeason = true), thunderCtx(thunderDaysPast = null)).reason,
        )
    }

    @Test
    fun `扩展v7 - 明亮环境按照度下限判定`() {
        assertTrue(judgeOne(UnlockCondition.BrightLight(5000), context(ambientLight = FakeAmbientLight(8000f))).satisfied)
        assertFalse(judgeOne(UnlockCondition.BrightLight(10000), context(ambientLight = FakeAmbientLight(8000f))).satisfied)
        assertEquals(
            JudgeReasons.AMBIENT_LIGHT_UNAVAILABLE,
            judgeOne(UnlockCondition.BrightLight(5000), context(ambientLight = FakeAmbientLight(null))).reason,
        )
    }

    @Test
    fun `扩展v7 - 应用用量按今日前台时长判定`() {
        val cond = UnlockCondition.AppUsageCeiling("com.example.app", 30)
        assertTrue(judgeOne(cond, context(usage = FakeUsage(minutesToday = 25))).satisfied)
        // 0 分钟（未使用）也满足
        assertTrue(judgeOne(cond, context(usage = FakeUsage(minutesToday = 0))).satisfied)
        assertFalse(judgeOne(cond, context(usage = FakeUsage(minutesToday = 45))).satisfied)
        // 使用统计权限未授予 → null → 专用原因
        assertEquals(
            JudgeReasons.forLang(com.muxiao.timart.domain.model.Lang.ZH_HANS).usageStatsUnavailable,
            judgeOne(cond, context(usage = FakeUsage())).reason,
        )
    }

    @Test
    fun `扩展v7 - 气压骤降按昨日基线判定`() {
        val dropCtx = context(
            weather = FakeWeather(
                WeatherSnapshot(
                    cityId = "city-1",
                    cityName = "东京",
                    weatherType = WeatherType.RAIN,
                    tempC = 18.0,
                    capturedAt = 0L,
                    pressureHpa = 990.0,
                    yesterdayMeanPressureHpa = 1000.0,
                ),
            ),
        )
        assertTrue(judgeOne(UnlockCondition.PressureDelta(5.0), dropCtx).satisfied)
        assertFalse(judgeOne(UnlockCondition.PressureDelta(15.0), dropCtx).satisfied)
        // 昨日基线缺失 → fail-closed「指标不可用」
        val noBaseline = context(
            weather = FakeWeather(
                WeatherSnapshot("city-1", "东京", WeatherType.RAIN, 18.0, 0L, pressureHpa = 990.0),
            ),
        )
        assertEquals(
            JudgeReasons.forLang(com.muxiao.timart.domain.model.Lang.ZH_HANS).metricUnavailable,
            judgeOne(UnlockCondition.PressureDelta(5.0), noBaseline).reason,
        )
    }

    @Test
    fun `扩展v7 - 体感温度走指标通道`() {
        val feelsCtx = context(
            weather = FakeWeather(
                WeatherSnapshot(
                    cityId = "city-1",
                    cityName = "东京",
                    weatherType = WeatherType.CLOUDY,
                    tempC = 33.0,
                    capturedAt = 0L,
                    feelsLikeC = 40.0,
                ),
            ),
        )
        assertTrue(
            judgeOne(
                UnlockCondition.WeatherMetric(WeatherMetricKind.APPARENT, min = 35.0, max = null),
                feelsCtx,
            ).satisfied,
        )
        assertFalse(
            judgeOne(
                UnlockCondition.WeatherMetric(WeatherMetricKind.APPARENT, min = null, max = 30.0),
                feelsCtx,
            ).satisfied,
        )
        // 旧快照无体感字段 → 指标不可用
        val staleCtx = context(
            weather = FakeWeather(
                WeatherSnapshot("city-1", "东京", WeatherType.CLOUDY, 33.0, 0L),
            ),
        )
        assertEquals(
            JudgeReasons.forLang(com.muxiao.timart.domain.model.Lang.ZH_HANS).metricUnavailable,
            judgeOne(
                UnlockCondition.WeatherMetric(WeatherMetricKind.APPARENT, min = 35.0, max = null),
                staleCtx,
            ).reason,
        )
    }
}
