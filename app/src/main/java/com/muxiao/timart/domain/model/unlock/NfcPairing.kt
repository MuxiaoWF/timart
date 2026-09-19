package com.muxiao.timart.domain.model.unlock

/**
 * NFC 写卡配对编解码（纯 Kotlin，零 android import，架构红线）。
 *
 * 配对原理：创建条件时把一条 NDEF 外部类型记录写入卡贴——
 * 类型为 [FULL_TYPE]（`timart.com:capsule`），载荷为该挑战的 [payloadFor]（即 challengeId）；
 * 解锁贴卡时读取卡上同类型记录，载荷与 expectedPayload（= challengeId）一致即通过。
 *
 * android 侧（ui/detail 与写卡面板）负责 NdefMessage/NdefRecord 的构造与读写，
 * 本对象只承载类型名约定与匹配逻辑，保证 JVM 可单测。
 */
object NfcPairing {

    /** NDEF 外部类型域名（createExternal 的 domain 参数，不含冒号） */
    const val EXTERNAL_DOMAIN = "timart.com"

    /** NDEF 外部类型类型名（createExternal 的 type 参数） */
    const val EXTERNAL_TYPE = "capsule"

    /** 完整外部类型名，如 `timart.com:capsule` */
    const val FULL_TYPE = "$EXTERNAL_DOMAIN:$EXTERNAL_TYPE"

    /** 胶囊链接记录类型名（体验储备池 §4 NFC 实体锚点） */
    const val LINK_TYPE = "link"

    /** 胶囊链接完整外部类型名 `timart.com:link`，载荷 = capsuleId */
    const val LINK_FULL_TYPE = "$EXTERNAL_DOMAIN:$LINK_TYPE"

    /** 写入卡的载荷内容（UTF-8 字节随卡保存）：即 challengeId */
    fun payloadFor(challengeId: String): String = challengeId

    /** 该记录类型是否为本 App 的配对记录（忽略大小写） */
    fun isTimartRecord(recordType: String): Boolean = recordType.equals(FULL_TYPE, ignoreCase = true)

    /** 该记录类型是否为本 App 的胶囊链接记录（忽略大小写） */
    fun isLinkRecord(recordType: String): Boolean = recordType.equals(LINK_FULL_TYPE, ignoreCase = true)

    /** 贴卡判定：卡上全部 timart 配对记录的载荷中是否有与 [expectedPayload] 一致的 */
    fun matches(expectedPayload: String, timartRecordPayloads: Collection<String>): Boolean =
        timartRecordPayloads.any { it == expectedPayload }
}
