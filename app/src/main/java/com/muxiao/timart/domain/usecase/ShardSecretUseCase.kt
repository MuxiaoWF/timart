package com.muxiao.timart.domain.usecase

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 口令分片（体验储备池 §7.1 落地）：GF(256) 上的 Shamir 秘密共享，纯 Kotlin 可 JVM 单测。
 *
 * - [split]：32 字节秘密 S → total 份分片（threshold M 份可重构，M-1 份不泄露任何信息）；
 *   逐字节构造 M-1 次随机多项式（常数项 = 秘密字节），分片值 = 多项式在 x=index 处的取值；
 * - [combine]：M 份分片 Lagrange 插值求 x=0 → 重构 S；份额不足 / 索引重复 / 长度不一致返回 null；
 * - 分片串编解码：`TIMART-SHARD-<total>-<threshold>-<index>-<hex>`（hex = 32B 分片值），
 *   只在封存成功时展示一次，由封存者线下分发给 N 位持有人，**设备不留任何份额**。
 *
 * GF(256)：加/减 = XOR，乘除用 AES 多项式 0x11b（生成元 3 的 log/exp 表）。
 */
class ShardSecretUseCase {

    /** 单份分片：index 1..total（x 坐标），bytes 为与秘密等长的分片值 */
    data class Share(val index: Int, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Share && other.index == index && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = index * 31 + bytes.contentHashCode()
    }

    /** 分片串解析结果 */
    data class DecodedShare(val total: Int, val threshold: Int, val index: Int, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is DecodedShare && other.total == total && other.threshold == threshold &&
                other.index == index && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = ((total * 31 + threshold) * 31 + index) * 31 + bytes.contentHashCode()
    }

    /** 拆分秘密（threshold ≥ 2、total > threshold、total ≤ [MAX_TOTAL]；参数越界抛 IllegalArgumentException） */
    fun split(secret: ByteArray, threshold: Int, total: Int, random: SecureRandom = SecureRandom()): List<Share> {
        require(secret.size == SECRET_BYTES) { "secret must be $SECRET_BYTES bytes" }
        require(threshold >= 2) { "threshold must be >= 2" }
        require(total > threshold) { "total must be > threshold" }
        require(total <= MAX_TOTAL) { "total must be <= $MAX_TOTAL" }
        val shares = List(total) { ByteArray(secret.size) }
        // 逐字节独立构造多项式：系数随机，常数项 = 秘密字节
        for ((byteIndex, secretByte) in secret.withIndex()) {
            val coefficients = ByteArray(threshold)
            coefficients[0] = secretByte
            val randomTail = ByteArray(threshold - 1)
            random.nextBytes(randomTail)
            System.arraycopy(randomTail, 0, coefficients, 1, threshold - 1)
            for (shareIndex in 1..total) {
                shares[shareIndex - 1][byteIndex] = evalPoly(coefficients, shareIndex).toByte()
            }
        }
        return shares.mapIndexed { i, bytes -> Share(index = i + 1, bytes = bytes) }
    }

    /** 重构秘密：取 M 份做 Lagrange 插值求 x=0；输入不合法（份额不足 / 索引重复 / 长度不一致）返回 null */
    fun combine(shares: List<Share>, threshold: Int): ByteArray? {
        if (threshold < 2) return null
        if (shares.size < threshold) return null
        val used = shares.distinctBy { it.index }.sortedBy { it.index }.take(threshold)
        if (used.size < threshold) return null
        val size = used[0].bytes.size
        if (used.any { it.bytes.size != size }) return null

        // 每个节点的 Lagrange 权重（求值点 x=0）：w_j = Π_{m≠j} x_m / (x_m ⊕ x_j)
        val weights = IntArray(used.size)
        for (j in used.indices) {
            var weight = 1
            for (m in used.indices) {
                if (m == j) continue
                val delta = used[m].index xor used[j].index
                if (delta == 0) return null
                weight = gf256Mul(weight, gf256Mul(used[m].index, gf256Inverse(delta)))
            }
            weights[j] = weight
        }

        val secret = ByteArray(size)
        for (byteIndex in 0 until size) {
            var value = 0
            for (j in used.indices) {
                value = value xor gf256Mul(used[j].bytes[byteIndex].toInt() and 0xFF, weights[j])
            }
            secret[byteIndex] = value.toByte()
        }
        return secret
    }

    /** 分片值 = x 处多项式取值（coefficients[0] 为常数项，Horner 从最高次起算） */
    private fun evalPoly(coefficients: ByteArray, x: Int): Int {
        var result = 0
        for (i in coefficients.indices.reversed()) {
            result = gf256Mul(result, x) xor (coefficients[i].toInt() and 0xFF)
        }
        return result
    }

    /** 分片串编码（total/threshold 随串携带，持有人无需记忆参数） */
    fun encodeShare(share: Share, total: Int, threshold: Int): String =
        "$SHARE_PREFIX$total-$threshold-${share.index}-${share.bytes.joinToString("") { "%02x".format(it) }}"

    /** 分片串解析；前缀 / 参数 / 索引 / hex 任一不合法返回 null */
    fun decodeShare(text: String): DecodedShare? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(SHARE_PREFIX)) return null
        val parts = trimmed.removePrefix(SHARE_PREFIX).split('-')
        if (parts.size != 4) return null
        val total = parts[0].toIntOrNull() ?: return null
        val threshold = parts[1].toIntOrNull() ?: return null
        val index = parts[2].toIntOrNull() ?: return null
        if (total < 2 || threshold < 2 || threshold >= total || index !in 1..total) return null
        val hex = parts[3]
        if (hex.length != SECRET_BYTES * 2) return null
        val bytes = ByteArray(SECRET_BYTES)
        for (i in bytes.indices) {
            val byte = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
            bytes[i] = byte.toByte()
        }
        return DecodedShare(total, threshold, index, bytes)
    }

    /** k2 派生输入的口令形态：hex 编码保证字节稳定（ASCII 形态在 UTF-8/Argon2 内部编码下不变） */
    fun secretToPassword(secret: ByteArray): CharArray =
        secret.joinToString("") { "%02x".format(it) }.toCharArray()

    /** verifier₂ = SHA-256(k2)（hex 小写；比对用 [MessageDigest.isEqual] 在调用方完成） */
    fun verifierHex(k2: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(k2).joinToString("") { "%02x".format(it) }

    // ---- GF(256)（AES 多项式 0x11b，生成元 3） ----

    private val expTable = IntArray(512)
    private val logTable = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            expTable[i] = x
            // x = x · 3 = (x · 2) ⊕ x，乘 2 的约减多项式 0x11b
            val doubled = ((x shl 1) and 0xFF) xor (if (x and 0x80 != 0) 0x1b else 0)
            x = doubled xor x
        }
        for (i in 0 until 255) {
            logTable[expTable[i]] = i
        }
        for (i in 255 until 512) {
            expTable[i] = expTable[i - 255]
        }
    }

    private fun gf256Mul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return expTable[logTable[a] + logTable[b]]
    }

    private fun gf256Inverse(a: Int): Int {
        require(a != 0) { "inverse of 0" }
        return expTable[255 - logTable[a]]
    }

    companion object {
        const val SECRET_BYTES = 32
        const val MAX_TOTAL = 6
        const val SHARE_PREFIX = "TIMART-SHARD-"
    }
}
