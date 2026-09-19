package com.muxiao.timart.utils.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.muxiao.timart.domain.context.SensorExtraProvider
import com.muxiao.timart.domain.model.unlock.PoseKind

/**
 * 传感器扩展快照读取（设备姿态 / 接近遮挡；储备池 v4 判定通道）。
 *
 * 与 [LightSensorReader] 同款常驻低频监听设计：无对应传感器时 pose/proximity
 * 恒返回 null（判定侧 fail-closed 给「传感器不可用」原因）；
 * 有传感器时缓存最近一次读数供同步查询。
 */
class SensorExtraReader(context: Context) : SensorExtraProvider {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    @Volatile private var lastPose: PoseKind? = null

    @Volatile private var lastProximityCovered: Boolean? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val y = event.values[1]
                    val z = event.values[2]
                    lastPose = when {
                        z >= FLAT_Z_THRESHOLD -> PoseKind.FLAT
                        z <= -FLAT_Z_THRESHOLD -> PoseKind.UPSIDE_DOWN
                        y >= UPRIGHT_Y_THRESHOLD -> PoseKind.UPRIGHT
                        else -> null // 倾斜过渡态：不归入三态，保持最近判定口径为"无姿态"
                    }
                }

                Sensor.TYPE_PROXIMITY ->
                    // 大多数接近传感器：近距返回远小于 max 的值（常为 0），远距返回 max
                    lastProximityCovered = event.values[0] < event.sensor.maximumRange * 0.5f
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** 首次查询时惰性注册常驻监听（只注册一次；registerListener 对重复注册安全，这里仍加守卫省开销） */
    @Volatile private var listening = false

    private fun ensureListening() {
        if (listening) return
        listening = true
        val manager = sensorManager ?: return
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
            manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun pose(): PoseKind? {
        ensureListening()
        return lastPose
    }

    override fun proximityCovered(): Boolean? {
        ensureListening()
        return lastProximityCovered
    }

    private companion object {
        /** 平放判定：z 轴重力分量阈值（m/s²，重力 ≈ 9.8） */
        const val FLAT_Z_THRESHOLD = 7.0f

        /** 直立判定：y 轴重力分量阈值 */
        const val UPRIGHT_Y_THRESHOLD = 7.0f
    }
}
