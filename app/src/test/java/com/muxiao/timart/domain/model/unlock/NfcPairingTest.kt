package com.muxiao.timart.domain.model.unlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** NFC 写卡配对编解码（纯 Kotlin 逻辑，架构 §2.15 增补） */
class NfcPairingTest {

    @Test
    fun 载荷即挑战ID() {
        assertEquals("abc-123", NfcPairing.payloadFor("abc-123"))
    }

    @Test
    fun 外部类型名拼接() {
        assertEquals("timart.com:capsule", NfcPairing.FULL_TYPE)
        assertTrue(NfcPairing.isTimartRecord("timart.com:capsule"))
        assertTrue(NfcPairing.isTimartRecord("TIMART.COM:CAPSULE"))
        assertFalse(NfcPairing.isTimartRecord("android.com:pkg"))
        assertFalse(NfcPairing.isTimartRecord("timart.com:other"))
    }

    @Test
    fun `匹配 - 载荷一致命中`() {
        val payloads = listOf("timart.com:capsule", "key-1")
        assertTrue(NfcPairing.matches("key-1", payloads))
    }

    @Test
    fun `匹配 - 载荷不一致不命中`() {
        val payloads = listOf("timart.com:capsule", "other-id")
        assertFalse(NfcPairing.matches("key-1", payloads))
    }

    @Test
    fun `匹配 - 无配对记录不命中`() {
        assertFalse(NfcPairing.matches("key-1", emptyList()))
    }

    @Test
    fun `匹配 - 严格区分大小写`() {
        assertFalse(NfcPairing.matches("KEY-1", listOf("key-1")))
    }
}
