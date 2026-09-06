package com.muxiao.timart.ui.create.rules.condition

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.domain.model.WeatherType
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.context.WifiSsidState
import com.muxiao.timart.ui.components.PermissionGuideDialog
import com.muxiao.timart.utils.network.WifiSsidReader
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType

/**
 * 环境 / 天气条件表单（架构 §2.15）：
 * 天气条件判定时读取的是**封存时刻的快照城市**（城市锁定，创建后不再因天气联网）；
 * 无快照城市时表单禁用并提示先选城市。
 */
@Composable
fun WeatherTypeForm(
    snapshotCityName: String?,
    onConfirm: (UnlockCondition.WeatherType) -> Unit,
) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf<String>()) }
    val enabled = snapshotCityName != null
    val weatherEntries = WeatherType.entries.map { type ->
        type.name to (ConditionText.weatherName(type.name, RuntimeSettings.resolvedLang) ?: type.name)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.efTitle, style = TimartType.titleSerif, color = InkPrimary)
        if (enabled) {
            Text(
                text = L.efCityLockedFmt.format(snapshotCityName),
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                text = L.efNeedCity,
                style = TimartType.caption,
                color = InkDisabled,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        weatherEntries.chunked(4).forEach { rowEntries ->
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                rowEntries.forEach { (name, label) ->
                    SelectPill(
                        label = label,
                        selected = name in selected,
                        onClick = {
                            if (enabled) {
                                selected = if (name in selected) selected - name else selected + name
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 最后一行不足 4 个时补占位保持等宽
                repeat(4 - rowEntries.size) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        FormConfirmButton(enabled = enabled && selected.isNotEmpty(), label = L.confirm) {
            onConfirm(UnlockCondition.WeatherType(weatherTypes = selected))
        }
    }
}

// ================= 气温阈值 =================

/**
 * 气温阈值表单：高于 / 低于 / 区间 三模式，复用天气链路（快照城市实时气温）。
 * 无快照城市时禁用（与 WeatherTypeForm 同语义）。
 */
@Composable
fun TemperatureThresholdForm(
    snapshotCityName: String?,
    onConfirm: (UnlockCondition.TemperatureThreshold) -> Unit,
) {
    val L = LocalStrings.current
    // 模式：0 高于 / 1 低于 / 2 区间
    var mode by remember { mutableIntStateOf(0) }
    var value by remember { mutableIntStateOf(30) }
    var rangeMin by remember { mutableIntStateOf(0) }
    var rangeMax by remember { mutableIntStateOf(30) }
    val enabled = snapshotCityName != null
    val rangeValid = mode != 2 || rangeMin < rangeMax

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.tfTitle, style = TimartType.titleSerif, color = InkPrimary)
        if (enabled) {
            Text(
                text = L.efCityLockedFmt.format(snapshotCityName),
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = L.tfDesc,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                text = L.efNeedCity,
                style = TimartType.caption,
                color = InkDisabled,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            SelectPill(
                label = L.dfAbove,
                selected = mode == 0,
                onClick = { mode = 0 },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = L.dfBelow,
                selected = mode == 1,
                onClick = { mode = 1 },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = L.dfRange,
                selected = mode == 2,
                onClick = { mode = 2 },
                modifier = Modifier.weight(1f),
            )
        }

        val sliderColors = SliderDefaults.colors(
            thumbColor = TimeGold,
            activeTrackColor = TimeGold,
            inactiveTrackColor = SurfaceRaise,
        )
        if (mode == 2) {
            Text(
                text = L.tfRangeMinFmt.format(ConditionText.formatTemp(rangeMin.toDouble())),
                style = TimartType.body,
                color = InkPrimary,
                modifier = Modifier.padding(top = 18.dp),
            )
            Slider(
                value = rangeMin.toFloat(),
                onValueChange = {
                    rangeMin = it.toInt().coerceIn(MIN_TEMP, rangeMax - 1)
                },
                valueRange = MIN_TEMP.toFloat()..MAX_TEMP.toFloat(),
                colors = sliderColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = L.tfRangeMaxFmt.format(ConditionText.formatTemp(rangeMax.toDouble())),
                style = TimartType.body,
                color = InkPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
            Slider(
                value = rangeMax.toFloat(),
                onValueChange = {
                    rangeMax = it.toInt().coerceAtLeast(rangeMin + 1)
                },
                valueRange = MIN_TEMP.toFloat()..MAX_TEMP.toFloat(),
                colors = sliderColors,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = if (mode == 0) L.tfAboveFmt.format(ConditionText.formatTemp(value.toDouble()))
                  else L.tfBelowFmt.format(ConditionText.formatTemp(value.toDouble())),
                style = TimartType.body.copy(color = TimeGold),
                modifier = Modifier.padding(top = 18.dp),
            )
            Slider(
                value = value.toFloat(),
                onValueChange = { value = it.toInt().coerceIn(MIN_TEMP, MAX_TEMP) },
                valueRange = MIN_TEMP.toFloat()..MAX_TEMP.toFloat(),
                colors = sliderColors,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FormConfirmButton(enabled = enabled && rangeValid, label = L.confirm) {
            when (mode) {
                0 -> onConfirm(UnlockCondition.TemperatureThreshold(minC = value.toDouble(), maxC = null))
                1 -> onConfirm(UnlockCondition.TemperatureThreshold(minC = null, maxC = value.toDouble()))
                else -> onConfirm(
                    UnlockCondition.TemperatureThreshold(
                        minC = rangeMin.toDouble(),
                        maxC = rangeMax.toDouble(),
                    ),
                )
            }
        }
    }
}

// ================= Wi-Fi SSID =================

/**
 * Wi-Fi SSID 表单：输入若干 SSID（ chips 可删），判定时比对当前连接名。
 * 系统前置（定位权限 + 定位服务）在进表单时说明并引导申请（说明先于系统弹窗）。
 */
@Composable
fun SsidMatchForm(
    onConfirm: (UnlockCondition.SsidMatch) -> Unit,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    var ssids by remember { mutableStateOf(listOf<String>()) }
    var input by remember { mutableStateOf("") }
    // 读不到当前 Wi-Fi 时的行内提示（未连接 / 定位服务未开）
    var noCurrentHint by remember { mutableStateOf(false) }
    // 一键取当前网络：薄包装（无状态），随表单创建即可
    val wifiReader = remember { WifiSsidReader(context) }

    // 定位权限引导：27+ 读取 SSID 需要 FINE_LOCATION（说明先于系统弹窗）
    var showGuide by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        val granted = Build.VERSION.SDK_INT < 27 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) showGuide = true
    }
    if (showGuide) {
        PermissionGuideDialog(
            title = L.sfTitle,
            body = L.sfDesc,
            onConfirm = {
                showGuide = false
                if (Build.VERSION.SDK_INT >= 27) {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            onDismiss = { showGuide = false },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.sfTitle, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = L.sfDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )

        // 已添加 chips
        if (ssids.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                ssids.forEach { ssid ->
                    Text(
                        text = "$ssid ×",
                        style = TimartType.caption,
                        color = InkSecondary,
                        modifier = Modifier
                            .background(DeepCharcoal, RoundedCornerShape(10.dp))
                            .clickable { ssids = ssids - ssid }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // 输入行
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .background(DeepCharcoal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = TimartType.body.copy(color = InkPrimary),
                cursorBrush = SolidColor(TimeGold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(text = L.sfHint, style = TimartType.body, color = InkDisabled)
                    }
                    inner()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
            )
            TextButton(onClick = {
                val name = input.trim()
                if (name.isNotEmpty() && name !in ssids) {
                    ssids = ssids + name
                    input = ""
                }
            }) {
                Text(text = L.sfAdd, color = TimeGold, style = TimartType.body)
            }
        }

        // 一键取当前连接的 Wi-Fi（免手写；无权限时走引导，读不到时行内提示）
        TextButton(onClick = {
            val info = wifiReader.current()
            when (info.state) {
                WifiSsidState.CONNECTED -> {
                    noCurrentHint = false
                    info.ssid?.let { ssid ->
                        if (ssid !in ssids) ssids = ssids + ssid
                    }
                }
                WifiSsidState.NO_PERMISSION -> showGuide = true
                WifiSsidState.NOT_CONNECTED, WifiSsidState.NO_LOCATION_SERVICE -> noCurrentHint = true
            }
        }) {
            Text(text = L.sfUseCurrent, color = TimeGold, style = TimartType.body)
        }
        if (noCurrentHint) {
            Text(
                text = L.sfNoCurrent,
                style = TimartType.caption,
                color = InkDisabled,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        FormConfirmButton(enabled = ssids.isNotEmpty(), label = L.confirm) {
            onConfirm(UnlockCondition.SsidMatch(ssids = ssids.toSet()))
        }
    }
}

/** 气温滑条范围（°C） */
private const val MIN_TEMP = -30
private const val MAX_TEMP = 45
