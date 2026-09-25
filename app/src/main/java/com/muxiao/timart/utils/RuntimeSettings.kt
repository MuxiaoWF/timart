package com.muxiao.timart.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muxiao.timart.domain.model.Lang
import java.util.Locale

/** 语言选项（「跟随系统」由读取侧解析成 [Lang]；UI 直接持有本枚举） */
enum class AppLanguage { SYSTEM, ZH_HANS, ZH_HANT, EN;

    /** 解析为生效语言：SYSTEM 按系统 locale，其余一一对应 */
    fun toLang(systemTag: String): Lang = when (this) {
        SYSTEM -> Lang.fromSystemTag(systemTag)
        ZH_HANS -> Lang.ZH_HANS
        ZH_HANT -> Lang.ZH_HANT
        EN -> Lang.EN
    }
}

/**
 * 进程内运行时设置镜像（meta 的读端，TASKS T13）：
 * - 冷启动由 TimartApplication 从 meta 加载兜底值；
 * - 设置页修改时由 SettingsViewModel 即时同步，创建页输入飘粒等消费方直接读取。
 * - [appLanguage] 为 Compose 快照状态（同 particleEngine 热替换模式）：设置页切换语言后，
 *   所有组合期读取它的界面自动重组换文案，无需重建 Activity。
 */
object RuntimeSettings {

    /** meta key 常量（唯一出处；SettingsViewModel 与 TimartApplication 共用） */
    const val KEY_TIER = "settings.tier"
    const val KEY_GYRO = "settings.gyro"
    const val KEY_INPUT_SPARK = "settings.inputSpark"
    const val KEY_SOUND = "settings.sound"
    const val KEY_LANGUAGE = "settings.language"
    const val KEY_BIOMETRIC_LOCK = "settings.biometricLock"
    const val KEY_DAWN_DUSK = "settings.dawnDusk"

    /** 口令提示语（储备池 v6；首次设置口令时可选写入，解锁弹窗显示；明文 meta，非胶囊级不进孤儿扫描） */
    const val KEY_PW_HINT = "settings.pwHint"

    /** 口令会话免输时长（储备池 v7；小时，24/72/168 三档，默认 72 = 原实现口径） */
    const val KEY_SESSION_TTL_HOURS = "settings.sessionTtlHours"

    /** 陀螺仪视差（首页星图轻微偏移；ARCHITECTURE §2.14 默认关） */
    @Volatile
    var gyroEnabled: Boolean = false

    /** 输入飘粒（创建页光标处上飘尘粒） */
    @Volatile
    var inputSparkEnabled: Boolean = true

    /** 音效（PENDING / UNSEAL / DISSOLVE 提示音；默认关闭，设置页显式开启） */
    @Volatile
    var soundEnabled: Boolean = false

    /**
     * 启动隐私锁（N18，默认关）：ON_RESUME 加一道 BiometricPrompt 门，防口令会话已解锁时被随手翻看。
     * 只挡入口不动数据——验证失败不销毁、不锁定，与 [KEY_SOUND] 等偏好同走 meta 镜像。
     */
    @Volatile
    var biometricLock: Boolean = false

    /**
     * 昼夜暖色变体（N17，默认关）：开启后清晨/黄昏时段给主题一层极低 alpha 暖色叠加，
     * 只读本地时钟零网络；叠加实现见 [com.muxiao.timart.ui.theme.DawnDuskTint]。
     */
    @Volatile
    var dawnDuskTint: Boolean = false

    /**
     * 口令会话免输时长（储备池 v7，小时）：影响下一次口令解锁后的 Vault 包裹过期时刻
     * （已存在的包裹副本按其原过期时刻，不追溯）。密钥本体与口令依旧从不落盘。
     */
    @Volatile
    var sessionTtlHours: Int = 72

    /** 界面语言（快照状态；默认跟随系统） */
    var appLanguage: AppLanguage by mutableStateOf(AppLanguage.SYSTEM)

    /** 当前生效语言（UI 组合期 / 通知与 Worker 等非组合层共用同一取值） */
    val resolvedLang: Lang
        get() = appLanguage.toLang(Locale.getDefault().toLanguageTag())
}
