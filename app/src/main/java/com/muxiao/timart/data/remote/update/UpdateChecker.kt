package com.muxiao.timart.data.remote.update

import com.muxiao.timart.data.remote.geocode.httpGetString
import org.json.JSONObject

/**
 * GitHub Releases 更新检查（零依赖，与天气/地理编码同款 HttpURLConnection 通道）。
 * 仓库占位期（尚未发布任何 Release）：API 404 返回非 JSON 文本 → [Result.NotFound] 软处理。
 * 版本比对采用 tag 与 versionName 去除前导 v 后的精确相等（发布流程需保证 tag = v{versionName}）。
 */
object UpdateChecker {

    /** 仓库主页（占位） */
    const val REPO_URL = "https://github.com/muxiaowf/timart"

    /** Releases 列表页（无 html_url 兜底） */
    const val RELEASES_PAGE = "https://github.com/muxiaowf/timart/releases"

    private const val API_LATEST = "https://api.github.com/repos/muxiaowf/timart/releases/latest"

    sealed class Result {
        /** 发现新版本：最新版本号 + 发布页地址 */
        data class Available(val latestTag: String, val releaseUrl: String) : Result()

        object UpToDate : Result()

        object NotFound : Result()

        object NetworkError : Result()
    }

    /** 同步网络调用，必须在 IO 协程中执行 */
    fun check(currentVersion: String): Result {
        val body = httpGetString(API_LATEST, timeoutMillis = 8_000) ?: return Result.NetworkError
        val json = runCatching { JSONObject(body) }.getOrElse { return Result.NotFound }
        val tag = json.optString("tag_name").removePrefix("v").removePrefix("V")
        if (tag.isEmpty()) return Result.NotFound
        val releaseUrl = json.optString("html_url").ifEmpty { RELEASES_PAGE }
        return if (tag == currentVersion) Result.UpToDate else Result.Available(tag, releaseUrl)
    }
}
