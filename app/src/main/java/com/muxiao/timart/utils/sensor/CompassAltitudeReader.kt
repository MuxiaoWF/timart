package com.muxiao.timart.utils.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.muxiao.timart.domain.context.AltitudeProvider
import com.muxiao.timart.domain.context.CompassProvider

/**
 * 指南针 + 气压计海拔读取。
 * 传感器监听常驻（SENSOR_DELAY_UI 低频），快照直接读缓存值：
 * - 方位角：TYPE_ROTATION_VECTOR → 地磁北向 0–359°
 * - 海拔：TYPE_PRESSURE → 标准大气换算（米）；无气压计返回 null
 * 磁力计/气压计缺失时分别返回 null，判定侧按「不可用」给原因。
 */
class CompassAltitudeReader(context: Context) : CompassProvider, AltitudeProvider {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    @Volatile
    private var heading: Float? = null

    @Volatile
    private var pressureHpa: Float? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val rotation = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotation, orientation)
                    // 方位角（绕 -Z），弧度 → 0–359°
                    val azimuth = Math.toDegrees(orientation[0].toDouble())
                    heading = ((azimuth + 360.0) % 360.0).toFloat()
                }

                Sensor.TYPE_PRESSURE -> pressureHpa = event.values[0]
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    init {
        // K2：init 块内禁止 return，用 ?.let 表达"无传感器服务则不注册"
        sensorManager?.let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
                sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
            }
            sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
                sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun headingDeg(): Float? = heading

    override fun altitudeMeters(): Double? = pressureHpa?.let {
        SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it).toDouble()
    }
}
