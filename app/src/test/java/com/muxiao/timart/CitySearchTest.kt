package com.muxiao.timart

import com.muxiao.timart.domain.model.City
import com.muxiao.timart.domain.model.matchesKeyword
import com.muxiao.timart.domain.model.nearestTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 城市搜索匹配与定位附近排序纯 JVM 测试：
 * matchesKeyword = 中文名 / 省份（国外为国家）/ 拉丁别名（忽略大小写）；
 * nearestTo = haversine 最近城市排序。
 */
class CitySearchTest {

    private val beijing = City("101010100", "北京", "北京", 39.904, 116.407)
    private val tokyo = City("901010101", "东京", "日本", 35.69, 139.692, alias = "Tokyo")
    private val paris = City("intl-paris", "巴黎", "法国", 48.8566, 2.3522, alias = "Paris")

    @Test
    fun matchesChineseNameAndProvince() {
        assertTrue(beijing.matchesKeyword("北京"))
        assertTrue(beijing.matchesKeyword("京"))
        assertTrue(tokyo.matchesKeyword("日本")) // 国家名（province）命中
        assertTrue(paris.matchesKeyword("法国"))
    }

    @Test
    fun matchesLatinAliasCaseInsensitive() {
        assertTrue(tokyo.matchesKeyword("tokyo"))
        assertTrue(tokyo.matchesKeyword("TOKYO"))
        assertTrue(paris.matchesKeyword("paris"))
        assertFalse(beijing.matchesKeyword("beijing")) // 无 alias 的城市不误命中
    }

    @Test
    fun nonMatchAndBlank() {
        assertFalse(tokyo.matchesKeyword("伦敦"))
        assertFalse(paris.matchesKeyword("New York"))
        assertTrue(beijing.matchesKeyword("")) // 空关键词 = 全量
        assertTrue(beijing.matchesKeyword("  "))
    }

    @Test
    fun trimBeforeMatch() {
        assertTrue(tokyo.matchesKeyword(" 东京 "))
        assertTrue(paris.matchesKeyword(" PARIS "))
    }

    @Test
    fun nearestToOrdersByDistance() {
        val tianjin = City("101030100", "天津", "天津", 39.084, 117.201)
        val shanghai = City("101020100", "上海", "上海", 31.230, 121.474)
        val cities = listOf(shanghai, tianjin, beijing)
        // 北京附近：北京自身（距离 0）最近，其次天津
        val nearBeijing = cities.nearestTo(39.904, 116.407, 2)
        assertEquals(listOf("北京", "天津"), nearBeijing.map { it.name })
        // count 截断
        assertEquals(1, cities.nearestTo(31.230, 121.474, 1).size)
        assertEquals("上海", cities.nearestTo(31.230, 121.474, 1).first().name)
    }

    @Test
    fun nearestToHandlesCountLargerThanList() {
        val cities = listOf(beijing, tokyo)
        assertEquals(2, cities.nearestTo(0.0, 0.0, 10).size)
    }
}
