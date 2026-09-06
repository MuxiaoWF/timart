package com.muxiao.timart.data.remote.weather

import com.muxiao.timart.domain.context.WeatherProvider
import com.muxiao.timart.domain.model.WeatherSnapshot
import com.muxiao.timart.domain.repository.CityRepository
import com.muxiao.timart.domain.repository.WeatherRepository

/**
 * 天气仓库实现：Open-Meteo + 30min 正缓存 + 5min 负缓存。
 *
 * 正缓存：命中且未过期直接返回（[forceSnapshot] 绕过缓存）；
 * 负缓存：请求失败同样落缓存（短 TTL）——弱网下避免每次判定/预览未命中即重试网络，
 * 判定侧拿到 null 走「暂时无法获取天气」原因文案。
 */
class WeatherRepositoryImpl(
    private val api: OpenMeteoApi,
    private val cityRepository: CityRepository,
) : WeatherRepository, WeatherProvider {

    /** cityId → (过期时刻, 快照)；snapshot 为 null 表示负缓存条目 */
    private val cache = HashMap<String, CacheEntry>()

    override fun snapshot(cityId: String): WeatherSnapshot? {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            val entry = cache[cityId]
            if (entry != null && now < entry.expiresAt) return entry.snapshot
        }
        val fresh = fetch(cityId)
        val ttl = if (fresh != null) POSITIVE_TTL_MILLIS else NEGATIVE_TTL_MILLIS
        synchronized(cache) { cache[cityId] = CacheEntry(now + ttl, fresh) }
        return fresh
    }

    override fun forceSnapshot(cityId: String): WeatherSnapshot? {
        val fresh = fetch(cityId) ?: return null
        synchronized(cache) {
            cache[cityId] = CacheEntry(System.currentTimeMillis() + POSITIVE_TTL_MILLIS, fresh)
        }
        return fresh
    }

    /** WeatherProvider 委托（ConditionContext 使用） */
    override fun currentWeather(cityId: String): WeatherSnapshot? = snapshot(cityId)

    private fun fetch(cityId: String): WeatherSnapshot? {
        val city = cityRepository.byId(cityId) ?: return null
        val weather = api.fetchCurrentWeather(city.lat, city.lng) ?: return null
        return WeatherSnapshot(
            cityId = city.id,
            cityName = city.name,
            weatherType = WmoCodeMapper.map(weather.weatherCode),
            tempC = weather.temperatureC,
            capturedAt = System.currentTimeMillis(),
            humidityPercent = weather.humidityPercent,
            windKmh = weather.windKmh,
            pressureHpa = weather.pressureHpa,
            uvIndex = weather.uvIndex,
        )
    }

    private data class CacheEntry(val expiresAt: Long, val snapshot: WeatherSnapshot?)

    private companion object {
        /** 正缓存 30 分钟 */
        const val POSITIVE_TTL_MILLIS = 1_800_000L

        /** 负缓存 5 分钟（失败不立刻重试，也不长时间放弃） */
        const val NEGATIVE_TTL_MILLIS = 300_000L
    }
}
