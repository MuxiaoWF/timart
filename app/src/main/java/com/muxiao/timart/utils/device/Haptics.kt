package com.muxiao.timart.utils.device

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 胶囊触觉签名（体验储备池 §6）：解锁 / 销毁时的振动纹样，Vibrator 实现、零权限。
 * 每颗胶囊可选一种纹样（meta `capsule.haptic.<id>`，0 = 无），销毁事件整体放慢加重。
 * 振动只做轻反馈：跟随系统触觉设置，失败静默（不干扰任何业务路径）。
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * 播放纹样：[style] 1 = 双击 / 2 = 长振 / 3 = 涟漪（0 与未知 = 不振动）。
     * [destroy] = 销毁事件：节奏整体放慢加重（同纹样两种语义）。
     */
    fun play(style: Int, destroy: Boolean) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        var timings = when (style) {
            STYLE_DOUBLE -> longArrayOf(0, 35, 70, 35)
            STYLE_PULSE -> longArrayOf(0, 180)
            STYLE_RIPPLE -> longArrayOf(0, 25, 60, 25, 60, 25)
            else -> return
        }
        if (destroy) {
            timings = timings.map { if (it > 60) (it * 1.6).toLong() else it }.toLongArray()
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = timings.mapIndexed { i, t ->
                    if (i % 2 == 1) (if (destroy && t > 100) 200 else 255) else 0
                }.toIntArray()
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        }
    }

    companion object {
        const val STYLE_DOUBLE = 1
        const val STYLE_PULSE = 2
        const val STYLE_RIPPLE = 3
    }
}
