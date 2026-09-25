package com.muxiao.timart.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.TimartApplication
import java.util.concurrent.TimeUnit

/**
 * 后台周期检测（ARCHITECTURE §2.13）：
 * - 6 小时周期（KEEP 策略：同任务已入队则不重复）；
 * - doWork 构造 `foregroundOnly=true` 上下文：GPS 条件按 skipped 处理（不满足 +
 *   原因「打开 App 后检测位置条件」），其余条件全部正常判定；
 * - 满足 → 持久化 UNLOCKED + 本地通知；判定逻辑与前台共用 UnlockJudgeUseCase
 *   （架构红线：不另写一套后台判定）。
 */
class ConditionCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TimartApplication ?: return Result.failure()
        val container = app.container
        return try {
            container.unlockJudgeUseCase.judgeAllLocked(
                ctx = container.defaultContext(foregroundOnly = true),
                repo = container.capsuleRepository,
                onUnlocked = { capsule ->
                    container.notifier.notifyUnlock(capsule.id, capsule.title)
                },
                lang = RuntimeSettings.resolvedLang,
                // 休眠种子（嵌套胶囊未萌芽）不参与周期判定：藏着的种子条件达成也不解锁
                shouldJudge = { capsule -> !container.isSeedDormant(capsule.id) },
            )
            runUpcomingReminders(container)
            runForecastNotices(container)
            // 小组件列表化（N21）+ 单球（储备池 v7）：与判定/提醒同拍刷新（6h 周期；无实例时 no-op）
            runCatching {
                com.muxiao.timart.widget.TimartWidgetProvider().refreshAll(applicationContext)
                com.muxiao.timart.widget.TimartWidgetBallProvider().refreshAll(applicationContext)
            }
            Result.success()
        } catch (_: Throwable) {
            // 单次失败不重试：周期任务下个周期自然再来（retry 会在周期内反复重跑）
            Result.success()
        }
    }

    /**
     * 临近解锁提醒（N1）：确定性时间条件（FixedDate / FixedDateTime / YearlyDate）在提前量
     * 窗口内发一条本地通知；meta `settings.remindSent.<id>` 记录已提醒的剩余天数档位去重
     * （6h 周期内同一档位只发一条）。休眠种子与判定同口径跳过（不泄露隐藏种子的存在）。
     * 任何异常不影响主判定结果（本方法内部已兜底）。
     */
    private suspend fun runUpcomingReminders(container: com.muxiao.timart.AppContainer) {
        runCatching {
            val metaDao = container.database.metaDao()
            val locked = container.capsuleRepository.allLockedSync()
                .filter { !container.isSeedDormant(it.id) }
            val dues = com.muxiao.timart.domain.usecase.UpcomingReminders.due(
                capsules = locked,
                leadDaysOf = { id ->
                    metaDao.getSync(com.muxiao.timart.data.local.db.CapsuleMetaKeys.remind(id))
                        ?.toIntOrNull()
                },
                now = System.currentTimeMillis(),
            )
            dues.forEach { due ->
                val sentKey = com.muxiao.timart.data.local.db.CapsuleMetaKeys.remindSent(due.capsule.id)
                val lastSent = metaDao.getSync(sentKey)?.toIntOrNull()
                if (lastSent != due.daysLeft) {
                    container.notifier.notifyUpcoming(due.capsule.id, due.capsule.title, due.daysLeft)
                    metaDao.putSync(
                        com.muxiao.timart.data.local.db.entity.MetaEntity(
                            sentKey,
                            due.daysLeft.toString(),
                        ),
                    )
                }
            }
        }
    }

    /**
     * 天气预告通知（储备池 v6，v7 扩展到指标类）：含天气类条件（WeatherType / 气温区间 /
     * 降水概率 / 风向）的 LOCKED 胶囊，未来 3 天预报命中 → 本地通知「未来可能出现 X，某胶囊接近可解」。
     * 只预告不判锁（解锁仍由判定引擎依实况决定）；去重 = meta `settings.forecastSent.<id>`
     * 记录上次预告日期（每胶囊每天最多一条）。按城市聚合预报请求（同城胶囊共用一次网络）。
     * 任何失败静默跳过（fail-quiet：预告是增益信息，不可用不影响主链路）。
     */
    private suspend fun runForecastNotices(container: com.muxiao.timart.AppContainer) {
        runCatching {
            val metaDao = container.database.metaDao()
            val today = java.time.LocalDate.now()
            val todayIso = today.toString()
            val locked = container.capsuleRepository.allLockedSync()
                .filter { !container.isSeedDormant(it.id) }
                .filter { it.weather != null }
            val targets = locked.mapNotNull { capsule ->
                val weatherConditions = capsule.unlockRule.conditionList.mapNotNull { condition ->
                    when (condition) {
                        is com.muxiao.timart.domain.model.unlock.UnlockCondition.WeatherType ->
                            condition.forecastable()
                        is com.muxiao.timart.domain.model.unlock.UnlockCondition.TemperatureThreshold ->
                            condition.forecastable()
                        is com.muxiao.timart.domain.model.unlock.UnlockCondition.PrecipitationProbability ->
                            condition.forecastable()
                        is com.muxiao.timart.domain.model.unlock.UnlockCondition.WindDirection ->
                            condition.forecastable()
                        else -> null
                    }
                }
                if (weatherConditions.isEmpty()) null else capsule to weatherConditions
            }
            if (targets.isEmpty()) return

            // 按城市聚合取预报（每城一次网络请求）
            val cityIds = targets.map { it.first.weather!!.cityId }.distinct()
            val cityRepository = container.cityRepository
            val api = container.openMeteoApi
            val forecastsByCity = HashMap<String, List<com.muxiao.timart.data.remote.weather.OpenMeteoApi.DailyForecastEntry>>()
            cityIds.forEach { cityId ->
                val city = cityRepository.byId(cityId) ?: return@forEach
                val forecast = api.fetchDailyForecast(city.lat, city.lng, days = 3) ?: return@forEach
                forecastsByCity[cityId] = forecast.filter { it.date.isAfter(today) }
            }

            targets.forEach { (capsule, conditions) ->
                val cityId = capsule.weather!!.cityId
                val forecast = forecastsByCity[cityId] ?: return@forEach
                // 命中任一条件即出预告；描述词取首个命中条件的类型名
                var hitDescription: String? = null
                for (condition in conditions) {
                    if (forecast.any { condition.matchesForecastDay(it) }) {
                        hitDescription = condition.forecastLabel(RuntimeSettings.resolvedLang)
                        break
                    }
                }
                val description = hitDescription ?: return@forEach
                // 去重：每胶囊每天最多一条
                val sentKey = com.muxiao.timart.data.local.db.CapsuleMetaKeys.forecastSent(capsule.id)
                if (metaDao.getSync(sentKey) == todayIso) return@forEach
                container.notifier.notifyForecast(capsule.id, capsule.title, description)
                metaDao.putSync(
                    com.muxiao.timart.data.local.db.entity.MetaEntity(sentKey, todayIso),
                )
            }
        }
    }

    /** 可参与天气预告的条件（只预告不判锁；判定仍是唯一事实源） */
    private interface Forecastable {
        /** 未来某天的预报是否命中本条件（语义 = 该日"可能出现"条件所需的天气） */
        fun matchesForecastDay(day: com.muxiao.timart.data.remote.weather.OpenMeteoApi.DailyForecastEntry): Boolean

        /** 通知文案里的条件描述词（随界面语言） */
        fun forecastLabel(lang: com.muxiao.timart.domain.model.Lang): String
    }

    private fun com.muxiao.timart.domain.model.unlock.UnlockCondition.WeatherType.forecastable() =
        object : Forecastable {
            override fun matchesForecastDay(day: com.muxiao.timart.data.remote.weather.OpenMeteoApi.DailyForecastEntry) =
                com.muxiao.timart.data.remote.weather.WmoCodeMapper.map(day.weatherCode).name in weatherTypes

            override fun forecastLabel(lang: com.muxiao.timart.domain.model.Lang): String =
                com.muxiao.timart.l10n.stringsFor(lang).condWeather
        }

    private fun com.muxiao.timart.domain.model.unlock.UnlockCondition.TemperatureThreshold.forecastable() =
        object : Forecastable {
            override fun matchesForecastDay(day: com.muxiao.timart.data.remote.weather.OpenMeteoApi.DailyForecastEntry) =
                (minC == null || (day.tempMaxC ?: Double.NaN) >= minC) &&
                    (maxC == null || (day.tempMinC ?: Double.NaN) <= maxC)

            override fun forecastLabel(lang: com.muxiao.timart.domain.model.Lang): String =
                com.muxiao.timart.l10n.stringsFor(lang).condTemp
        }

    private fun com.muxiao.timart.domain.model.unlock.UnlockCondition.PrecipitationProbability.forecastable() =
        object : Forecastable {
            override fun matchesForecastDay(day: com.muxiao.timart.data.remote.weather.OpenMeteoApi.DailyForecastEntry) =
                (day.precipProbMax ?: -1) >= minProb

            override fun forecastLabel(lang: com.muxiao.timart.domain.model.Lang): String =
                com.muxiao.timart.l10n.stringsFor(lang).condPrecipProb
        }

    private fun com.muxiao.timart.domain.model.unlock.UnlockCondition.WindDirection.forecastable() =
        object : Forecastable {
            override fun matchesForecastDay(day: com.muxiao.timart.data.remote.weather.OpenMeteoApi.DailyForecastEntry) =
                day.windDirDeg != null &&
                    com.muxiao.timart.domain.model.unlock.windDirFromDeg(day.windDirDeg) in dirs

            override fun forecastLabel(lang: com.muxiao.timart.domain.model.Lang): String =
                com.muxiao.timart.l10n.stringsFor(lang).condWindDir
        }

    companion object {

        private const val WORK_NAME = "timart_condition_check"        /** PRD：后台周期 6 小时 */
        private const val REPEAT_HOURS = 6L

        /** 周期任务入队（KEEP 幂等；TimartApplication.onCreate 调用） */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ConditionCheckWorker>(
                REPEAT_HOURS,
                TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
