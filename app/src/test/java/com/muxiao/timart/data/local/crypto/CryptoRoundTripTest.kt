package com.muxiao.timart.data.local.crypto

import com.muxiao.timart.data.local.db.MetaDao
import com.muxiao.timart.data.local.db.entity.MetaEntity
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.repository.CapsuleRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 加密体系往返单测：真实跑 Argon2id（1–3s 接受）+ PBKDF2 回退 + AES-GCM + Verifier */
class CryptoRoundTripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---- 测试用假实现 ----

    private class FakeMetaDao : MetaDao {
        val store = mutableMapOf<String, String>()

        override suspend fun get(key: String): String? = store[key]

        override fun getSync(key: String): String? = store[key]

        override fun observe(key: String): Flow<String?> = flow { emit(store[key]) }

        override suspend fun put(entity: MetaEntity) {
            store[entity.key] = entity.value
        }

        override fun putSync(entity: MetaEntity) {
            store[entity.key] = entity.value
        }
    }

    private class FakeCapsuleRepository : CapsuleRepository {
        val items = mutableListOf<Capsule>()

        override fun observeAll(): Flow<List<Capsule>> = flow { emit(items.toList()) }

        override fun observeById(id: String): Flow<Capsule?> = flow { emit(items.find { it.id == id }) }

        override fun byIdSync(id: String): Capsule? = items.find { it.id == id }

        override suspend fun allSync(): List<Capsule> = items.toList()

        override suspend fun allLockedSync(): List<Capsule> = items.filter { it.state == CapsuleState.LOCKED }

        override suspend fun insert(capsule: Capsule) {
            items.add(capsule)
        }

        override suspend fun update(capsule: Capsule) {
            val index = items.indexOfFirst { it.id == capsule.id }
            if (index >= 0) items[index] = capsule else items.add(capsule)
        }

        override suspend fun updateState(id: String, state: CapsuleState, atMillis: Long?) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) {
                items[index] = items[index].copy(
                    state = state,
                    unlockTimestamp = if (state == CapsuleState.UNLOCKED) atMillis else items[index].unlockTimestamp,
                )
            }
        }

        override suspend fun updateLayout(id: String, layoutX: Float?, layoutY: Float?) = Unit
        override suspend fun updateAutoDestroyAfterRead(id: String, value: Boolean) = Unit
        override suspend fun updateUnlockRule(id: String, rule: UnlockRule) = Unit

        override suspend fun delete(id: String) {
            items.removeAll { it.id == id }
        }
    }

    private fun newManager(): Triple<ContentCryptoManager, FakeMetaDao, FakeCapsuleRepository> {
        val cipher = AesGcmCipher()
        val metaDao = FakeMetaDao()
        val capsuleRepo = FakeCapsuleRepository()
        lateinit var manager: ContentCryptoManager
        val imageStore = ImageCipherStore(tmp.newFolder("images"), cipher) { manager.sessionKeyOrNull() }
        manager = ContentCryptoManager(cipher, metaDao, capsuleRepo, imageStore)
        return Triple(manager, metaDao, capsuleRepo)
    }

    // ---- 1. Argon2id 派生（真实跑，参数不放大）----

    @Test
    fun argon2DeriveIsDeterministicAnd32Bytes() {
        val params = KdfEngines.defaultParams(KdfEngines.newSalt())
        val pw = "口令测试Password123".toCharArray()
        val key1 = Argon2Kdf().derive(pw, params)
        val key2 = Argon2Kdf().derive(pw, params)
        assertEquals(32, key1.size)
        assertArrayEquals(key1, key2)
        // 不同盐必须产生不同密钥
        val other = Argon2Kdf().derive(pw, KdfEngines.defaultParams(KdfEngines.newSalt()))
        assertFalse(key1.contentEquals(other))
    }

    // ---- 2. PBKDF2 回退路径 ----

    @Test
    fun pbkdf2DeriveWorksAndParamsRoundTrip() {
        val params = KdfEngines.fallbackParams(KdfEngines.newSalt())
        val key = Pbkdf2Kdf().derive("pbkdf2-password".toCharArray(), params)
        assertEquals(32, key.size)
        assertEquals(KdfEngines.ALGO_PBKDF2, params.algo)
        assertEquals(310000, params.iterations)

        // 参数 JSON 往返（备份/导入依赖此格式稳定）
        val json = KdfEngines.paramsToJson(params)
        val parsed = KdfEngines.paramsFromJson(json)
        assertEquals(params, parsed)

        // deriveWithFallback 显式走 PBKDF2 引擎（同口令派生结果必须一致）
        val derived = KdfEngines.deriveWithFallback("pbkdf2-password".toCharArray(), params)
        assertEquals(KdfEngines.ALGO_PBKDF2, derived.params.algo)
        assertArrayEquals(key, derived.key)
    }

    // ---- 3. AES-256-GCM 往返 ----

    @Test
    fun aesGcmRoundTripAndUniqueNonce() {
        val key = Pbkdf2Kdf().derive("aes-test".toCharArray(), KdfEngines.fallbackParams(KdfEngines.newSalt()))
        val cipher = AesGcmCipher()
        val plaintext = "写给三年后的自己：还记得今天的雨吗？".toByteArray(Charsets.UTF_8)

        val blob = cipher.encrypt(key, plaintext)
        // 布局 = nonce(12) ‖ ct ‖ tag(16)
        assertEquals(12 + plaintext.size + 16, blob.size)
        assertArrayEquals(plaintext, cipher.decrypt(key, blob))

        // 每次加密新 nonce，相同明文产生不同密文
        val blob2 = cipher.encrypt(key, plaintext)
        assertFalse(blob.contentEquals(blob2))
    }

    // ---- 4. 错误口令 / 篡改密文失败 ----

    @Test
    fun wrongPasswordFailsAndTamperedCipherFails() {
        val key = Pbkdf2Kdf().derive("correct".toCharArray(), KdfEngines.fallbackParams(KdfEngines.newSalt()))
        val cipher = AesGcmCipher()
        val blob = cipher.encrypt(key, "内容".toByteArray())

        // 错误口令 → CryptoException
        try {
            cipher.decrypt(key.copyOf().also { it[0] = it[0].toInt().inv().toByte() }, blob)
            fail("错误口令应当解密失败")
        } catch (e: CryptoException) {
            assertTrue(e.message!!.contains("解密失败"))
        }

        // 篡改密文字节 → CryptoException（tag 校验失败）
        val tampered = blob.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        try {
            cipher.decrypt(key, tampered)
            fail("篡改密文应当解密失败")
        } catch (_: CryptoException) {
            // 预期路径
        }
    }

    // ---- 5. Verifier 正/误 + 会话编排 ----

    @Test
    fun verifierAcceptsCorrectAndRejectsWrongPassword() = runBlocking {
        val (manager, metaDao, _) = newManager()
        val password = "中文口令也可以".toCharArray()

        assertFalse(manager.hasPasswordSetup())
        manager.setupPassword(password)
        assertTrue(manager.hasPasswordSetup())
        assertTrue(manager.isUnlocked)

        // meta 表已写入三键
        assertTrue(metaDao.store.containsKey(ContentCryptoManager.KEY_KDF_PARAMS))
        assertTrue(metaDao.store.containsKey(ContentCryptoManager.KEY_VERIFIER))
        assertEquals("true", metaDao.store[ContentCryptoManager.KEY_HAS_PASSWORD])

        // 正确口令（重新派生路径）
        manager.lock()
        assertFalse(manager.isUnlocked)
        assertTrue(manager.unlock(password))
        assertTrue(manager.isUnlocked)

        // 错误口令
        manager.lock()
        assertFalse(manager.unlock("错误的口令呀".toCharArray()))
        assertFalse(manager.isUnlocked)
    }

    @Test
    fun contentEncryptDecryptUsesSessionKey() = runBlocking {
        val (manager, _, _) = newManager()
        manager.setupPassword("content-password".toCharArray())
        val content = "三年后打开会看到这句话".toByteArray(Charsets.UTF_8)

        val blob = manager.encryptContent(content)
        assertArrayEquals(content, manager.decryptContent(blob))

        manager.lock()
        try {
            manager.decryptContent(blob)
            fail("未解锁时应当抛 CryptoException")
        } catch (_: CryptoException) {
            // 预期路径
        }
    }

    // ---- 6. 图片存取往返（内存密钥通道）----

    @Test
    fun imageCipherStoreRoundTrip() = runBlocking {
        val (manager, _, _) = newManager()
        manager.setupPassword("image-password".toCharArray())
        // 通过会话密钥的存取路径（sessionKeyProvider 由 manager 提供）
        val store = ImageCipherStore(
            tmp.newFolder("imgs"),
            AesGcmCipher(),
        ) { manager.sessionKeyOrNull() }
        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        store.save("cap-1", 0, bytes)
        assertEquals(listOf(0), store.listIndexes("cap-1"))
        assertArrayEquals(bytes, store.read("cap-1", 0))
        assertEquals(null, store.read("cap-1", 1))
        assertTrue(store.readEncrypted("cap-1", 0)!!.size == bytes.size + 12 + 16)

        store.deleteDir("cap-1")
        assertTrue(store.allCapsuleIds().isEmpty())
    }

    // ---- 7. 未设口令时的保护 ----

    @Test
    fun unlockFailsWhenNoPasswordSetup() = runBlocking {
        val (manager, _, _) = newManager()
        assertFalse(manager.unlock("whatever".toCharArray()))
        assertFalse(manager.hasPasswordSetup())
        val baseDir: File = tmp.root
        assertTrue(baseDir.exists())
    }
}
