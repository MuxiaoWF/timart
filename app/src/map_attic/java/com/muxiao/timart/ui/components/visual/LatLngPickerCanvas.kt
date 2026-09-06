package com.muxiao.timart.ui.components.visual

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TrackHairline
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 离线经纬度画布（架构 §2.18，GPS 条件表单用）：
 * 可平移缩放的经纬网格 + 十字准星 + 经纬度手动输入 + 「使用当前位置」（LocationManager）。
 * 网格间距随 zoom 自适应换算；**无任何地图 SDK / 无瓦片下载**，纯本地几何绘制。
 */
@Composable
fun LatLngPickerCanvas(
    lat: Double,
    lng: Double,
    onLatLngChange: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    var centerLat by remember { mutableStateOf(lat) }
    var centerLng by remember { mutableStateOf(lng) }
    var zoom by remember { mutableStateOf(4f) } // 屏幕上 1° = 40dp × zoom

    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.45f)
                .border(1.dp, TrackHairline, MaterialTheme.shapes.small)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(0.8f, 24f)
                        val pxPerDeg = 40.dp.toPx() * zoom
                        centerLng = (centerLng + pan.x / pxPerDeg).coerceIn(-180.0, 180.0)
                        centerLat = (centerLat - pan.y / pxPerDeg).coerceIn(-85.0, 85.0)
                        onLatLngChange(centerLat, centerLng)
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pxPerDeg = 40.dp.toPx() * zoom
                // 网格间距自适应：目标屏幕间距约 100px
                val step = GRID_STEPS.firstOrNull { it * pxPerDeg >= 100f } ?: 30.0
                val halfW = size.width / 2f
                val halfH = size.height / 2f
                val labelStyle = TextStyle(color = InkSecondary, fontSize = 9.sp)

                // 纬度横线（含度数标注）
                val startLat = centerLat - (halfH / pxPerDeg)
                val endLat = centerLat + (halfH / pxPerDeg)
                var latLine = floor(startLat / step) * step
                while (latLine <= endLat) {
                    val y = (halfH - ((latLine - centerLat) * pxPerDeg)).toFloat()
                    drawLine(TrackHairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${((latLine * 10).roundToInt() / 10.0)}°",
                        topLeft = Offset(6.dp.toPx(), y + 2.dp.toPx()),
                        style = labelStyle,
                    )
                    latLine += step
                }
                // 经度竖线（含度数标注）
                val startLng = centerLng - (halfW / pxPerDeg)
                val endLng = centerLng + (halfW / pxPerDeg)
                var lngLine = floor(startLng / step) * step
                while (lngLine <= endLng) {
                    val x = (halfW + ((lngLine - centerLng) * pxPerDeg)).toFloat()
                    drawLine(TrackHairline, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${((lngLine * 10).roundToInt() / 10.0)}°",
                        topLeft = Offset(x + 2.dp.toPx(), 6.dp.toPx()),
                        style = labelStyle,
                    )
                    lngLine += step
                }
                // 十字准星（画布中心即选定坐标）
                drawLine(TimeGold, Offset(halfW - 18f, halfH), Offset(halfW + 18f, halfH), strokeWidth = 2f)
                drawLine(TimeGold, Offset(halfW, halfH - 18f), Offset(halfW, halfH + 18f), strokeWidth = 2f)
                drawCircle(
                    TimeGold.copy(alpha = 0.35f),
                    radius = 26f,
                    center = Offset(halfW, halfH),
                    style = Stroke(width = 2f),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            OutlinedTextField(
                value = trimCoord(centerLat),
                onValueChange = { raw ->
                    raw.toDoubleOrNull()?.let { v ->
                        centerLat = v.coerceIn(-85.0, 85.0)
                        onLatLngChange(centerLat, centerLng)
                    }
                },
                label = { Text(L.mapLat, style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = trimCoord(centerLng),
                onValueChange = { raw ->
                    raw.toDoubleOrNull()?.let { v ->
                        centerLng = v.coerceIn(-180.0, 180.0)
                        onLatLngChange(centerLat, centerLng)
                    }
                },
                label = { Text(L.mapLng, style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedButton(
            onClick = {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val permitted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                val last = if (lm != null && permitted) {
                    lm.allProviders.firstNotNullOfOrNull { provider -> lm.getLastKnownLocation(provider) }
                } else {
                    null
                }
                if (last != null) {
                    centerLat = last.latitude.coerceIn(-85.0, 85.0)
                    centerLng = last.longitude.coerceIn(-180.0, 180.0)
                    onLatLngChange(centerLat, centerLng)
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
        ) {
            Text(L.mapUseCurrent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun trimCoord(v: Double): String = String.format("%.4f", v)

/** 网格间距候选（度），从密到疏自适应选择 */
private val GRID_STEPS = doubleArrayOf(0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 30.0)
