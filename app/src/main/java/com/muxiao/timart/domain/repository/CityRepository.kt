package com.muxiao.timart.domain.repository

import com.muxiao.timart.domain.model.City

/** 内置城市码表仓库（assets/cities.json 冷加载后常驻内存） */
interface CityRepository {

    /** 全量城市列表 */
    fun all(): List<City>

    /** 按名称 / 省份模糊搜索 */
    fun search(keyword: String): List<City>

    fun byId(id: String): City?

    /** 上次使用的城市（设置默认选中） */
    fun lastUsed(): City?

    /** 记住上次使用的城市 */
    fun rememberLastUsed(id: String)
}
