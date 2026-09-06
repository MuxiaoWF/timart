package com.muxiao.timart.utils.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

/**
 * 陀螺仪视差（ARCHITECTURE §2.14，设置页「陀螺仪视差」开关的消费端）：
 * 加速度计重力向量 → 设备倾斜（左右 / 前后，归一化 -1..1），低通平滑后以
 * Compose 快照状态 [tilt] 暴露。时轨页对「星野 / 轨道 / 球体」三层施加
 * 不同幅度偏移形成深度视差；开关关闭或传感器缺失时不注册监听。
 *
 * 用 TYPE_ACCELEROMETER（无需权限、全机型可用）；监听回调只更新两个 Float，
 * 绘制端在 draw 相位延迟读取（视差变化只触发重绘，不触发重组）。
 */
class ParallaxSensor(context: Context) {

    /** 平滑后的倾斜量：x = 左右倾（-1..1），y = 前后倾（-1..1，0 = 竖直持机） */
    var tilt by mutableStateOf(Offset.Zero)
        private set

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var smoothX = 0f
    private var smoothY = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = (event.values[0] / GRAVITY).coerceIn(-1f, 1f)
            val y = ((event.values[1] - GRAVITY) / GRAVITY).coerceIn(-1f, 1f)
            // 低通滤波：抑制手部高频抖动，保留缓慢倾斜意图
            smoothX += (x - smoothX) * SMOOTHING
            smoothY += (y - smoothY) * SMOOTHING
            // 量化到 1/24 步进：接近静止时不写状态，避免层更新风暴（发热优化）
            val qx = (smoothX * 24).roundToInt() / 24f
            val qy = (smoothY * 24).roundToInt() / 24f
            if (qx != tilt.x || qy != tilt.y) {
                tilt = Offset(qx, qy)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** 注册监听（已在监听中时重复注册是幂等的） */
    fun start() {
        val m = manager ?: return
        val s = sensor ?: return
        smoothX = tilt.x
        smoothY = tilt.y
        // UI 档采样（~15Hz）：视差/倾斜都是慢速氛围运动，低通滤波后足够顺滑，且比 GAME 档省电
        m.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
    }

    /** 注销监听（倾角归零：关闭开关后卡片/星图应回到正位） */
    fun stop() {
        manager?.unregisterListener(listener)
        tilt = Offset.Zero
        smoothX = 0f
        smoothY = 0f
    }

    private companion object {
        const val GRAVITY = 9.81f
        const val SMOOTHING = 0.15f
    }
}
