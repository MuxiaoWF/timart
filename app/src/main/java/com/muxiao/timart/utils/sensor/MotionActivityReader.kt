package com.muxiao.timart.utils.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.muxiao.timart.domain.context.MotionActivityProvider
import com.muxiao.timart.domain.model.unlock.MotionKind

/**
 * 运动状态读取（无 GMS 设备的启发式实现，架构红线：不引入 play-services 依赖）。
 *
 * - 最近 [WALKING_WINDOW_MILLIS] 内有计步检测（TYPE_STEP_DETECTOR）事件 → WALKING
 * - 否则 → STILL
 * - 域模型 MotionKind 仅保留 STILL/WALKING 两态（无 GMS 无法可靠区分跑步/骑行/驾车），
 *   表单注释已同步说明该系统限制。
 */
class MotionActivityReader(context: Context) : MotionActivityProvider {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** 最近一次计步检测事件时刻（uptime 毫秒） */
    @Volatile
    private var lastStepDetectUptime: Long = 0L

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                lastStepDetectUptime = System.currentTimeMillis()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    init {
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun current(): MotionKind? {
        if (sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) == null) return null
        return if (System.currentTimeMillis() - lastStepDetectUptime <= WALKING_WINDOW_MILLIS) {
            MotionKind.WALKING
        } else {
            MotionKind.STILL
        }
    }

    private companion object {
        /** 步行判定窗口：2 分钟内的计步事件视为步行中 */
        const val WALKING_WINDOW_MILLIS = 2 * 60 * 1000L
    }
}
