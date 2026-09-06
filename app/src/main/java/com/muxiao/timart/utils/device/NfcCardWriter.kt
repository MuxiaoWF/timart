package com.muxiao.timart.utils.device

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.muxiao.timart.domain.model.unlock.NfcPairing

/**
 * NFC 配对写卡器（独立于 Compose，android 侧编解码枢纽）。
 *
 * - 写入：一条 NDEF 外部类型记录（类型 `timart.com:capsule`，载荷=challengeId），
 *   已格式化标签走 [Ndef]，未格式化标签走 [NdefFormatable] 现场格式化后写入；
 * - 读取：reader mode 拿到 [Tag] 后读取 cachedNdefMessage，筛出本 App 配对记录的载荷。
 *
 * 失败分支显式枚举（标签不支持 NDEF / 容量不足 / IO 异常），UI 侧按 reason 出文案。
 */
object NfcCardWriter {

    enum class FailReason { NO_NDEF, TOO_SMALL, IO }

    sealed interface Result {
        data object Success : Result
        data class Failure(val reason: FailReason) : Result
    }

    /** 把该挑战的配对记录写入卡贴 */
    fun writePairing(tag: Tag, challengeId: String): Result {
        val message = NdefMessage(
            NdefRecord.createExternal(
                NfcPairing.EXTERNAL_DOMAIN,
                NfcPairing.EXTERNAL_TYPE,
                NfcPairing.payloadFor(challengeId).toByteArray(Charsets.UTF_8),
            ),
        )
        val bytes = message.toByteArray()
        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                try {
                    if (ndef.maxSize < bytes.size) return Result.Failure(FailReason.TOO_SMALL)
                    ndef.writeNdefMessage(message)
                } finally {
                    runCatching { ndef.close() }
                }
                Result.Success
            } else {
                val formatable = NdefFormatable.get(tag) ?: return Result.Failure(FailReason.NO_NDEF)
                formatable.connect()
                try {
                    formatable.format(message)
                } finally {
                    runCatching { formatable.close() }
                }
                Result.Success
            }
        } catch (e: Exception) {
            Result.Failure(FailReason.IO)
        }
    }

    /** 读取卡上全部本 App 配对记录的载荷（cachedNdefMessage 免 connect，避免与系统分发竞态） */
    fun readPairingPayloads(tag: Tag): List<String> {
        val ndef = Ndef.get(tag) ?: return emptyList()
        return runCatching {
            ndef.cachedNdefMessage?.records
                ?.filter { NfcPairing.isTimartRecord(String(it.type, Charsets.UTF_8)) }
                ?.map { String(it.payload, Charsets.UTF_8) }
                .orEmpty()
        }.getOrElse { emptyList() }
    }
}
