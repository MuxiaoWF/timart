package com.muxiao.timart.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxiao.timart.AppContainer
import com.muxiao.timart.data.local.db.entity.MetaEntity
import com.muxiao.timart.domain.model.AnimationTier
import com.muxiao.timart.l10n.currentStrings
import com.muxiao.timart.utils.AppLanguage
import com.muxiao.timart.utils.RuntimeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 设置页 VM：设置项全部读写 meta，进程内即时生效 + 重启后保持。
 *
 * - 档位（settings.tier）：立即 [AppContainer.rebuildParticleEngine] 热重建引擎预算；
 * - 音效（settings.sound）：立即 [com.muxiao.timart.utils.audio.AudioManager.setEnabled]；
 * - 陀螺仪 / 飘粒（settings.gyro / settings.inputSpark）：持久化 + [RuntimeSettings] 镜像即时生效；
 * - 修改口令：[com.muxiao.timart.data.local.crypto.ContentCryptoManager.changePassword]
 *   全库重加密（含图片），进度经 [passwordChange] 上报，期间 UI 禁止退出。
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val metaDao get() = container.database.metaDao()

    private val _tier = MutableStateFlow(container.particleTier)
    val tier: StateFlow<AnimationTier> = _tier

    private val _gyroEnabled = MutableStateFlow(true)
    val gyroEnabled: StateFlow<Boolean> = _gyroEnabled

    private val _inputSparkEnabled = MutableStateFlow(true)
    val inputSparkEnabled: StateFlow<Boolean> = _inputSparkEnabled

    private val _soundEnabled = MutableStateFlow(false)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled

    private val _language = MutableStateFlow(RuntimeSettings.appLanguage)
    val language: StateFlow<AppLanguage> = _language

    private val _hasPassword = MutableStateFlow(false)
    val hasPassword: StateFlow<Boolean> = _hasPassword

    private val _passwordChange = MutableStateFlow<PasswordChangeState?>(null)
    val passwordChange: StateFlow<PasswordChangeState?> = _passwordChange

    /** 修改口令过程状态：finished=false 进行中；finished+error=失败；finished+无error=成功 */
    data class PasswordChangeState(
        val done: Int,
        val total: Int,
        val finished: Boolean,
        val error: String?,
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val tier = metaDao.get(KEY_TIER)?.let { stored ->
                runCatching { AnimationTier.valueOf(stored) }.getOrNull()
            } ?: container.particleTier
            val gyro = metaDao.get(KEY_GYRO)?.toBooleanStrictOrNull() ?: false // ARCHITECTURE §2.14：视差默认关
            val spark = metaDao.get(KEY_INPUT_SPARK)?.toBooleanStrictOrNull() ?: true
            val sound = metaDao.get(KEY_SOUND)?.toBooleanStrictOrNull() ?: false // 音效默认关闭
            val has = runCatching { container.contentCryptoManager.hasPasswordSetup() }.getOrDefault(false)

            _tier.value = tier
            _gyroEnabled.value = gyro
            _inputSparkEnabled.value = spark
            _soundEnabled.value = sound
            _hasPassword.value = has

            // 进程内即时生效（冷启动兜底由 TimartApplication 加载 meta 完成；
            // 若设置页早于 Application 异步加载完成，这里兜底对齐引擎档位）
            if (container.particleTier != tier) container.rebuildParticleEngine(tier)
            RuntimeSettings.gyroEnabled = gyro
            RuntimeSettings.inputSparkEnabled = spark
            RuntimeSettings.soundEnabled = sound
            container.audioManager.setEnabled(sound)
        }
    }

    /** 切换动效档位：热重建引擎预算（新实例随各页 ParticleCanvas 重新 attach 生效）+ 持久化 */
    fun setTier(tier: AnimationTier) {
        if (_tier.value == tier) return
        _tier.value = tier
        container.rebuildParticleEngine(tier)
        viewModelScope.launch(Dispatchers.IO) {
            metaDao.put(MetaEntity(KEY_TIER, tier.name))
        }
    }

    fun setGyroEnabled(value: Boolean) {
        _gyroEnabled.value = value
        RuntimeSettings.gyroEnabled = value
        viewModelScope.launch(Dispatchers.IO) {
            metaDao.put(MetaEntity(KEY_GYRO, value.toString()))
        }
    }

    fun setInputSparkEnabled(value: Boolean) {
        _inputSparkEnabled.value = value
        RuntimeSettings.inputSparkEnabled = value
        viewModelScope.launch(Dispatchers.IO) {
            metaDao.put(MetaEntity(KEY_INPUT_SPARK, value.toString()))
        }
    }

    fun setSoundEnabled(value: Boolean) {
        _soundEnabled.value = value
        RuntimeSettings.soundEnabled = value
        container.audioManager.setEnabled(value)
        viewModelScope.launch(Dispatchers.IO) {
            metaDao.put(MetaEntity(KEY_SOUND, value.toString()))
        }
    }

    // ---- 昼夜暖色变体（N17，默认关）：只读本地时钟零网络 ----

    private val _dawnDuskTint = MutableStateFlow(RuntimeSettings.dawnDuskTint)
    val dawnDuskTint: StateFlow<Boolean> = _dawnDuskTint

    /** 切换晨昏暖色叠加：镜像即时生效（下次组合评估相位）+ 持久化 */
    fun setDawnDuskTint(value: Boolean) {
        _dawnDuskTint.value = value
        RuntimeSettings.dawnDuskTint = value
        viewModelScope.launch(Dispatchers.IO) {
            metaDao.put(MetaEntity(RuntimeSettings.KEY_DAWN_DUSK, value.toString()))
        }
    }

    // ---- 启动隐私锁（N18，默认关）：只挡入口不动数据 ----

    private val _biometricLock = MutableStateFlow(RuntimeSettings.biometricLock)
    val biometricLock: StateFlow<Boolean> = _biometricLock

    fun setBiometricLock(value: Boolean) {
        _biometricLock.value = value
        RuntimeSettings.biometricLock = value
        viewModelScope.launch(Dispatchers.IO) {
            metaDao.put(MetaEntity(RuntimeSettings.KEY_BIOMETRIC_LOCK, value.toString()))
        }
    }

    /** 切换界面语言：写 [RuntimeSettings.appLanguage] 快照状态（全树即时重组换文案）+ 持久化 meta */
    fun setLanguage(value: AppLanguage) {
        if (_language.value == value) return
        _language.value = value
        RuntimeSettings.appLanguage = value
        viewModelScope.launch(Dispatchers.IO) {
            metaDao.put(MetaEntity(KEY_LANGUAGE, value.name))
        }
    }

    /**
     * 修改口令：旧口令校验 → 新参数派生 → 全库胶囊与图片重加密。
     * 「期间禁止退出」由弹窗层保证（DialogProperties 屏蔽 back / 点击外部）。
     */
    fun changePassword(old: String, new: String) {
        val current = _passwordChange.value
        if (current != null && !current.finished) return // 进行中忽略重复提交
        _passwordChange.value = PasswordChangeState(done = 0, total = 0, finished = false, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                container.contentCryptoManager.changePassword(
                    old = old.toCharArray(),
                    new = new.toCharArray(),
                ) { done, total ->
                    _passwordChange.value = PasswordChangeState(done, total, finished = false, error = null)
                }
            }
            _passwordChange.value = result.fold(
                onSuccess = {
                    PasswordChangeState(done = 1, total = 1, finished = true, error = null)
                },
                onFailure = { throwable ->
                    PasswordChangeState(done = 0, total = 0, finished = true, error = throwable.message ?: currentStrings().pwModifyFailed)
                },
            )
        }
    }

    /** 消费完成/失败状态（弹窗关闭后调用，回到空闲） */
    fun consumePasswordChangeState() {
        _passwordChange.value = null
    }

    // ---- 自动定期本地备份（AutoBackupManager；配置读写 meta，执行复用 BackupManager 导出链路） ----

    /** 自动备份 UI 状态：配置 + 最近一次手动触发的结果（lastStatus 供 UI 映射文案）+ 随备份体检摘要（N22） */
    data class AutoBackupState(
        val enabled: Boolean = false,
        val periodDays: Int = com.muxiao.timart.utils.export.AutoBackupManager.DEFAULT_PERIOD_DAYS,
        val treeUri: String? = null,
        val lastAt: Long? = null,
        val running: Boolean = false,
        val lastStatus: com.muxiao.timart.utils.export.AutoBackupManager.Status? = null,
        val healthLastAt: Long? = null,
        val healthFindings: Int? = null,
    )

    private val _autoBackup = MutableStateFlow(AutoBackupState())
    val autoBackup: StateFlow<AutoBackupState> = _autoBackup

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _autoBackup.value = _autoBackup.value.from(container.autoBackupManager.readConfig())
        }
    }

    fun setAutoBackupEnabled(value: Boolean) {
        _autoBackup.value = _autoBackup.value.copy(enabled = value)
        viewModelScope.launch(Dispatchers.IO) {
            container.autoBackupManager.setEnabled(value)
        }
    }

    fun setAutoBackupPeriod(days: Int) {
        _autoBackup.value = _autoBackup.value.copy(periodDays = days)
        viewModelScope.launch(Dispatchers.IO) {
            container.autoBackupManager.setPeriodDays(days)
        }
    }

    /** 目录选择器回调：授权失败时 treeUri 保持空（UI 显示未选择） */
    fun setAutoBackupTree(uri: android.net.Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = container.autoBackupManager.setTreeUri(uri)
            val config = container.autoBackupManager.readConfig()
            _autoBackup.value = _autoBackup.value.copy(
                treeUri = if (ok) uri.toString() else config.treeUri,
            )
        }
    }

    /** 手动触发一次备份（会话未解锁返回 SKIPPED_LOCKED，由 UI 提示） */
    fun runAutoBackupNow() {
        val current = _autoBackup.value
        if (current.running) return
        _autoBackup.value = current.copy(running = true, lastStatus = null)
        viewModelScope.launch(Dispatchers.IO) {
            val status = container.autoBackupManager.runNow()
            _autoBackup.value = AutoBackupState(
                enabled = current.enabled,
                periodDays = current.periodDays,
                treeUri = current.treeUri,
                lastAt = container.autoBackupManager.readConfig().lastAt,
                running = false,
                lastStatus = status,
                healthLastAt = metaDao.get(com.muxiao.timart.utils.export.AutoBackupManager.KEY_HEALTH_LAST_AT)
                    ?.toLongOrNull(),
                healthFindings = metaDao.get(com.muxiao.timart.utils.export.AutoBackupManager.KEY_HEALTH_FINDINGS)
                    ?.toIntOrNull(),
            )
        }
    }

    private suspend fun AutoBackupState.from(config: com.muxiao.timart.utils.export.AutoBackupManager.Config) = copy(
        enabled = config.enabled,
        periodDays = config.periodDays,
        treeUri = config.treeUri,
        lastAt = config.lastAt,
        healthLastAt = metaDao.get(com.muxiao.timart.utils.export.AutoBackupManager.KEY_HEALTH_LAST_AT)
            ?.toLongOrNull(),
        healthFindings = metaDao.get(com.muxiao.timart.utils.export.AutoBackupManager.KEY_HEALTH_FINDINGS)
            ?.toIntOrNull(),
    )

    // ---- 历史备份浏览（N4：轮转目录现存份数列表 + 指定份恢复） ----

    private val _autoBackupHistory = MutableStateFlow(emptyList<com.muxiao.timart.utils.export.AutoBackupManager.HistoryCopy>())
    val autoBackupHistory: StateFlow<List<com.muxiao.timart.utils.export.AutoBackupManager.HistoryCopy>> =
        _autoBackupHistory

    fun loadAutoBackupHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _autoBackupHistory.value = container.autoBackupManager.listHistory()
        }
    }

    private companion object {
        const val KEY_TIER = RuntimeSettings.KEY_TIER
        const val KEY_LANGUAGE = RuntimeSettings.KEY_LANGUAGE
        const val KEY_GYRO = RuntimeSettings.KEY_GYRO
        const val KEY_INPUT_SPARK = RuntimeSettings.KEY_INPUT_SPARK
        const val KEY_SOUND = RuntimeSettings.KEY_SOUND
    }
}
