package com.muxiao.timart.data.local.db

import kotlinx.serialization.json.Json

/**
 * 列值转换辅助（非 Room @TypeConverter）：
 * 时间一律 epoch Long 无需转换；List<String> ↔ JSON 字符串由 Mapper 调用本对象完成。
 */
object Converters {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** List<String> → JSON 数组字符串（空列表存 "[]"） */
    fun stringsToJson(list: List<String>): String = json.encodeToString(list)

    /** JSON 数组字符串 → List<String>（解析失败返回空列表，容错） */
    fun jsonToStrings(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
