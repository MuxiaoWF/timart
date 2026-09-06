package com.muxiao.timart.data.local

import android.content.Context
import com.muxiao.timart.data.local.db.MetaDao
import com.muxiao.timart.data.local.db.entity.MetaEntity
import com.muxiao.timart.domain.model.City
import com.muxiao.timart.domain.model.matchesKeyword
import com.muxiao.timart.domain.repository.CityRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 内置城市码表仓库：首次访问冷加载 assets/cities.json 后常驻内存；
 * 上次使用城市持久化在 meta 表。中国城市之外含国外主要城市（国家名作 province 分组，
 * alias 提供英文关键词搜索）。
 */
class CityRepositoryImpl(context: Context, private val metaDao: MetaDao) : CityRepository {

    companion object {
        private const val LAST_CITY_KEY = "app.lastCityId"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val cities: List<City> by lazy { loadCities(context.applicationContext) }

    private val byIdIndex: Map<String, City> by lazy { cities.associateBy { it.id } }

    private fun loadCities(context: Context): List<City> = try {
        val raw = context.assets.open("cities.json").bufferedReader().use { it.readText() }
        val dtos = json.decodeFromString<List<CityDto>>(raw)
        dtos.map {
            City(id = it.id, name = it.name, province = it.province, lat = it.lat, lng = it.lng, alias = it.alias)
        }
    } catch (_: Exception) {
        // 城市表损坏不应导致应用崩溃，返回空表（创建流程可跳过天气快照）
        emptyList()
    }

    override fun all(): List<City> = cities

    override fun search(keyword: String): List<City> {
        val kw = keyword.trim()
        if (kw.isEmpty()) return cities
        return cities.filter { it.matchesKeyword(kw) }
    }

    override fun byId(id: String): City? = byIdIndex[id]

    override fun lastUsed(): City? = metaDao.getSync(LAST_CITY_KEY)?.let { byId(it) }

    override fun rememberLastUsed(id: String) {
        metaDao.putSync(MetaEntity(key = LAST_CITY_KEY, value = id))
    }
}

/**
 * cities.json 行结构：{ "id", "name", "province", "lat", "lng", "alias"? }。
 * 中国城市 id 沿用 9 位数字码；国外城市 id 以 `intl-` 前缀命名（如 intl-jp-tokyo），
 * province 存国家中文名（选择器按其分组），alias 存拉丁文名。
 */
@Serializable
private data class CityDto(
    val id: String,
    val name: String,
    val province: String,
    val lat: Double,
    val lng: Double,
    val alias: String? = null,
)
