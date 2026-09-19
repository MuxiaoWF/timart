package com.muxiao.timart.data.remote.weather

import com.muxiao.timart.domain.context.AirQualityProvider
import com.muxiao.timart.domain.repository.CityRepository

/**
 * 空气质量仓库实现：Open-Meteo Air Quality + 与天气同款 30min 正缓存 / 5min 负缓存。
 * 城市表无对应条目或请求失败返回 null（判定侧走「获取失败」原因文案）。
 */
class AirQualityRepositoryImpl(
    private val api: OpenMeteoAirApi,
    private val cityRepository: CityRepository,
) : AirQualityProvider {

    /** cityId → (过期时刻, AQI)；aqi 为 null 表示负缓存条目 */
    private val cache = HashMap<String, CacheEntry>()

    override fun aqi(cityId: String): Int? {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            val entry = cache[cityId]
            if (entry != null && now < entry.expiresAt) return entry.aqi
        }
        val fresh = cityRepository.byId(cityId)?.let { api.fetchAqi(it.lat, it.lng) }
        val ttl = if (fresh != null) POSITIVE_TTL_MILLIS else NEGATIVE_TTL_MILLIS
        synchronized(cache) { cache[cityId] = CacheEntry(now + ttl, fresh) }
        return fresh
    }

    private data class CacheEntry(val expiresAt: Long, val aqi: Int?)

    private companion object {
        const val POSITIVE_TTL_MILLIS = 1_800_000L
        const val NEGATIVE_TTL_MILLIS = 300_000L
    }
}
