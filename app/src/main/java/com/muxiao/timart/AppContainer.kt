package com.muxiao.timart

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import com.muxiao.timart.data.local.CapsuleRepositoryImpl
import com.muxiao.timart.data.local.CityRepositoryImpl
import com.muxiao.timart.data.local.DestroyedRepositoryImpl
import com.muxiao.timart.data.local.crypto.AesGcmCipher
import com.muxiao.timart.data.local.crypto.ContentCryptoManager
import com.muxiao.timart.data.local.crypto.ImageCipherStore
import com.muxiao.timart.data.local.crypto.SessionKeyVault
import com.muxiao.timart.data.local.db.TimartDatabase
import com.muxiao.timart.data.remote.weather.OpenMeteoApi
import com.muxiao.timart.data.remote.weather.WeatherRepositoryImpl
import com.muxiao.timart.domain.context.CapsuleMetaProvider
import com.muxiao.timart.domain.context.ConditionContext
import com.muxiao.timart.domain.context.DependencyChecker
import com.muxiao.timart.domain.model.AnimationTier
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.repository.CapsuleRepository
import com.muxiao.timart.domain.repository.DestroyedRepository
import com.muxiao.timart.domain.usecase.CapsuleCrudUseCase
import com.muxiao.timart.domain.usecase.DependencyGraphUseCase
import com.muxiao.timart.domain.usecase.ReadCapsuleUseCase
import com.muxiao.timart.domain.usecase.UnlockJudgeUseCase
import com.muxiao.timart.ui.components.particle.ParticleEngine
import com.muxiao.timart.utils.app.UsageStatsTracker
import com.muxiao.timart.utils.audio.AudioManager
import com.muxiao.timart.utils.device.SystemModeReader
import com.muxiao.timart.utils.export.BackupManager
import com.muxiao.timart.utils.location.GeocodeResolver
import com.muxiao.timart.utils.location.LocationProviderImpl
import com.muxiao.timart.utils.network.NetworkTypeDetector
import com.muxiao.timart.utils.network.WifiSsidReader
import com.muxiao.timart.utils.notification.Notifier
import com.muxiao.timart.utils.permission.PermissionHelper
import com.muxiao.timart.utils.sensor.BatteryStatusReader
import com.muxiao.timart.utils.sensor.CompassAltitudeReader
import com.muxiao.timart.utils.sensor.MotionActivityReader
import com.muxiao.timart.utils.sensor.StepCounterReader
import com.muxiao.timart.utils.time.SystemTimeProvider
import java.io.File

/**
 * 手写 DI 组合根（全应用唯一依赖组装处）：
 * db/dao → crypto → repositories → providers → useCases → audio/notifier。
 * 全部单例经 lazy 装配，冷启动零重活（粒子引擎在首帧后初始化）。
 *
 * @Stable 恢复 Compose 跳过重组能力：公开 var 会让稳定性推断判为 unstable，
 * 捕获它的 lambda（如 pager 四页内容）无法 memoize，页签切换期间逐帧重组（卡顿主因之一）。
 * 可观察可变位均由 mutableStateOf 承载（pendingNfcCapsuleId / particleEngine / particleTier）；
 * 唯一例外 [pendingCapsulePrefill] 是普通 var，但只在 ViewModel 构造与事件回调中读写、
 * 从不参与组合期读取，快照不可观察不构成漏重组。
 */
