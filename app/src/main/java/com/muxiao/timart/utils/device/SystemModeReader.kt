package com.muxiao.timart.utils.device

import android.app.AlarmManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.muxiao.timart.domain.context.AlarmProvider
import com.muxiao.timart.domain.context.SystemModeProvider

/**
 * 系统模式快照读取（闹钟/省电/静音/耳机/飞行模式/媒体播放）：全部同步零回调，
 * 每次判定即时读取，无缓存陈旧问题。任何服务缺失按安全默认值返回。
 */
class SystemModeReader(context: Context) : AlarmProvider, SystemModeProvider {

    private val appContext = context.applicationContext
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun nextAlarmMillis(): Long? = alarmManager?.nextAlarmClock?.triggerTime

    override fun isPowerSave(): Boolean = powerManager?.isPowerSaveMode ?: false

    override fun isSilentRinger(): Boolean = audioManager?.let {
        it.ringerMode != AudioManager.RINGER_MODE_NORMAL
    } ?: false

    override fun isHeadphoneConnected(): Boolean {
        val audio = audioManager ?: return false
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outputs.any { device ->
            device.type in HEADPHONE_TYPES
        }
    }

    override fun isAirplaneModeOn(): Boolean = runCatching {
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    }.getOrDefault(false)

    @Suppress("DEPRECATION") // isMusicActive 在 API 26 起被标记弃用但功能正常，且为唯一零权限同步查询通道
    override fun isMusicPlaying(): Boolean = audioManager?.isMusicActive ?: false

    private companion object {
        // TYPE_BLE_HEADSET 为 API 31 新增字段（编译期常量内联，运行时本就安全）；
        // 引用置于版本守卫内以通过 lint 检查
        val HEADPHONE_TYPES: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        }
    }
}
