package com.muxiao.timart.data.remote.geocode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地址解析响应解析纯 JVM 测试（org.json 在单测中是 android.jar 桩不可测，
 * 故 geocode 模块解析统一走 kotlinx-serialization，真实 JVM 实现可测）。
 * 锁定三件事：坐标字段正确解析（Photon GeoJSON 经度在前）、label 组装规则、
 * 脏数据容错（非数值坐标/缺字段/坏 JSON 一律过滤或空列表，绝不抛出）。
 */
class GeoCodeParserTest {

    // ---- Nominatim ----

    @Test
    fun `nominatim search parses coords and label, skips non numeric rows`() {
        val body = """
            [
              {
                "place_id": 123, "osm_type": "way",
                "lat": "31.2396800", "lon": "121.4997200",
                "display_name": "外滩, 中山东一路, 黄浦区, 上海市, 中国",
                "class": "tourism", "type": "attraction", "importance": 0.9,
                "address": {"country": "中国"},
                "boundingbox": ["31.23", "31.24", "121.49", "121.50"]
              },
              {
                "place_id": 456, "lat": "not-a-number", "lon": "116.397",
                "display_name": "坏数据", "class": "place", "type": "city"
              }
            ]
        """.trimIndent()
        val results = NominatimApiClient().parseSearch(body)
        assertEquals(1, results.size)
        assertEquals(31.23968, results[0].lat, 1e-9)
        assertEquals(121.49972, results[0].lng, 1e-9)
        assertTrue(results[0].label.startsWith("外滩"))
    }

    @Test
    fun `nominatim search tolerates malformed json and empty array`() {
        assertTrue(NominatimApiClient().parseSearch("{not json").isEmpty())
        assertTrue(NominatimApiClient().parseSearch("[]").isEmpty())
    }

    @Test
    fun `nominatim reverse parses single object`() {
        val body = """
            {
              "place_id": 789, "lat": "39.9042000", "lon": "116.4074000",
              "display_name": "天安门, 东长安街, 东城区, 北京市, 中国",
              "class": "tourism", "type": "attraction"
            }
        """.trimIndent()
        val suggestion = NominatimApiClient().parseReverse(body)
        assertEquals(39.9042, suggestion!!.lat, 1e-9)
        assertEquals(116.4074, suggestion.lng, 1e-9)
        assertTrue(suggestion.label.startsWith("天安门"))
    }

    @Test
    fun `nominatim reverse without label returns null`() {
        assertNull(NominatimApiClient().parseReverse("""{"lat": "1.0", "lon": "2.0"}"""))
        assertNull(NominatimApiClient().parseReverse("not json"))
    }

    // ---- Photon ----

    @Test
    fun `photon search swaps geojson lng-lat order and composes label`() {
        val body = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {"type": "Point", "coordinates": [121.49972, 31.23968]},
                  "properties": {
                    "osm_id": 1, "osm_type": "W",
                    "country": "中国", "city": "上海市", "state": "上海市", "name": "外滩"
                  }
                }
              ]
            }
        """.trimIndent()
        val results = PhotonApiClient().parseSearch(body)
        assertEquals(1, results.size)
        assertEquals(31.23968, results[0].lat, 1e-9)
        assertEquals(121.49972, results[0].lng, 1e-9)
        // city/state 重复去重：外滩 · 上海市 · 中国
        assertEquals("外滩 · 上海市 · 中国", results[0].label)
    }

    @Test
    fun `photon search falls back to street plus housenumber when name missing`() {
        val body = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {"type": "Point", "coordinates": [116.397, 39.904]},
                  "properties": {"country": "中国", "city": "北京市", "street": "东长安街", "housenumber": "1号"}
                }
              ]
            }
        """.trimIndent()
        val results = PhotonApiClient().parseSearch(body)
        assertEquals(1, results.size)
        assertEquals("1号 东长安街 · 北京市 · 中国", results[0].label)
    }

    @Test
    fun `photon search skips features without coords or primary text`() {
        val body = """
            {
              "type": "FeatureCollection",
              "features": [
                {"type": "Feature", "geometry": {"type": "Point", "coordinates": []},
                 "properties": {"name": "no-coords"}},
                {"type": "Feature", "geometry": {"type": "Point", "coordinates": [1.0, 2.0]},
                 "properties": {"osm_id": 7}},
                {"type": "Feature", "geometry": {"type": "Point", "coordinates": [3.0, 4.0]},
                 "properties": {"name": "ok"}}
              ]
            }
        """.trimIndent()
        val results = PhotonApiClient().parseSearch(body)
        assertEquals(1, results.size)
        assertEquals("ok", results[0].label)
        assertEquals(4.0, results[0].lat, 1e-9)
        assertEquals(3.0, results[0].lng, 1e-9)
    }

    @Test
    fun `photon search tolerates malformed json`() {
        assertTrue(PhotonApiClient().parseSearch("{broken").isEmpty())
    }

    // ---- 天地图 ----

    @Test
    fun `tianditu search parses status-ok response with lon-lat location`() {
        val body = """
            {
              "result": {
                "formatted_address": "北京市西城区西长安街街道中南海",
                "location": {"lon": 116.39125, "lat": 39.90732},
                "level": "兴趣点"
              },
              "msg": "ok", "status": "0"
            }
        """.trimIndent()
        val results = TiandituApiClient("tk").parseSearch(body)
        assertEquals(1, results.size)
        assertEquals(39.90732, results[0].lat, 1e-9)
        assertEquals(116.39125, results[0].lng, 1e-9)
        assertEquals("北京市西城区西长安街街道中南海", results[0].label)
    }

    @Test
    fun `tianditu search skips failure status and malformed json`() {
        val failed = """
            {"msg":"查询结果为空","status":"1"}
        """.trimIndent()
        assertTrue(TiandituApiClient("tk").parseSearch(failed).isEmpty())
        assertTrue(TiandituApiClient("tk").parseSearch("{bad json").isEmpty())
    }

    @Test
    fun `tianditu reverse parses formatted address`() {
        val body = """
            {
              "result": {
                "formatted_address": "北京市西城区百万庄大街22号",
                "addressComponent": {"province": "北京市", "city": "北京市", "county": "西城区"}
              },
              "msg": "ok", "status": "0"
            }
        """.trimIndent()
        assertEquals("北京市西城区百万庄大街22号", TiandituApiClient("tk").parseReverseLabel(body))
    }

    @Test
    fun `tianditu reverse tolerates failure and missing result`() {
        assertNull(TiandituApiClient("tk").parseReverseLabel("""{"msg":"错误","status":"1"}"""))
        assertNull(TiandituApiClient("tk").parseReverseLabel("""{"msg":"ok","status":"0"}"""))
        assertNull(TiandituApiClient("tk").parseReverseLabel("not json"))
    }
}
