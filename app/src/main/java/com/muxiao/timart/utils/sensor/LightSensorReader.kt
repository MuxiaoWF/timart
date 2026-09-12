package com.muxiao.timart.utils.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.muxiao.timart.domain.context.AmbientLightProvider

/**
 * 环境光读取（AmbientLight 条件判定通道）。
 * 传感器监听常驻（SENSOR_DELAY_UI 低频），快照直接读缓存值：
 * - 照度 lux：TYPE_LIGHT 原始值；
 * - 无光线传感器返回 null，判定侧按「不可用」给原因。
 */
class LightSensorReader(context: Context) : AmbientLightProvider {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    @Volatile
    private var lux: Float? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_LIGHT) lux = event.values[0]
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    init {
        // K2：init 块内禁止 return，用 ?.let 表达"无传感器服务则不注册"
        sensorManager?.let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
                sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun lux(): Float? = lux
}
