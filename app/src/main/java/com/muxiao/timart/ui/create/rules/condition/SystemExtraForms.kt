package com.muxiao.timart.ui.create.rules.condition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.PlugKind
import com.muxiao.timart.domain.model.unlock.PoseKind
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType

/**
 * 储备池 v4 设备 / 系统状态条件表单（delta-prd-vs-code.md D-1.2 §2）：
 * 深色模式 / 勿扰 / 设备姿态 / 屏幕亮度 / 媒体静音 / VPN / 充电方式 / 电池温度 /
 * 横竖屏 / 指定路由器 / 蓝牙设备 / 捂住手机 / 刚重启 / 已装应用。
 */

// ================= 二元状态开关（深色 / 勿扰 / 静音 / VPN / 横竖屏 / 捂住手机） =================

@Composable
fun DarkThemeForm(onConfirm: (UnlockCondition.DarkTheme) -> Unit) {
    val L = LocalStrings.current
    var dark by remember { mutableStateOf(true) }
    val condition = UnlockCondition.DarkTheme(dark)

    ExtendFormScaffold(title = L.condDarkTheme, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.DarkTheme(true), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = dark,
                onClick = { dark = true },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.DarkTheme(false), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = !dark,
                onClick = { dark = false },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun DoNotDisturbForm(onConfirm: (UnlockCondition.DoNotDisturb) -> Unit) {
    val L = LocalStrings.current
    var active by remember { mutableStateOf(true) }
    val condition = UnlockCondition.DoNotDisturb(active)

    ExtendFormScaffold(title = L.condDnd, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.DoNotDisturb(true), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = active,
                onClick = { active = true },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.DoNotDisturb(false), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = !active,
                onClick = { active = false },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun MediaVolumeForm(onConfirm: (UnlockCondition.MediaVolume) -> Unit) {
    val L = LocalStrings.current
    var muted by remember { mutableStateOf(true) }
    val condition = UnlockCondition.MediaVolume(muted)

    ExtendFormScaffold(title = L.condMediaVolume, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.MediaVolume(true), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = muted,
                onClick = { muted = true },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.MediaVolume(false), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = !muted,
                onClick = { muted = false },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun VpnActiveForm(onConfirm: (UnlockCondition.VpnActive) -> Unit) {
    val L = LocalStrings.current
    var active by remember { mutableStateOf(true) }
    val condition = UnlockCondition.VpnActive(active)

    ExtendFormScaffold(title = L.condVpn, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.VpnActive(true), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = active,
                onClick = { active = true },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.VpnActive(false), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = !active,
                onClick = { active = false },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun OrientationForm(onConfirm: (UnlockCondition.Orientation) -> Unit) {
    val L = LocalStrings.current
    var landscape by remember { mutableStateOf(true) }
    val condition = UnlockCondition.Orientation(landscape)

    ExtendFormScaffold(title = L.condOrientation, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.Orientation(true), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = landscape,
                onClick = { landscape = true },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.Orientation(false), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = !landscape,
                onClick = { landscape = false },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun ProximityCoveredForm(onConfirm: (UnlockCondition.ProximityCovered) -> Unit) {
    val L = LocalStrings.current
    ExtendFormScaffold(
        title = L.condProximity,
        valueCondition = UnlockCondition.ProximityCovered,
        onConfirm = { onConfirm(UnlockCondition.ProximityCovered) },
    ) {}
}

// ================= 设备姿态 =================

@Composable
fun DevicePoseForm(onConfirm: (UnlockCondition.DevicePose) -> Unit) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf(PoseKind.FLAT)) }

    ExtendFormScaffold(
        title = L.condPose,
        valueCondition = UnlockCondition.DevicePose(selected),
        enabled = selected.isNotEmpty(),
        onConfirm = { onConfirm(UnlockCondition.DevicePose(selected)) },
    ) {
        SelectionGrid(
            items = PoseKind.entries.toList(),
            selected = { it in selected },
            label = { ConditionText.poseName(it, com.muxiao.timart.utils.RuntimeSettings.resolvedLang) },
            onToggle = { kind -> selected = if (kind in selected) selected - kind else selected + kind },
        )
    }
}

// ================= 屏幕亮度 =================

@Composable
fun ScreenBrightnessForm(onConfirm: (UnlockCondition.ScreenBrightness) -> Unit) {
    val L = LocalStrings.current
    var level by remember { mutableIntStateOf(40) }
    val condition = UnlockCondition.ScreenBrightness(level)

    ExtendFormScaffold(title = L.condBrightness, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = level, range = 5f..250f) { level = it }
    }
}

// ================= 充电方式 =================

@Composable
fun PlugTypeForm(onConfirm: (UnlockCondition.PlugType) -> Unit) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf(PlugKind.WIRELESS)) }

    ExtendFormScaffold(
        title = L.condPlugType,
        valueCondition = UnlockCondition.PlugType(selected),
        enabled = selected.isNotEmpty(),
        onConfirm = { onConfirm(UnlockCondition.PlugType(selected)) },
    ) {
        SelectionGrid(
            items = PlugKind.entries.toList(),
            selected = { it in selected },
            label = { ConditionText.plugName(it, com.muxiao.timart.utils.RuntimeSettings.resolvedLang) },
            onToggle = { kind -> selected = if (kind in selected) selected - kind else selected + kind },
        )
    }
}

// ================= 电池温度 =================

@Composable
fun BatteryTempForm(onConfirm: (UnlockCondition.BatteryTemp) -> Unit) {
    val L = LocalStrings.current
    var useMin by remember { mutableStateOf(true) }
    var useMax by remember { mutableStateOf(false) }
    var minC by remember { mutableIntStateOf(20) }
    var maxC by remember { mutableIntStateOf(40) }
    val condition = UnlockCondition.BatteryTemp(
        minC = if (useMin) minC.toDouble() else null,
        maxC = if (useMax) maxC.toDouble() else null,
    )

    ExtendFormScaffold(title = L.condBatteryTemp, valueCondition = condition, enabled = useMin || useMax, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = minC, range = 0f..60f, steps = 59) { minC = it }
        IntSlider(value = maxC, range = 0f..60f, steps = 59) { maxC = it }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            SelectPill(label = L.condAtLeast, selected = useMin, onClick = { useMin = !useMin }, modifier = Modifier.weight(1f))
            SelectPill(
                label = L.condAtMost,
                selected = useMax,
                onClick = { useMax = !useMax },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
    }
}

// ================= 指定路由器（BSSID） =================

@Composable
fun SsidBssidMatchForm(
    vm: com.muxiao.timart.ui.create.CreateViewModel,
    onConfirm: (UnlockCondition.SsidBssidMatch) -> Unit,
) {
    val L = LocalStrings.current
    var bssid by remember { mutableStateOf("") }
    var readFailed by remember { mutableStateOf(false) }
    val condition = UnlockCondition.SsidBssidMatch(
        bssids = bssid.trim().split("、", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet(),
    )

    ExtendFormScaffold(
        title = L.condBssid,
        valueCondition = if (condition.bssids.isEmpty()) null else condition,
        enabled = condition.bssids.isNotEmpty(),
        onConfirm = { onConfirm(condition) },
    ) {
        Text(
            text = L.condBssid,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
        BasicTextField(
            value = bssid,
            onValueChange = { bssid = it },
            singleLine = true,
            textStyle = TimartType.body.copy(color = InkPrimary),
            cursorBrush = SolidColor(TimeGold),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(SurfaceRaise)
                .padding(12.dp),
        )
        Row(modifier = Modifier.padding(top = 8.dp)) {
            SelectPill(
                label = L.sfUseCurrentBssid,
                selected = false,
                onClick = {
                    val current = vm.currentWifiBssid()
                    if (current != null) {
                        bssid = current
                        readFailed = false
                    } else {
                        readFailed = true
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (readFailed) {
            Text(
                text = L.sfNoCurrentBssid,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ================= 指定蓝牙设备 =================

@Composable
fun BluetoothDeviceForm(onConfirm: (UnlockCondition.BluetoothDevice) -> Unit) {
    val L = LocalStrings.current
    var names by remember { mutableStateOf("") }
    val condition = UnlockCondition.BluetoothDevice(
        deviceNames = names.split("、", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet(),
    )

    ExtendFormScaffold(
        title = L.condBluetooth,
        valueCondition = if (condition.deviceNames.isEmpty()) null else condition,
        enabled = condition.deviceNames.isNotEmpty(),
        onConfirm = { onConfirm(condition) },
    ) {
        BasicTextField(
            value = names,
            onValueChange = { names = it },
            singleLine = true,
            textStyle = TimartType.body.copy(color = InkPrimary),
            cursorBrush = SolidColor(TimeGold),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .background(SurfaceRaise)
                .padding(12.dp),
        )
        Text(
            text = L.btFormHint,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ================= 刚重启手机 =================

@Composable
fun FreshBootForm(onConfirm: (UnlockCondition.FreshBoot) -> Unit) {
    val L = LocalStrings.current
    var minutes by remember { mutableIntStateOf(10) }
    val condition = UnlockCondition.FreshBoot(minutes)

    ExtendFormScaffold(title = L.condFreshBoot, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = minutes, range = 1f..120f) { minutes = it }
    }
}

// ================= 已安装某应用 =================

@Composable
fun InstalledAppForm(onConfirm: (UnlockCondition.InstalledApp) -> Unit) {
    val L = LocalStrings.current
    var packageName by remember { mutableStateOf("") }
    val condition = UnlockCondition.InstalledApp(packageName.trim())

    ExtendFormScaffold(
        title = L.condInstalledApp,
        valueCondition = if (packageName.isBlank()) null else condition,
        enabled = packageName.isNotBlank(),
        onConfirm = { onConfirm(condition) },
    ) {
        Text(
            text = L.installedPkgLabel,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
        BasicTextField(
            value = packageName,
            onValueChange = { packageName = it },
            singleLine = true,
            textStyle = TimartType.body.copy(color = InkPrimary),
            cursorBrush = SolidColor(TimeGold),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(SurfaceRaise)
                .padding(12.dp),
        )
    }
}
