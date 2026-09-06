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

    private companion object {
        val KEY_TIER = RuntimeSettings.KEY_TIER
        val KEY_LANGUAGE = RuntimeSettings.KEY_LANGUAGE
        val KEY_GYRO = RuntimeSettings.KEY_GYRO
        val KEY_INPUT_SPARK = RuntimeSettings.KEY_INPUT_SPARK
        val KEY_SOUND = RuntimeSettings.KEY_SOUND
    }
}
