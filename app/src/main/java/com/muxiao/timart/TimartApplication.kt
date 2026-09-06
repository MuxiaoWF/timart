package com.muxiao.timart

import android.app.Application
import com.muxiao.timart.domain.model.AnimationTier
import com.muxiao.timart.utils.AppLanguage
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.worker.ConditionCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口：构建 AppContainer 组合根、创建通知渠道、注册周期检测 Worker（6h，KEEP 幂等）。
 * 同时把持久化设置镜像进 [RuntimeSettings]（音效 / 飘粒 / 视差的进程内即时读端，
 * 与设置页修改实时同步）。
 */
class TimartApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** 应用级作用域：仅用于启动期轻量加载（meta 读取不阻塞首帧） */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 解锁提醒渠道（API 26+，幂等）
        container.notifier.ensureChannel()
        // 周期检测 Worker（6h 周期，KEEP 幂等）
        ConditionCheckWorker.enqueue(this)
        // 设置镜像兜底加载（冷启动后音效开关等立即按持久化值生效）
        appScope.launch(Dispatchers.IO) {
            val metaDao = container.database.metaDao()
            // 语言镜像最先加载：写入快照状态后各界面组合期自动按所选语言重组
            RuntimeSettings.appLanguage =
                metaDao.get(RuntimeSettings.KEY_LANGUAGE)?.let { stored ->
                    runCatching { AppLanguage.valueOf(stored) }.getOrNull()
                } ?: AppLanguage.SYSTEM
            RuntimeSettings.gyroEnabled =
                metaDao.get(RuntimeSettings.KEY_GYRO)?.toBooleanStrictOrNull() ?: false
            RuntimeSettings.inputSparkEnabled =
                metaDao.get(RuntimeSettings.KEY_INPUT_SPARK)?.toBooleanStrictOrNull() ?: true
            RuntimeSettings.soundEnabled =
                metaDao.get(RuntimeSettings.KEY_SOUND)?.toBooleanStrictOrNull() ?: false
            container.audioManager.setEnabled(RuntimeSettings.soundEnabled)
            // 动效档位镜像：冷启动即按持久化档位重建引擎（此前只在设置页 VM 加载，
            // 导致开机后引擎一直是自动检测档位，需进设置页手动切换才生效）
            metaDao.get(RuntimeSettings.KEY_TIER)?.let { stored ->
                runCatching { AnimationTier.valueOf(stored) }.getOrNull()
            }?.let { tier ->
                if (tier != container.particleTier) container.rebuildParticleEngine(tier)
            }
            // 口令会话恢复：TTL（24h）内的冷启动免重输口令（Keystore 包裹副本，失败则按需输口令）
            container.contentCryptoManager.tryRestoreSession()
        }
    }
}
