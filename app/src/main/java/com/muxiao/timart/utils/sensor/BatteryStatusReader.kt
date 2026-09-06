package com.muxiao.timart.utils.sensor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.muxiao.timart.domain.context.BatteryInfo
import com.muxiao.timart.domain.context.BatteryProvider

/**
 * 电池状态读取：粘性广播 ACTION_BATTERY_CHANGED（无实时监听成本）。
 * 实现 BatteryProvider 接口。
 */
class BatteryStatusReader(private val context: Context) : BatteryProvider {

    override fun battery(): BatteryInfo = try {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) {
            BatteryInfo(levelPercent = null, isCharging = false)
        } else {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                plugged != 0
            BatteryInfo(levelPercent = percent, isCharging = charging)
        }
    } catch (_: Throwable) {
        BatteryInfo(levelPercent = null, isCharging = false)
    }
}
