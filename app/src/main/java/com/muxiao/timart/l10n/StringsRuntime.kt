package com.muxiao.timart.l10n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.muxiao.timart.domain.model.Lang
import com.muxiao.timart.utils.RuntimeSettings
import java.util.Locale

/** 由生效语言取词表（纯函数，非组合层与组合层共用） */
fun stringsFor(lang: Lang): Strings = when (lang) {
    Lang.ZH_HANS -> StringsZhHans
    Lang.ZH_HANT -> StringsZhHant
    Lang.EN -> StringsEn
}

/** 非组合层快捷入口：按当前生效语言取词表（Worker / 通知 / 导出 / crypto 异常消息等） */
fun currentStrings(): Strings = stringsFor(RuntimeSettings.resolvedLang)

/**
 * 组合层词表提供点。MainActivity 根部 provider 一次；
 * [rememberStrings] 组合期读取 RuntimeSettings.appLanguage 快照状态，
 * 设置页切换语言后全树自动以新词表重组，无需重建 Activity。
 */
val LocalStrings = compositionLocalOf<Strings> { StringsZhHans }

@Composable
fun rememberStrings(): Strings {
    val appLanguage = RuntimeSettings.appLanguage
    return remember(appLanguage) {
        stringsFor(appLanguage.toLang(Locale.getDefault().toLanguageTag()))
    }
}
