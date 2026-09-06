package com.muxiao.timart.utils.device

import android.app.AlarmManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.PowerManager
import com.muxiao.timart.domain.context.AlarmProvider
import com.muxiao.timart.domain.context.SystemModeProvider

/**
 * 系统模式快照读取（闹钟/省电/静音/耳机）：全部同步零回调，
 * 每次判定即时读取，无缓存陈旧问题。任何服务缺失按安全默认值返回。
 */
class SystemModeReader(context: Context) : AlarmProvider, SystemModeProvider {

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

    private companion object {
        val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
