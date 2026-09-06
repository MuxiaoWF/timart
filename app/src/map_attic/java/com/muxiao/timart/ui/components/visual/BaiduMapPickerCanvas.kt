package com.muxiao.timart.ui.components.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.CircleOptions
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.model.LatLng
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.utils.location.CoordTransform
import kotlin.math.abs

/**
 * 百度地图选点画布（GPS 条件表单用，在线模式）：
 * 地图中心即选定坐标（十字准星意象），拖动/缩放即改选点；金色半透明圆为解锁半径示意。
 *
 * - 坐标协议：对内统一 WGS-84（与判定链一致）；与百度地图交互时经 [CoordTransform] 转 BD-09；
 * - 回环防护：地图拖动产生的状态变更不再回写地图（容差 2e-4°≈22 m）；
 * - 调用方须先经 BaiduMapBootstrap.ensureInitialized 成功后再挂载本组件。
 */
@Composable
fun BaiduMapPickerCanvas(
    lat: Double,
    lng: Double,
    radiusMeter: Int,
    onLatLngChange: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mapView = remember { MapView(context) }
    val baiduMap = remember { mapView.map }

    // 地图最近一次对外上报的 WGS-84 坐标（回环防护标记）
    var emittedFromMap by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // ---- 生命周期转发 ----
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    // ---- 初始状态：以当前坐标为地图中心 ----
    DisposableEffect(baiduMap) {
        val bd = CoordTransform.wgs84ToBd09(lat, lng)
        baiduMap.setMapStatus(
            MapStatusUpdateFactory.newMapStatus(
                MapStatus.Builder()
                    .target(LatLng(bd.first, bd.second))
                    .zoom(15f)
                    .build(),
            ),
        )
        // 缩放范围用 SDK 默认（3–21），足够选点
        baiduMap.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(status: MapStatus?, reason: Int) = Unit

            override fun onMapStatusChangeStart(status: MapStatus?) = Unit

            override fun onMapStatusChange(status: MapStatus?) {
                status?.target?.let { t ->
                    val (wLat, wLng) = CoordTransform.bd09ToWgs84(t.latitude, t.longitude)
                    val prev = emittedFromMap
                    if (prev == null || abs(prev.first - wLat) > 1e-6 || abs(prev.second - wLng) > 1e-6) {
                        emittedFromMap = wLat to wLng
                        onLatLngChange(wLat, wLng)
                    }
                    drawRadiusCircle(baiduMap, t, radiusMeter)
                }
            }

            override fun onMapStatusChangeFinish(status: MapStatus?) = Unit
        })
        onDispose { baiduMap.setOnMapStatusChangeListener(null) }
    }

    // ---- 外部改点（手动输入经纬 / 使用当前位置）→ 地图回中 ----
    LaunchedEffect(lat, lng) {
        if (emittedFromMap == null || abs((emittedFromMap?.first ?: 0.0) - lat) > 2e-4 ||
            abs((emittedFromMap?.second ?: 0.0) - lng) > 2e-4
        ) {
            val bd = CoordTransform.wgs84ToBd09(lat, lng)
            baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(LatLng(bd.first, bd.second)))
            drawRadiusCircle(baiduMap, LatLng(bd.first, bd.second), radiusMeter)
        }
    }

    // ---- 半径变化 → 重画半径圈 ----
    LaunchedEffect(radiusMeter) {
        baiduMap.mapStatus?.target?.let { drawRadiusCircle(baiduMap, it, radiusMeter) }
    }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.45f),
        )
        // 十字准星：地图中心即选定坐标（纯覆盖层，不拦截手势）
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1.45f),
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(TimeGold, Offset(cx - 36f, cy), Offset(cx + 36f, cy), strokeWidth = 4f)
            drawLine(TimeGold, Offset(cx, cy - 36f), Offset(cx, cy + 36f), strokeWidth = 4f)
            drawCircle(
                TimeGold.copy(alpha = 0.35f),
                radius = 52f,
                center = Offset(cx, cy),
                style = Stroke(width = 4f),
            )
        }
    }
}

/** 中心半径圈（BD-09 中心 + 米制半径；地图上唯一 overlay，重画即清） */
private fun drawRadiusCircle(map: BaiduMap, target: LatLng, radiusMeter: Int) {
    map.clear()
    map.addOverlay(
        CircleOptions()
            .center(target)
            .radius(radiusMeter)
            .fillColor(0x24E8B44A) // TimeGold ~14% alpha
            .stroke(com.baidu.mapapi.map.Stroke(4f, 0xFFE8B44A.toInt())),
    )
}
