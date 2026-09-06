package com.muxiao.timart.ui.create.rules.condition

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.NetType
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.ui.components.PermissionGuideDialog
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType

/**
 * 设备状态类条件表单（架构 §2.15）：电量 / 充电 / 今日步数 / 网络类型。
 * 表单只产出条件对象，不做任何设备读取（判定在解锁时刻）。
 */

// ================= 电量 =================

@Composable
fun BatteryLevelForm(
    onConfirm: (UnlockCondition.BatteryLevel) -> Unit,
) {
    val L = LocalStrings.current
    // 模式：0 高于 / 1 低于 / 2 区间
    var mode by remember { mutableIntStateOf(0) }
    var value by remember { mutableIntStateOf(80) }
    var rangeMin by remember { mutableIntStateOf(20) }
    var rangeMax by remember { mutableIntStateOf(80) }
    val rangeValid = mode != 2 || rangeMin < rangeMax

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.dfBatteryTitle, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = L.dfBatteryDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )

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

        if (mode == 2) {
            Text(
                text = L.dfRangeMinFmt.format(rangeMin),
                style = TimartType.body,
                color = InkPrimary,
                modifier = Modifier.padding(top = 18.dp),
            )
            PercentSlider(value = rangeMin) { v ->
                rangeMin = v.coerceAtMost(rangeMax - 1)
            }
            Text(
                text = L.dfRangeMaxFmt.format(rangeMax),
                style = TimartType.body,
                color = InkPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
            PercentSlider(value = rangeMax) { v ->
                rangeMax = v.coerceAtLeast(rangeMin + 1)
            }
            if (!rangeValid) {
                Text(
                    text = L.dfRangeInvalid,
                    style = TimartType.caption,
                    color = InkDisabled,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Text(
                text = if (mode == 0) L.dfAboveValueFmt.format(value)
                  else L.dfBelowValueFmt.format(value),
                style = TimartType.body,
                color = InkPrimary,
                modifier = Modifier.padding(top = 18.dp),
            )
            PercentSlider(value = value) { value = it }
        }

        FormConfirmButton(enabled = rangeValid, label = L.confirm) {
            when (mode) {
                0 -> onConfirm(UnlockCondition.BatteryLevel(min = value, max = null))
                1 -> onConfirm(UnlockCondition.BatteryLevel(min = null, max = value))
                else -> onConfirm(UnlockCondition.BatteryLevel(min = rangeMin, max = rangeMax))
            }
        }
    }
}

// ================= 充电状态 =================

@Composable
fun ChargingStateForm(
    onConfirm: (UnlockCondition.ChargingState) -> Unit,
) {
    val L = LocalStrings.current
    var isCharging by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.dfChargingTitle, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = L.dfChargingDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            SelectPill(
                label = L.dfChargingPill,
                selected = isCharging,
                onClick = { isCharging = true },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = L.dfNotChargingPill,
                selected = !isCharging,
                onClick = { isCharging = false },
                modifier = Modifier.weight(1f),
            )
        }
        FormConfirmButton(enabled = true, label = L.confirm) {
            onConfirm(UnlockCondition.ChargingState(isCharging = isCharging))
        }
    }
}

// ================= 今日步数 =================

