package com.muxiao.timart.domain.model

/**
 * 应用界面语言（纯 Kotlin，domain 文案与 UI 字符串表共用）。
 * 「跟随系统」是应用层概念（AppLanguage），在读取侧先解析成 [Lang] 再进入 domain。
 */
enum class Lang { ZH_HANS, ZH_HANT, EN;

    companion object {
        /**
         * 由系统语言 BCP-47 tag 解析界面语言（纯字符串逻辑，可 JVM 单测）：
         * zh-TW/HK/MO/Hant → 繁中；zh 其余 → 简中；en → 英文；其他语言兜底简中（产品中文优先）。
         */
        fun fromSystemTag(tag: String): Lang = when {
            tag.startsWith("zh", ignoreCase = true) ->
                if (listOf("tw", "hk", "mo", "hant").any { tag.contains(it, ignoreCase = true) }) ZH_HANT else ZH_HANS
            tag.startsWith("en", ignoreCase = true) -> EN
            else -> ZH_HANS
        }
    }
}
