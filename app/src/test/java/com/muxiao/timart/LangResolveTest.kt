package com.muxiao.timart

import com.muxiao.timart.domain.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 语言解析纯 JVM 测试：Lang.fromSystemTag（系统 locale → 界面语言）。
 * 规则：zh-TW/HK/MO/Hant → 繁中；zh 其余 → 简中；en → 英文；其他 → 兜底简中。
 */
class LangResolveTest {

    @Test
    fun zhSimplifiedVariants() {
        assertEquals(Lang.ZH_HANS, Lang.fromSystemTag("zh-CN"))
        assertEquals(Lang.ZH_HANS, Lang.fromSystemTag("zh"))
        assertEquals(Lang.ZH_HANS, Lang.fromSystemTag("zh-Hans"))
        assertEquals(Lang.ZH_HANS, Lang.fromSystemTag("zh-SG"))
        assertEquals(Lang.ZH_HANS, Lang.fromSystemTag("zh-Hans-CN"))
    }

    @Test
    fun zhTraditionalVariants() {
        assertEquals(Lang.ZH_HANT, Lang.fromSystemTag("zh-TW"))
        assertEquals(Lang.ZH_HANT, Lang.fromSystemTag("zh-HK"))
        assertEquals(Lang.ZH_HANT, Lang.fromSystemTag("zh-MO"))
        assertEquals(Lang.ZH_HANT, Lang.fromSystemTag("zh-Hant"))
        assertEquals(Lang.ZH_HANT, Lang.fromSystemTag("zh-Hant-TW"))
        assertEquals(Lang.ZH_HANT, Lang.fromSystemTag("zh-TW#Hant"))
    }

    @Test
    fun englishVariants() {
        assertEquals(Lang.EN, Lang.fromSystemTag("en"))
        assertEquals(Lang.EN, Lang.fromSystemTag("en-US"))
        assertEquals(Lang.EN, Lang.fromSystemTag("en-GB"))
    }

    @Test
    fun otherLanguagesFallBackToSimplifiedChinese() {
        assertEquals(Lang.ZH_HANS, Lang.fromSystemTag("ja-JP"))
        assertEquals(Lang.ZH_HANS, Lang.fromSystemTag("fr"))
        assertEquals(Lang.ZH_HANS, Lang.fromSystemTag("de-DE"))
        assertEquals(Lang.ZH_HANS, Lang.fromSystemTag(""))
    }
}