@Composable
fun StepCountForm(
    onConfirm: (UnlockCondition.StepCount) -> Unit,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    var steps by remember { mutableIntStateOf(5000) }

    // 活动记录权限引导（T14）：说明先于系统弹窗。
    // 此前全工程从未申请过 ACTIVITY_RECOGNITION（manifest 已声明），导致 API 29+
    // 步数条件永远 stepUnavailable——创建时机引导是唯一修复入口。
    var showGuide by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        val hasSensor = (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasSensor && !granted) showGuide = true
    }
    if (showGuide) {
        PermissionGuideDialog(
            title = L.dfStepTitle,
            body = L.permActivityDesc,
            onConfirm = {
                showGuide = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            },
            onDismiss = { showGuide = false },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.dfStepTitle, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = L.dfStepDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        // 硬件计步器系统限制注明（重启当日清零 / 清数据或重装历史归零）
        Text(
            text = L.stepLimitNote,
            style = TimartType.caption,
            color = InkDisabled,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = L.dfStepValueFmt.format(steps),
            style = TimartType.body.copy(color = TimeGold),
            modifier = Modifier
                .padding(top = 20.dp)
                .align(Alignment.CenterHorizontally),
        )
        Slider(
            value = steps.toFloat(),
            onValueChange = { steps = (it / 500).toInt() * 500 },
            valueRange = 1000f..30000f,
            colors = SliderDefaults.colors(
                thumbColor = TimeGold,
                activeTrackColor = TimeGold,
                inactiveTrackColor = com.muxiao.timart.ui.theme.SurfaceRaise,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        FormConfirmButton(enabled = true, label = L.confirm) {
            onConfirm(UnlockCondition.StepCount(minTodayStep = steps))
        }
    }
}

// ================= 网络类型 =================

@Composable
fun NetworkTypeForm(
    onConfirm: (UnlockCondition.NetworkType) -> Unit,
) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf<NetType>()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.dfNetTitle, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = L.dfNetDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            NetType.entries.forEach { type ->
                SelectPill(
                    label = ConditionText.netShortName(type, RuntimeSettings.resolvedLang),
                    selected = type in selected,
                    onClick = {
                        selected = if (type in selected) selected - type else selected + type
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        FormConfirmButton(enabled = selected.isNotEmpty(), label = L.confirm) {
            onConfirm(UnlockCondition.NetworkType(types = selected))
        }
    }
}

// ================= 连续步数（streak） =================

/**
 * 连续步数表单：连续 N 天 × 每天目标步数。
 * 判定含今日：此前 N-1 天须各有历史记录且达标（缺记录日 fail-closed，见 StepCounterReader）。
 */
@Composable
fun StepStreakForm(
    onConfirm: (UnlockCondition.StepStreak) -> Unit,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    var days by remember { mutableIntStateOf(7) }
    var goal by remember { mutableIntStateOf(6000) }

    // 活动记录权限引导（与 StepCountForm 同通道）
    var showGuide by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        val hasSensor = (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasSensor && !granted) showGuide = true
    }
    if (showGuide) {
        PermissionGuideDialog(
            title = L.kfTitle,
            body = L.permActivityDesc,
            onConfirm = {
                showGuide = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            },
            onDismiss = { showGuide = false },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.kfTitle, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = L.kfDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(
            text = L.kfDaysFmt.format(days),
            style = TimartType.body.copy(color = TimeGold),
            modifier = Modifier.padding(top = 20.dp),
        )
        Slider(
            value = days.toFloat(),
            onValueChange = { days = it.toInt().coerceIn(2, 30) },
            valueRange = 2f..30f,
            colors = SliderDefaults.colors(
                thumbColor = TimeGold,
                activeTrackColor = TimeGold,
                inactiveTrackColor = com.muxiao.timart.ui.theme.SurfaceRaise,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = L.kfGoalFmt.format(goal),
            style = TimartType.body.copy(color = TimeGold),
            modifier = Modifier.padding(top = 12.dp),
        )
        Slider(
            value = goal.toFloat(),
            onValueChange = { goal = (it / 500).toInt() * 500 },
            valueRange = 1000f..30000f,
            colors = SliderDefaults.colors(
                thumbColor = TimeGold,
                activeTrackColor = TimeGold,
                inactiveTrackColor = com.muxiao.timart.ui.theme.SurfaceRaise,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = L.kfStreakNote,
            style = TimartType.caption,
            color = InkDisabled,
            modifier = Modifier.padding(top = 8.dp),
        )
        // 硬件计步器系统限制注明（与 StepCountForm 同口径）
        Text(
            text = L.stepLimitNote,
            style = TimartType.caption,
            color = InkDisabled,
            modifier = Modifier.padding(top = 4.dp),
        )

        FormConfirmButton(enabled = true, label = L.confirm) {
            onConfirm(UnlockCondition.StepStreak(days = days, goal = goal))
        }
    }
}

// ================= 共用滑条 =================

/** 百分比滑条（0–100 整数） */
@Composable
private fun PercentSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = 0f..100f,
        colors = SliderDefaults.colors(
            thumbColor = TimeGold,
            activeTrackColor = TimeGold,
            inactiveTrackColor = com.muxiao.timart.ui.theme.SurfaceRaise,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
