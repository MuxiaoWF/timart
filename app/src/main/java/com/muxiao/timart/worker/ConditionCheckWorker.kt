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
            )
            Result.success()
        } catch (_: Throwable) {
            // 单次失败不重试：周期任务下个周期自然再来（retry 会在周期内反复重跑）
            Result.success()
        }
    }

    companion object {

        private const val WORK_NAME = "timart_condition_check"

        /** PRD：后台周期 6 小时 */
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
