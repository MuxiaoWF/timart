package com.muxiao.timart.utils.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.muxiao.timart.data.local.crypto.AudioCipherStore
import java.io.File

/**
 * 语音留言播放器（体验储备池 §1 声音留言）：
 * 解密语音字节 → 落 cacheDir 临时文件 → MediaPlayer 播放，播完/停止即释放并删除明文缓存。
 * 音频字节永不落任何持久存储；方法须在 IO 协程调用（prepare 为阻塞调用）。
 */
class VoicePlayer(
    context: Context,
    private val audioStore: AudioCipherStore,
) {

    private val appContext = context.applicationContext

    private var player: MediaPlayer? = null

    /** 当前是否在播放（UI 状态轮询/回调用；MediaPlayer 非法态按未播放处理） */
    val isPlaying: Boolean
        get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /** 播放/停止切换：返回切换后的播放态（播放失败返回 false，不打断主流程） */
    fun toggle(capsuleId: String): Boolean {
        if (isPlaying) {
            stop()
            return false
        }
        return start(capsuleId)
    }

    /** 解密 → 写临时文件 → 播放；语音缺失或损坏返回 false（fail-closed，UI 不出播放态） */
    fun start(capsuleId: String): Boolean {
        stop()
        val plain = runCatching { audioStore.read(capsuleId, 0) }.getOrNull() ?: return false
        val cache = File(appContext.cacheDir, "timart_voice_play.m4a")
        try {
            cache.writeBytes(plain)
            plain.fill(0)
            val p = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(cache.absolutePath)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            player = p
            return true
        } catch (_: Throwable) {
            runCatching { player?.release() }
            player = null
            cache.delete()
            return false
        }
    }

    /** 停止并释放 + 删除明文缓存（明文语音不留盘） */
    fun stop() {
        val p = player
        player = null
        runCatching {
            if (p != null) {
                if (p.isPlaying) p.stop()
                p.release()
            }
        }
        runCatching { File(appContext.cacheDir, "timart_voice_play.m4a").delete() }
    }
}
