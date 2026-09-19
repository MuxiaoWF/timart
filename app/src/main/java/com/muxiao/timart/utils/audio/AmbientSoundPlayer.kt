package com.muxiao.timart.utils.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.muxiao.timart.utils.RuntimeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * 环境音场景（体验储备池 §6）：雨夜 / 深空嗡鸣循环音，解封淡入、离场淡出。
 *
 * 声音在播放前用确定性合成即时生成（2.205 万 Hz 单声道、4 秒循环 + 首尾交叉淡化接缝），
 * **不新增任何音频资源文件**——守住体积红线（坑 #17）。AudioTrack MODE_STATIC 循环，
 * 音量经步进斜坡淡入淡出；受设置页音效开关统一约束（RuntimeSettings.soundEnabled）。
 */
class AmbientSoundPlayer {

    enum class Scene { OFF, RAIN, DRONE }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var track: AudioTrack? = null
    private var rampJob: Job? = null

    /** 开始循环并淡入；已在播放则先停旧再起新 */
    fun start(scene: Scene) {
        stop()
        if (scene == Scene.OFF || !RuntimeSettings.soundEnabled) return
        val pcm = synthesize(scene) ?: return
        val bytes = ByteArray(pcm.size * 2)
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer().put(pcm)
        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bytes.size)
            .build()
        runCatching {
            newTrack.write(pcm, 0, pcm.size)
            newTrack.setLoopPoints(0, pcm.size, -1)
            newTrack.play()
        }.onFailure {
            runCatching { newTrack.release() }
            return
        }
        track = newTrack
        rampJob = scope.launch {
            ramp(newTrack, 0f, TARGET_VOLUME, FADE_IN_MILLIS)
        }
    }

    /** 淡出后释放 */
    fun stop() {
        rampJob?.cancel()
        rampJob = null
        val current = track
        track = null
        if (current != null) {
            scope.launch {
                ramp(current, TARGET_VOLUME, 0f, FADE_OUT_MILLIS)
                runCatching {
                    current.stop()
                    current.release()
                }
            }
        }
    }

    /** 释放全部资源（宿主 VM onCleared 兜底）：立即停，不走淡出协程（作用域将随之取消） */
    fun shutdown() {
        rampJob?.cancel()
        rampJob = null
        val current = track
        track = null
        runCatching {
            current?.stop()
            current?.release()
        }
        scope.cancel()
    }

    private suspend fun ramp(track: AudioTrack, from: Float, to: Float, millis: Long) {
        val steps = (millis / 50).coerceAtLeast(1)
        var i = 1
        while (i <= steps && currentCoroutineContext().isActive) {
            if (track !== this@AmbientSoundPlayer.track && to > from) return // 已被新场景顶替
            runCatching { track.setVolume(from + (to - from) * i / steps) }
            delay(50.milliseconds)
            i++
        }
        runCatching { track.setVolume(to) }
    }

    /** 合成 4 秒无缝循环（首尾 250ms 交叉淡化接缝） */
    private fun synthesize(scene: Scene): ShortArray? {
        val frames = SAMPLE_RATE * SECONDS
        val fade = (SAMPLE_RATE * 0.25).toInt()
        val pcm = ShortArray(frames)
        val rnd = java.util.Random(SCENE_SEED + scene.ordinal)
        var brown = 0.0
        val raw = DoubleArray(frames + fade)
        when (scene) {
            Scene.RAIN -> {
                for (i in raw.indices) {
                    val white = rnd.nextDouble() * 2 - 1
                    // 布朗噪声：低通化白噪声 + 慢速强度起伏（雨幕）
                    brown = (brown + 0.02 * white) / 1.02
                    val lfo = 0.72 + 0.28 * sin(2 * PI * i / raw.size * 3)
                    raw[i] = brown * 3.4 * lfo
                }
            }

            Scene.DRONE -> {
                for (i in raw.indices) {
                    val t = i.toDouble() / SAMPLE_RATE
                    // 三个低频正弦的拍频簇 + 慢呼吸
                    val value = 0.5 * sin(2 * PI * 55 * t) +
                        0.3 * sin(2 * PI * 110 * t) +
                        0.2 * sin(2 * PI * 110.9 * t)
                    val tremolo = 0.7 + 0.3 * sin(2 * PI * 0.25 * t)
                    raw[i] = value * tremolo * 0.6
                }
            }

            else -> return null
        }
        // 首尾交叉淡化：把尾段叠进头段，形成无缝循环
        for (i in 0 until frames) {
            var value = raw[i]
            if (i < fade) {
                val w = i.toDouble() / fade
                value = raw[i] * w + raw[frames + i] * (1 - w)
            }
            pcm[i] = (value * Short.MAX_VALUE).toInt()
                .coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm
    }

    companion object {
        private const val SAMPLE_RATE = 22_050
        private const val SECONDS = 4
        private const val TARGET_VOLUME = 0.35f
        private const val FADE_IN_MILLIS = 1_500L
        private const val FADE_OUT_MILLIS = 400L
        private const val SCENE_SEED = 20260919L
    }
}
