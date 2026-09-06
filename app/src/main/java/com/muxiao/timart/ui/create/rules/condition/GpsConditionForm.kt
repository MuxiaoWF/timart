package com.muxiao.timart.ui.create.rules.condition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.muxiao.timart.data.remote.geocode.GeoSuggestion
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.components.PermissionGuideDialog
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TrackHairline
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.utils.location.GeocodeResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * GPS 到达条件表单（架构 §2.15）：
 * 无地图、无画布——地址文字解析（[GeocodeResolver]：原生 Geocoder → Nominatim → Photon 三级兜底，
 * 网络仅发送输入的地点文字）+ 半径滑条（50–2000 米）+「使用当前位置」（LocationManager lastKnown，
 * 纯被动读本地）+ 手动经纬度兜底（解析全部失败时表单仍可用）。
 * 表单只产出坐标与半径，位置判定在解锁时刻由 LocationProvider 执行。
 * T14：位置权限说明先于系统弹窗（首次进入表单且未授权时引导一次）。
 */
@Composable
fun GpsConditionForm(
    geocodeResolver: GeocodeResolver,
    onConfirm: (UnlockCondition) -> Unit,
    away: Boolean = false,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    var lat by remember { mutableDoubleStateOf(39.9042) } // 默认北京
    var lng by remember { mutableDoubleStateOf(116.4074) }
    var radius by remember { mutableIntStateOf(200) }
    var showGuide by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    // 位置权限引导：说明先于系统弹窗；拒绝不影响表单使用（坐标可解析/手动输入）
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) showGuide = true
    }
    if (showGuide) {
        PermissionGuideDialog(
            title = L.gfPermTitle,
            body = L.permLocationDesc + "\n" + L.gfGeocodeDesc,
            onConfirm = {
                showGuide = false
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            onDismiss = { showGuide = false },
        )
    }

    // ---- 地址解析状态 ----
    var query by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<GeoSuggestion>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var pickedLabel by remember { mutableStateOf<String?>(null) }
    var manualEntry by remember { mutableStateOf(false) }
    var manualLatText by remember { mutableStateOf("%.4f".format(39.9042)) }
    var manualLngText by remember { mutableStateOf("%.4f".format(116.4074)) }
    val scope = rememberCoroutineScope()

    fun clearPicks() {
        selectedIndex = -1
        pickedLabel = null
        statusText = null
    }

    fun resolve() {
        val q = query.trim()
        if (q.isEmpty() || resolving) return
        resolving = true
        statusText = null
        suggestions = emptyList()
        selectedIndex = -1
        pickedLabel = null
        scope.launch(Dispatchers.IO) {
            val results = geocodeResolver.search(q, RuntimeSettings.resolvedLang)
            suggestions = results
            resolving = false
            if (results.isEmpty()) statusText = L.gfNoResult
        }
    }

    fun reverseLabel(targetLat: Double, targetLng: Double) {
        scope.launch(Dispatchers.IO) {
            pickedLabel = geocodeResolver.reverse(targetLat, targetLng, RuntimeSettings.resolvedLang)?.label
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.gfTitle, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = L.gfDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )

        // ---- 地址输入 + 解析（三级兜底：Geocoder → Nominatim → Photon） ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(L.gfAddrHint, style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                enabled = !resolving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { resolve() }),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { resolve() },
                enabled = !resolving && query.isNotBlank(),
            ) {
                Text(if (resolving) L.gfResolving else L.gfResolve, style = MaterialTheme.typography.labelLarge)
            }
        }

        statusText?.let {
            Text(
                text = it,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // ---- 解析候选列表（选中即定坐标） ----
        suggestions.forEachIndexed { index, s ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .border(
                        1.dp,
                        if (index == selectedIndex) TimeGold else TrackHairline,
                        MaterialTheme.shapes.small,
                    )
                    .background(
                        if (index == selectedIndex) SurfaceRaise else Color.Transparent,
                        MaterialTheme.shapes.small,
                    )
                    .clickable {
                        lat = s.lat
                        lng = s.lng
                        selectedIndex = index
                        pickedLabel = s.label
                        statusText = null
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = s.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkPrimary,
                        maxLines = 2,
                    )
                    Text(
                        text = "%.4f°, %.4f°".format(s.lat, s.lng),
                        style = TimartType.caption,
                        color = InkSecondary,
                    )
                }
            }
        }

        // ---- 使用当前位置（LocationManager lastKnown，纯被动，不请求权限） ----
        OutlinedButton(
            onClick = {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val permitted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                val last = if (lm != null && permitted) {
                    lm.allProviders.firstNotNullOfOrNull { provider -> lm.getLastKnownLocation(provider) }
                } else {
                    null
                }
                if (last != null) {
                    val newLat = last.latitude.coerceIn(-85.0, 85.0)
                    val newLng = last.longitude.coerceIn(-180.0, 180.0)
                    lat = newLat
                    lng = newLng
                    clearPicks()
                    suggestions = emptyList()
                    reverseLabel(newLat, newLng)
                } else {
                    statusText = L.gfNoLocation
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
        ) {
            Text(L.mapUseCurrent, style = MaterialTheme.typography.labelLarge)
        }

        // ---- 选中地址 / 坐标摘要 ----
        pickedLabel?.let {
            Text(
                text = it,
                style = TimartType.caption,
                color = InkPrimary,
                modifier = Modifier.padding(top = 14.dp),
                maxLines = 2,
            )
        }
        Text(
            text = "%.4f°, %.4f°".format(lat, lng),
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier
                .padding(top = 2.dp)
                .align(Alignment.CenterHorizontally),
        )

        // ---- 半径 ----
        Text(
            text = L.gfRadiusFmt.format(radius),
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 14.dp),
        )
        Slider(
            value = radius.toFloat(),
            onValueChange = { radius = (it / 50).toInt() * 50 },
            valueRange = 50f..2000f,
            colors = SliderDefaults.colors(
                thumbColor = TimeGold,
                activeTrackColor = TimeGold,
                inactiveTrackColor = SurfaceRaise,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- 手动经纬度兜底（解析全部失败 / 精确输入场景） ----
        TextButton(onClick = { manualEntry = !manualEntry }) {
            Text(L.gfManualToggle, style = MaterialTheme.typography.labelLarge, color = TimeGold)
        }
        if (manualEntry) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = manualLatText,
                    onValueChange = { raw ->
                        manualLatText = raw
                        raw.toDoubleOrNull()?.let { v ->
                            lat = v.coerceIn(-85.0, 85.0)
                            clearPicks()
                        }
                    },
                    label = { Text(L.mapLat, style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = manualLngText,
                    onValueChange = { raw ->
                        manualLngText = raw
                        raw.toDoubleOrNull()?.let { v ->
                            lng = v.coerceIn(-180.0, 180.0)
                            clearPicks()
                        }
                    },
                    label = { Text(L.mapLng, style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        FormConfirmButton(enabled = true, label = L.gfConfirm) {
            if (away) {
                onConfirm(
                    UnlockCondition.AwayFromLocation(lat = lat, lng = lng, radiusMeter = radius),
                )
            } else {
                onConfirm(
                    UnlockCondition.GpsLocation(lat = lat, lng = lng, radiusMeter = radius),
                )
            }
        }
    }
}
