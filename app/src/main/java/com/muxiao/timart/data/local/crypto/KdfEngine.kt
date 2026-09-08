package com.muxiao.timart.data.local.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * KDF 参数（随库持久化于 meta.kdf.params，§5.3）。
 *
 * ARGON2ID：{ algo, salt(b64), memoryKb, iterations, parallelism }
 * PBKDF2：  { algo, salt(b64), iterations }
 */
@Serializable
data class KdfParams(
    val algo: String,
    val salt: String,
    val memoryKb: Int? = null,
    val iterations: Int,
    val parallelism: Int? = null,
)

/**
 * 密钥派生引擎接口：口令 → 256 位内容密钥。
 */
interface KdfEngine {
    /** 派生 32 字节密钥；实现不得缓存口令或密钥 */
    fun derive(password: CharArray, params: KdfParams): ByteArray
}

/** Argon2id 实现（BouncyCastle 纯 Java 实现，API 24 可用），默认 64MB / t=3 / p=1 */
class Argon2Kdf : KdfEngine {

    override fun derive(password: CharArray, params: KdfParams): ByteArray {
        val salt = KdfEngines.decodeSalt(params.salt)
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withIterations(params.iterations)
            .withMemoryAsKB(params.memoryKb ?: DEFAULT_MEMORY_KB)
            .withParallelism(params.parallelism ?: DEFAULT_PARALLELISM)
        val generator = Argon2BytesGenerator()
        generator.init(builder.build())
        val out = ByteArray(KEY_LENGTH_BYTES)
        generator.generateBytes(password, out, 0, out.size)
        return out
    }

    companion object {
        const val DEFAULT_MEMORY_KB = 65536 // 64MB
        const val DEFAULT_ITERATIONS = 3
        const val DEFAULT_PARALLELISM = 1
    }
}

/** PBKDF2-HMAC-SHA256 回退实现（迭代 ≥310000） */
class Pbkdf2Kdf : KdfEngine {

    override fun derive(password: CharArray, params: KdfParams): ByteArray {
        val salt = KdfEngines.decodeSalt(params.salt)
        val spec = PBEKeySpec(password, salt, params.iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    companion object {
        const val DEFAULT_ITERATIONS = 310000
        private const val KEY_LENGTH_BITS = 256
    }
}

/**
 * KDF 工厂与派生编排。
 * Argon2id 派生异常（含 OOM 风险）时回退 PBKDF2，最终参数持久化由调用方落 meta 表。
 */
object KdfEngines {

    const val ALGO_ARGON2ID = "ARGON2ID"
    const val ALGO_PBKDF2 = "PBKDF2"

    private const val SALT_LENGTH_BYTES = 16

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val random = SecureRandom()

    /** 按 algo 返回对应引擎（未知算法一律视为 PBKDF2，向前兼容） */
    fun byAlgo(algo: String): KdfEngine =
        if (algo == ALGO_ARGON2ID) Argon2Kdf() else Pbkdf2Kdf()

    /** 生成 16B 随机盐 */
    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }

    /** Argon2id 默认参数 */
    fun defaultParams(salt: ByteArray): KdfParams = KdfParams(
        algo = ALGO_ARGON2ID,
        salt = encodeSalt(salt),
        memoryKb = Argon2Kdf.DEFAULT_MEMORY_KB,
        iterations = Argon2Kdf.DEFAULT_ITERATIONS,
        parallelism = Argon2Kdf.DEFAULT_PARALLELISM,
    )

    /** PBKDF2 回退参数 */
    fun fallbackParams(salt: ByteArray): KdfParams = KdfParams(
        algo = ALGO_PBKDF2,
        salt = encodeSalt(salt),
        iterations = Pbkdf2Kdf.DEFAULT_ITERATIONS,
    )

    /**
     * 派生（带回退）：Argon2id 派生抛出任何异常（含 OutOfMemoryError）时
     * 自动切换 PBKDF2 参数重派，返回最终实际使用的参数与密钥。
     */
    fun deriveWithFallback(password: CharArray, params: KdfParams): DerivedKey = try {
        DerivedKey(params = params, key = byAlgo(params.algo).derive(password, params))
    } catch (t: Throwable) {
        if (params.algo == ALGO_ARGON2ID) {
            // Argon2 不可用 → PBKDF2 回退（新盐，最终参数由调用方持久化）
            val fallback = fallbackParams(decodeSalt(params.salt))
            DerivedKey(params = fallback, key = Pbkdf2Kdf().derive(password, fallback))
        } else {
            throw t
        }
    }

    /** 派生结果：实际使用的参数（回退后可能与传入不同）+ 32B 密钥 */
    data class DerivedKey(val params: KdfParams, val key: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DerivedKey

            if (params != other.params) return false
            if (!key.contentEquals(other.key)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = params.hashCode()
            result = 31 * result + key.contentHashCode()
            return result
        }
    }

    /** KdfParams → JSON（落 meta 表用） */
    fun paramsToJson(params: KdfParams): String = json.encodeToString(KdfParams.serializer(), params)

    /** JSON → KdfParams */
    fun paramsFromJson(raw: String): KdfParams = json.decodeFromString(KdfParams.serializer(), raw)

    internal fun encodeSalt(salt: ByteArray): String =
        java.util.Base64.getEncoder().withoutPadding().encodeToString(salt)

    internal fun decodeSalt(b64: String): ByteArray {
        val decoded = java.util.Base64.getDecoder().decode(b64)
        require(decoded.isNotEmpty()) { "空盐值" }
        return decoded
    }

    /** 通用 b64 编码（Verifier 密文落 meta 表用） */
    fun b64Encode(bytes: ByteArray): String = encodeSalt(bytes)

    /** 通用 b64 解码 */
    fun b64Decode(b64: String): ByteArray = decodeSalt(b64)
}

/** 与 KEY_LENGTH_BYTES 对齐的公共常量（AES-256） */
internal const val KEY_LENGTH_BYTES = 32
