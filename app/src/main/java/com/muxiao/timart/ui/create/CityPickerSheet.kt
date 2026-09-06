package com.muxiao.timart.ui.create

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.components.PermissionGuideDialog
import com.muxiao.timart.domain.model.City
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TrackHairline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 城市选择底部面板（架构 §2.15）：搜索 + 省份分组列表 + 默认标注上次使用城市。
 * 城市码表常驻内存后为纯内存查询；选定即回调 [CityPickerSheet] 内 selectCity 并关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityPickerSheet(
    vm: CreateViewModel,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    var keyword by remember { mutableStateOf("") }
    var lastUsed by remember { mutableStateOf<City?>(null) }

    // 上次使用城市（meta 同步读放 IO）
    LaunchedEffect(Unit) {
        lastUsed = withContext(Dispatchers.IO) { vm.lastUsedCity() }
    }

    val cities = remember(keyword) { vm.searchCities(keyword) }

    val context = LocalContext.current
    // 定位状态：未授权 → 顶部「使用当前位置」引导条；已授权 → 附近分区（无 fix 不展示）
    var permitted by remember { mutableStateOf(true) }
    var nearby by remember { mutableStateOf<List<City>?>(null) }
    var showLocGuide by remember { mutableStateOf(false) }
    var showLocSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun reloadLocation() {
        val result = withContext(Dispatchers.Default) {
            vm.isLocationPermitted() to vm.nearbyCities()
        }
        permitted = result.first
        nearby = result.second
    }
    LaunchedEffect(Unit) { reloadLocation() }
    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch { reloadLocation() }
        } else {
            val activity = context as? Activity
            if (activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            ) {
                // 永久拒绝：系统不再弹窗，引导到应用详情页
                showLocSettings = true
            }
        }
    }
    val groups = remember(cities, nearby, L) {
        val showNearby = !nearby.isNullOrEmpty() && keyword.isBlank()
        val base = if (showNearby) {
            // 附近城市已单独置顶，从省份分组剔除避免 LazyColumn 重复 key
            val ids = nearby!!.map { it.id }.toSet()
            cities.filter { it.id !in ids }.groupBy { it.province }
        } else {
            cities.groupBy { it.province }
        }
        if (showNearby) linkedMapOf(L.cpNearbyHeader to nearby!!) + base else base
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        // 面板较高时默认会以半展开态打开，顶部（定位推荐条 / 附近城市）被折在上沿外，
        // 需手动上拉才能看到；这里直接跳过半展开态，打开即完整展示
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .padding(horizontal = 24.dp),
        ) {
            Text(text = L.cpTitle, style = TimartType.titleSerif, color = InkPrimary)

            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                singleLine = true,
                placeholder = {
                    Text(text = L.cpSearchHint, style = TimartType.body, color = InkDisabled)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TimeGold,
                    unfocusedBorderColor = TrackHairline,
                    focusedTextColor = InkPrimary,
                    unfocusedTextColor = InkPrimary,
                    cursorColor = TimeGold,
                ),
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            )

            // 「使用当前位置」固定条：置于搜索框正下方（列表分组之前），打开面板即见、
            // 不随列表滚动，任何权限/定位状态下都可点——
            // 未授权 → 引导授权；已授权有定位 → 行尾显示最近城市，点击直接选定；
            // 已授权无定位 → 行内提示定位失败
            var locFail by remember { mutableStateOf(false) }
            val nearestCity = nearby?.firstOrNull()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepCharcoal)
                    .clickable {
                        when {
                            !permitted -> {
                                locFail = false
                                showLocGuide = true
                            }
                            nearestCity != null -> {
                                vm.selectCity(nearestCity)
                                onDismiss()
                            }
                            else -> locFail = true
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Text(text = L.mapUseCurrent, style = TimartType.body, color = TimeGold)
                nearestCity?.let { city ->
                    Text(
                        text = " · ${city.name}",
                        style = TimartType.body,
                        color = InkSecondary,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "›", style = TimartType.titleSerif, color = InkSecondary)
            }
            if (locFail && permitted) {
                Text(
                    text = L.cpLocFail,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
            ) {
                groups.forEach { (province, citiesInProvince) ->
                    // 省份分组头
                    item(key = "header_$province") {
                        Text(
                            text = province,
                            style = TimartType.caption,
                            color = InkDisabled,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(TrackHairline),
                        )
                    }
                    // 城市行
                    items(
                        count = citiesInProvince.size,
                        key = { i -> citiesInProvince[i].id },
                    ) { i ->
                        val city = citiesInProvince[i]
                        val selected = city.id == vm.selectedCity?.id
                        val isLastUsed = city.id == lastUsed?.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.selectCity(city)
                                    onDismiss()
                                }
                                .padding(vertical = 13.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(
                                        color = if (selected) TimeGold else TrackHairline,
                                        shape = CircleShape,
                                    ),
                            )
                            Text(
                                text = city.name,
                                style = TimartType.body,
                                color = if (selected) TimeGold else InkPrimary,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                            if (isLastUsed) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = L.cpLastUsed,
                                    style = TimartType.caption,
                                    color = InkSecondary,
                                )
                            }
                        }
                    }
                }
                if (cities.isEmpty()) {
                    item {
                        Text(
                            text = L.cpNoMatch,
                            style = TimartType.body,
                            color = InkSecondary,
                            modifier = Modifier.padding(top = 32.dp, bottom = 24.dp),
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // 未授权引导：说明先于系统弹窗（T14 模式）；永久拒绝则引导到系统设置
    if (showLocGuide) {
        PermissionGuideDialog(
            title = L.gfPermTitle,
            body = L.permLocationDesc,
            confirmText = L.goEnable,
            onConfirm = {
                showLocGuide = false
                locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            onDismiss = { showLocGuide = false },
        )
    }
    if (showLocSettings) {
        PermissionGuideDialog(
            title = L.gfPermTitle,
            body = L.cpLocSettingsHint,
            confirmText = L.goEnable,
            onConfirm = {
                showLocSettings = false
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null)),
                    )
                }
            },
            onDismiss = { showLocSettings = false },
        )
    }
}
