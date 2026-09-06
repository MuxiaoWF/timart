package com.muxiao.timart.domain.repository

import com.muxiao.timart.domain.model.WeatherSnapshot

/**
 * 天气仓库接口（WeatherProvider 的超集），同步阻塞式——
 * 调用方需在 IO/Default 协程中调用；30min 时间片缓存在实现层。
 */
interface WeatherRepository {

    /** 查询城市当前天气（30min 缓存，时间片命中不请求）；失败返回 null */
    fun snapshot(cityId: String): WeatherSnapshot?

    /** 强制拉取快照（创建胶囊时绕缓存）；失败返回 null */
    fun forceSnapshot(cityId: String): WeatherSnapshot?
}