@Stable
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    // ---- 数据库与 DAO ----

    val database: TimartDatabase by lazy { TimartDatabase.get(appContext) }

    private val capsuleDao get() = database.capsuleDao()
    private val metaDao get() = database.metaDao()
    private val destroyedDao get() = database.destroyedDao()

    // ---- 加密体系 ----

    val aesGcmCipher: AesGcmCipher by lazy { AesGcmCipher() }

    val imageCipherStore: ImageCipherStore by lazy {
        ImageCipherStore(
            baseDir = File(appContext.filesDir, "images"),
            cipher = aesGcmCipher,
            sessionKeyProvider = { contentCryptoManager.sessionKeyOrNull() },
        )
    }

    /** 加密语音仓库（体验储备池 §1 声音留言；filesDir/audio/{id}/audio_0.bin，不落 Room） */
    val audioCipherStore: com.muxiao.timart.data.local.crypto.AudioCipherStore by lazy {
        com.muxiao.timart.data.local.crypto.AudioCipherStore(
            baseDir = File(appContext.filesDir, "audio"),
            cipher = aesGcmCipher,
            sessionKeyProvider = { contentCryptoManager.sessionKeyOrNull() },
        )
    }

    /** 语音留言播放器（详情页信笺播放键；明文只进 cacheDir 临时文件，播完即删） */
    val voicePlayer: com.muxiao.timart.utils.audio.VoicePlayer by lazy {
        com.muxiao.timart.utils.audio.VoicePlayer(appContext, audioCipherStore)
    }

    val contentCryptoManager: ContentCryptoManager by lazy {
        ContentCryptoManager(
            cipher = aesGcmCipher,
            metaDao = metaDao,
            capsuleRepository = capsuleRepository,
            imageStore = imageCipherStore,
            sessionVault = sessionKeyVault,
            audioStore = audioCipherStore,
        )
    }

    /** 会话保险库（Keystore 包裹，TTL 内冷启动免口令；见 ContentCryptoManager.tryRestoreSession） */
    val sessionKeyVault: SessionKeyVault by lazy { SessionKeyVault(appContext) }

    // ---- 仓库 ----

    val capsuleRepositoryImpl: CapsuleRepositoryImpl by lazy { CapsuleRepositoryImpl(capsuleDao) }

    val capsuleRepository: CapsuleRepository get() = capsuleRepositoryImpl

    /** 判定上下文的依赖状态查询通道（同步适配在 DependencyCheckerImpl 内） */
    val dependencyChecker: DependencyChecker get() = capsuleRepositoryImpl.dependencyChecker

    val destroyedRepository: DestroyedRepository by lazy { DestroyedRepositoryImpl(destroyedDao) }

    val cityRepository: CityRepositoryImpl by lazy { CityRepositoryImpl(appContext, metaDao) }

    /** Open-Meteo API 客户端（当前天气 + 逐日预报共用同一实例） */
    val openMeteoApi: OpenMeteoApi by lazy { OpenMeteoApi() }

    val weatherRepositoryImpl: WeatherRepositoryImpl by lazy {
        WeatherRepositoryImpl(openMeteoApi, cityRepository)
    }

    /** 地址解析链（GPS 条件表单：原生 Geocoder → Nominatim → Photon → 天地图） */
    val geocodeResolver: GeocodeResolver by lazy { GeocodeResolver(appContext, BuildConfig.TIANDITU_TK) }

    // ---- 环境感知 Provider ----

    val timeProvider: SystemTimeProvider by lazy { SystemTimeProvider() }

    val batteryProvider: BatteryStatusReader by lazy { BatteryStatusReader(appContext) }

    val stepProvider: StepCounterReader by lazy { StepCounterReader(appContext) }

    val networkProvider: NetworkTypeDetector by lazy { NetworkTypeDetector(appContext) }

    /** 当前 Wi-Fi SSID 提供者（SSID 条件判定通道） */
    val wifiProvider: WifiSsidReader by lazy { WifiSsidReader(appContext) }

    /** 前台与 Worker 各持一份实例（foregroundOnly 标志不同） */
    fun locationProvider(foregroundOnly: Boolean): LocationProviderImpl =
        LocationProviderImpl(appContext, foregroundOnly)

    val permissionHelper: PermissionHelper by lazy { PermissionHelper(appContext) }

    // ---- 扩展条件 Provider ----

    /** 闹钟 / 省电 / 静音 / 耳机（同步快照，零回调） */
    val systemModeReader: SystemModeReader by lazy { SystemModeReader(appContext) }

    /** 指南针 + 气压计海拔（常驻低频监听） */
    val compassAltitudeReader: CompassAltitudeReader by lazy { CompassAltitudeReader(appContext) }

    /** 运动状态（无 GMS 启发式：计步检测 → 步行/静止） */
    val motionActivityReader: MotionActivityReader by lazy { MotionActivityReader(appContext) }

    /** 环境光（AmbientLight 条件判定通道；无光感设备返回 null） */
    val lightSensorReader: com.muxiao.timart.utils.sensor.LightSensorReader by lazy {
        com.muxiao.timart.utils.sensor.LightSensorReader(appContext)
    }

    // ---- 储备池 v4 Provider ----

    /** 系统界面状态 + 设备硬件状态（深色/横竖屏/勿扰/亮度/媒体静音/VPN/充电方式/电池温度/开机时长） */
    val deviceStateReader: com.muxiao.timart.utils.device.DeviceStateReader by lazy {
        com.muxiao.timart.utils.device.DeviceStateReader(appContext)
    }

    /** 传感器扩展（设备姿态 / 接近遮挡；无对应硬件返回 null） */
    val sensorExtraReader: com.muxiao.timart.utils.sensor.SensorExtraReader by lazy {
        com.muxiao.timart.utils.sensor.SensorExtraReader(appContext)
    }

    /** 应用生态（已装应用 / 桌面小组件 / 蓝牙设备名） */
    val appEnvReader: com.muxiao.timart.utils.app.AppEnvReader by lazy {
        com.muxiao.timart.utils.app.AppEnvReader(appContext)
    }

    /** 空气质量（AirQuality 条件判定通道，Open-Meteo Air Quality API + 缓存） */
    val airQualityRepository: com.muxiao.timart.data.remote.weather.AirQualityRepositoryImpl by lazy {
        com.muxiao.timart.data.remote.weather.AirQualityRepositoryImpl(
            com.muxiao.timart.data.remote.weather.OpenMeteoAirApi(),
            cityRepository,
        )
    }

    /** 应用内使用统计（打开次数 / 连击 / 上次打开；MainActivity ON_RESUME 记录会话） */
    val usageStatsTracker: UsageStatsTracker by lazy { UsageStatsTracker(appContext) }

    /** 胶囊库元信息（总数 / 已读标记 / 凝视计数，均为 meta 表 key-value） */
    val capsuleMetaProvider: CapsuleMetaProvider by lazy {
        object : CapsuleMetaProvider {
            override fun capsuleCount(): Int = runCatching { allCapsulesSnapshot().size }.getOrDefault(0)

            // 读失败按 false/0 处理（fail-closed）：条件按不满足，不抛异常打断判定链
            override fun isRead(capsuleId: String): Boolean = runCatching {
                metaDao.getSync("capsule.read.$capsuleId") == "true"
            }.getOrDefault(false)

            override fun viewCount(capsuleId: String): Int = runCatching {
                metaDao.getSync("capsule.views.$capsuleId")?.toIntOrNull() ?: 0
            }.getOrDefault(0)

            // ---- 储备池 v4 扩展通道 ----

            override fun readCount(): Int = runCatching { metaDao.readCountSync() }.getOrDefault(0)

            override fun destroyCount(): Int = runCatching { destroyedDao.countSync() }.getOrDefault(0)

            override fun totalCreated(): Int = runCatching {
                metaDao.getSync("app.created.total")?.toIntOrNull() ?: 0
            }.getOrDefault(0)

            override fun backupDone(): Boolean = runCatching {
                metaDao.getSync(BackupManager.FLAG_BACKUP_DONE) == "true"
            }.getOrDefault(false)

            override fun watchSeconds(capsuleId: String): Int = runCatching {
                metaDao.getSync("capsule.watch.$capsuleId")?.toIntOrNull() ?: 0
            }.getOrDefault(0)

            override fun lastReadAt(capsuleId: String): Long? = runCatching {
                metaDao.getSync("capsule.readAt.$capsuleId")?.toLongOrNull()
            }.getOrNull()
        }
    }

    /** 全量胶囊快照（meta 条件用；判定频率低，直接同步取） */
    private fun allCapsulesSnapshot(): List<Capsule> =
        runCatching { kotlinx.coroutines.runBlocking { capsuleRepository.allSync() } }.getOrDefault(emptyList())

    // ---- UseCase ----

    val unlockJudgeUseCase: UnlockJudgeUseCase by lazy { UnlockJudgeUseCase() }

    val dependencyGraphUseCase: DependencyGraphUseCase by lazy { DependencyGraphUseCase() }

    val capsuleCrudUseCase: CapsuleCrudUseCase by lazy {
        CapsuleCrudUseCase(
            capsules = capsuleRepository,
            destroyedRecords = destroyedRepository,
            crypto = contentCryptoManager,
            imageStore = imageCipherStore,
            audioStore = audioCipherStore,
            metaDao = metaDao,
        )
    }

    val readCapsuleUseCase: ReadCapsuleUseCase by lazy {
        ReadCapsuleUseCase(contentCryptoManager, imageCipherStore)
    }

    // ---- 通知与音效 ----

    val notifier: Notifier by lazy { Notifier(appContext) }

    val audioManager: AudioManager by lazy { AudioManager(appContext) }

    // ---- 备份导出导入（T15）----

    val backupManager: BackupManager by lazy {
        BackupManager(
            context = appContext,
            cipher = aesGcmCipher,
            crypto = contentCryptoManager,
            metaDao = metaDao,
            capsuleRepository = capsuleRepository,
            imageStore = imageCipherStore,
            destroyedRepository = destroyedRepository,
            audioStore = audioCipherStore,
        )
    }

    /** 自动定期本地备份（MainActivity ON_RESUME 周期检查 + 设置页配置；复用 BackupManager 导出链路） */
    val autoBackupManager: com.muxiao.timart.utils.export.AutoBackupManager by lazy {
        com.muxiao.timart.utils.export.AutoBackupManager(
            context = appContext,
            backupManager = backupManager,
            crypto = contentCryptoManager,
            metaDao = metaDao,
            // 随备份体检（N22）：复用 LibraryHealthCheck 纯逻辑 + 既有数据采集口径（同 MaintenanceDialogs）
            healthScan = {
                val capsules = capsuleRepository.allSync()
                val metaRows = database.metaDao().listLike("capsule.")
                com.muxiao.timart.domain.usecase.LibraryHealthCheck.scan(
                    capsules = capsules,
                    metaKeys = metaRows.map { it.key },
                    condMetPrefix = com.muxiao.timart.data.local.db.CapsuleMetaKeys.COND_MET_KEY_PREFIX,
                    prefixes = com.muxiao.timart.data.local.db.CapsuleMetaKeys.ORPHAN_SCAN_PREFIXES,
                )
            },
        )
    }

    // ---- NFC 锚点 / 嵌套种子 / 触觉（体验储备池 §3–§6） ----

    /** 口令分片（体验储备池 §7.1）：GF(256) Shamir 拆分/重构 + 分片串编解码（纯 Kotlin） */
    val shardSecretUseCase: com.muxiao.timart.domain.usecase.ShardSecretUseCase by lazy {
        com.muxiao.timart.domain.usecase.ShardSecretUseCase()
    }

    /** 触觉签名播放器（体验储备池 §6；零权限） */
    val haptics: com.muxiao.timart.utils.device.Haptics by lazy {
        com.muxiao.timart.utils.device.Haptics(appContext)
    }

    /** 播放胶囊触觉签名：读 meta `capsule.haptic.<id>`（0 = 无）后振动；读取失败静默 */
    fun playCapsuleHaptic(capsuleId: String, destroy: Boolean) {
        val style = runCatching {
            metaDao.getSync(com.muxiao.timart.data.local.db.CapsuleMetaKeys.haptic(capsuleId))
                ?.toIntOrNull() ?: 0
        }.getOrDefault(0)
        if (style != 0) haptics.play(style, destroy)
    }

    /** 环境音播放器（体验储备池 §6；合成循环、零音频资源；解封淡入 / 离场淡出） */
    val ambientSoundPlayer: com.muxiao.timart.utils.audio.AmbientSoundPlayer by lazy {
        com.muxiao.timart.utils.audio.AmbientSoundPlayer()
    }

    /**
     * NFC 实体锚点待直达的胶囊 id（体验储备池 §4）：MainActivity 解析 `timart.com:link`
     * NDEF 分发后写入，NavGraph 观察到非空即导航详情并清空。Compose 快照状态驱动。
     */
    var pendingNfcCapsuleId: String? by mutableStateOf(null)

    /**
     * App Shortcuts 待执行动作（静态 shortcut extra → MainActivity 解析写入）：
     * 值 = MainActivity.SHORTCUT_CREATE / SHORTCUT_NEXT_UNLOCK；NavGraph 消费并清空。
     * 与 [pendingNfcCapsuleId] 同为一次性进程内交接位。
     */
    var pendingShortcutAction: String? by mutableStateOf(null)

    /**
     * 「最近可解的一颗」（App Shortcut nextUnlock 目标解析）：全部锁定胶囊中
     * 确定性可推算目标时刻最近的一颗（口径与单球小组件 nearestEntry 一致；休眠种子跳过；无可推算目标返回 null）。
     */
    suspend fun nextUnlockableCapsuleId(nowMillis: Long = timeProvider.nowMillis()): String? {
        val zone = runCatching { java.time.ZoneId.of(timeProvider.zoneId()) }
            .getOrDefault(java.time.ZoneId.systemDefault())
        var bestId: String? = null
        var bestAt = Long.MAX_VALUE
        for (capsule in capsuleRepository.allLockedSync()) {
            if (isSeedDormant(capsule.id)) continue
            val createdDay = java.time.Instant.ofEpochMilli(capsule.createTimestamp)
                .atZone(zone).toLocalDate()
            val target = capsule.unlockRule.conditionList
                .mapNotNull {
                    com.muxiao.timart.domain.usecase.UpcomingReminders.fixedTargetAt(
                        it, nowMillis, zone, createdDay,
                    )
                }
                .minOrNull() ?: continue
            if (target < bestAt) {
                bestAt = target
                bestId = capsule.id
            }
        }
        return bestId
    }

    /**
     * 回信转新胶囊的待预填草稿（N5）：Detail 写入 (预填标题, 预填正文, 回信来源胶囊 id)，
     * CreateViewModel 构造时消费并清空；来源 id 非空时封存成功落 meta `capsule.replyTo.<newId>`。
     * 与 [pendingNfcCapsuleId] 同为一次性进程内交接位。
     */
    data class CapsulePrefill(val title: String, val body: String, val replySourceId: String?)

    var pendingCapsulePrefill: CapsulePrefill? = null

    /**
     * 前台全量判定满足比快照（MainActivity ON_RESUME 判定链完成后发布）：
     * key = 仍锁定的胶囊 id（overallOk=false），value = satisfied/total。
     * HomeViewModel 回前台时若快照晚于本次 resume 即采纳，避免同一前台周期双重全量判定。
     * 非 Compose 状态：仅后台线程读写，@Volatile 保证可见性。
     */
    @Volatile
    var foregroundJudgeSnapshot: ForegroundJudgeSnapshot? = null

    /** 前台判定快照（完成时刻 + 仍锁定胶囊的满足比） */
    data class ForegroundJudgeSnapshot(val completedAtMillis: Long, val ratios: Map<String, Float>)

    /**
     * 嵌套种子是否休眠（体验储备池 §3）：有 seedOf 标记且无 sprout 标记。
     *  同步读（MainActivity 全量判定 / Worker 的 shouldJudge 用），失败按未休眠 fail-open——
     *  宁可让种子照常判定，也不让读取故障把已萌芽/普通胶囊藏起来。 */
    fun isSeedDormant(capsuleId: String): Boolean = runCatching {
        val dao = metaDao
        dao.getSync(com.muxiao.timart.data.local.db.CapsuleMetaKeys.seedOf(capsuleId)) != null &&
            dao.getSync(com.muxiao.timart.data.local.db.CapsuleMetaKeys.sprout(capsuleId)) != "true"
    }.getOrDefault(false)

    // ---- 粒子引擎 ----

    /**
     * 全应用唯一粒子引擎（架构 §2.18）。
     * 初始档位按 ActivityManager 内存自动检测（isLowRamDevice 强制 LOW）；
     * 设置页手动切换档位时经 [rebuildParticleEngine] 热重建。
     * 用 Compose 快照状态承载：热替换实例后，所有在组合期读取此属性的页面
     * 立即重组换用新引擎（ParticleCanvas 按 engine 实例重新 attach），档位改动真实可见。
     */
    var particleEngine: ParticleEngine by mutableStateOf(ParticleEngine(defaultAnimationTier()))
        private set

    /** 当前生效档位（与 [particleEngine] 同步更新） */
    var particleTier: AnimationTier by mutableStateOf(defaultAnimationTier())
        private set

    /** 档位热切换：重建引擎预算（旧实例随各页 ParticleCanvas 重新 attach 自然释放） */
    fun rebuildParticleEngine(tier: AnimationTier) {
        particleEngine = ParticleEngine(tier)
        particleTier = tier
    }

    private fun defaultAnimationTier(): AnimationTier {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        // 按设备总内存分档（此前误用 memoryClass=应用堆上限，通常仅 128–512MB，
        // 永远够不到 3GB/6GB 阈值 → 自动检测恒为 LOW，背景尘与呼吸内流全部为 0）
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        return AnimationTier.autoDetect(memInfo.totalMem, am?.isLowRamDevice == true)
    }

    /** 读取 Content Uri 字节（创建页选图用；读取失败返回 null，不抛出） */
    fun readImageBytes(uri: Uri): ByteArray? = runCatching {
        appContext.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
    }.getOrNull()

    // ---- 条件判定上下文 ----

    /**
     * 默认前台上下文；Worker 场景传 foregroundOnly = true（GPS 条件按 skipped 处理）。
     * 复用 UnlockJudgeUseCase，不另写后台判定逻辑。
     * v4 扩展通道（systemUi/deviceExtra/sensorExtra/appEnv/airQuality）前台与 Worker 同源注入；
     * 单测假件可不传（可空默认 null → 判定按「不可用」fail-closed）。
     */
    fun defaultContext(foregroundOnly: Boolean = false): ConditionContext = ConditionContext(
        time = timeProvider,
        battery = batteryProvider,
        step = stepProvider,
        network = networkProvider,
        weather = weatherRepositoryImpl,
        location = locationProvider(foregroundOnly),
        dependency = dependencyChecker,
        wifi = wifiProvider,
        stepHistory = stepProvider,
        alarm = systemModeReader,
        systemMode = systemModeReader,
        motion = motionActivityReader,
        compass = compassAltitudeReader,
        altitude = compassAltitudeReader,
        usage = usageStatsTracker,
        meta = capsuleMetaProvider,
        ambientLight = lightSensorReader,
        systemUi = deviceStateReader,
        deviceExtra = deviceStateReader,
        sensorExtra = sensorExtraReader,
        appEnv = appEnvReader,
        airQuality = airQualityRepository,
    )
}
