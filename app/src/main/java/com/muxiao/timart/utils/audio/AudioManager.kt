package com.muxiao.timart.utils.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager as SystemAudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import com.muxiao.timart.R

/**
 * 音效管理：SoundPool 短音效（PENDING/UNSEAL/DISSOLVE）+ MediaPlayer 环境音循环。
 * 统一受设置音效开关控制；音频素材为合成软音色（上行轻钟/琶音/下行渐弱/滤波噪声垫）。
 * 注意与 android.media.AudioManager 同名，内部用 [SystemAudioManager] 别名。
 */
class AudioManager(context: Context) {

    private val systemAudioManager: SystemAudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? SystemAudioManager

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val pendingId: Int = soundPool.load(context, R.raw.pending, 1)
    private val unsealId: Int = soundPool.load(context, R.raw.unseal, 1)
    private val dissolveId: Int = soundPool.load(context, R.raw.dissolve, 1)

    @Volatile
    private var enabled = false

    private var ambientPlayer: MediaPlayer? = null

    /** 音效开关（设置页） */
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) stopAmbient()
    }

    private fun play(soundId: Int) {
        if (!enabled) return
        try {
            val currentVolume = systemAudioManager?.let {
                it.getStreamVolume(SystemAudioManager.STREAM_MUSIC).toFloat() /
                    it.getStreamMaxVolume(SystemAudioManager.STREAM_MUSIC).coerceAtLeast(1)
            } ?: 1f
            soundPool.play(soundId, currentVolume, currentVolume, 1, 0, 1f)
        } catch (_: Throwable) {
            // 音效失败不干扰主流程
        }
    }

    /** 条件满足单次反馈 */
    fun playPending() = play(pendingId)

    /** 揭封序列音效 */
    fun playUnseal() = play(unsealId)

    /** 消散音效 */
    fun playDissolve() = play(dissolveId)

    fun stopAmbient() {
        try {
            ambientPlayer?.stop()
            ambientPlayer?.release()
        } catch (_: Throwable) {
            // 忽略重复释放
        }
        ambientPlayer = null
    }
}
