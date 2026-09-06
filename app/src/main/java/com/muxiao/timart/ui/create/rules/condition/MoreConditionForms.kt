package com.muxiao.timart.ui.create.rules.condition

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.MoonPhaseKind
import com.muxiao.timart.domain.model.unlock.MotionKind
import com.muxiao.timart.domain.model.unlock.SunPhaseKind
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.WeatherMetricKind
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.utils.RuntimeSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 扩展条件表单（架构 §2.15 增补）：精确时刻 / 满 N 分钟 / 每月 N 号 / 每年纪念日 /
 * 日出日落 / 天气指标 / 月相 / 闹钟之前 / 省电 / 静音 / 耳机 / 运动状态 / 指南针 / 海拔。
 * 表单只产出条件对象；动态值文案统一走 ConditionText（三语同源），不重复维护。
 */

// ================= 共用小件 =================

/** 表单骨架：类型标题 + 通用说明 + 当前取值句 + 内容 + 确认按钮 */
@Composable
internal fun ExtendFormScaffold(
    title: String,
    valueCondition: UnlockCondition?,
    enabled: Boolean = true,
    onConfirm: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val L = LocalStrings.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = L.condFormDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (valueCondition != null) {
            Text(
                text = ConditionText.conditionSentence(valueCondition, RuntimeSettings.resolvedLang),
                style = TimartType.body.copy(color = TimeGold),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        content()
        FormConfirmButton(enabled = enabled, label = L.confirm, onClick = onConfirm)
    }
}

/** 通用整数滑条 */
@Composable
internal fun IntSlider(
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Int) -> Unit,
) {
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = TimeGold,
            activeTrackColor = TimeGold,
            inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent.copy(alpha = 0.2f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
}

// ================= 精确时刻 =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedDateTimeForm(onConfirm: (UnlockCondition.FixedDateTime) -> Unit) {
    val L = LocalStrings.current
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
    )
    var hour by remember { mutableIntStateOf(0) }
    var minute by remember { mutableIntStateOf(0) }

    ExtendFormScaffold(
        title = L.condExactTime,
        valueCondition = null,
        enabled = pickerState.selectedDateMillis != null,
        onConfirm = {
            val millis = pickerState.selectedDateMillis ?: return@ExtendFormScaffold
            val date: LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            val dateTime = "%04d-%02d-%02dT%02d:%02d".format(date.year, date.monthValue, date.dayOfMonth, hour, minute)
            onConfirm(UnlockCondition.FixedDateTime(targetDateTime = dateTime))
        },
    ) {
        androidx.compose.material3.DatePicker(
            state = pickerState,
            showModeToggle = false,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "%02d:%02d".format(hour, minute),
            style = TimartType.titleSerif.copy(color = TimeGold),
            modifier = Modifier
                .padding(top = 10.dp)
                .align(Alignment.CenterHorizontally),
        )
        IntSlider(value = hour, range = 0f..23f, steps = 22) { hour = it }
        IntSlider(value = minute, range = 0f..59f, steps = 59) { minute = it }
    }
}

// ================= 封存满 N 分钟 =================

@Composable
fun MinElapsedMinutesForm(onConfirm: (UnlockCondition.MinElapsedMinutes) -> Unit) {
    val L = LocalStrings.current
    var minutes by remember { mutableIntStateOf(60) }
    val condition = UnlockCondition.MinElapsedMinutes(minutes.toLong())

    ExtendFormScaffold(title = L.condMinMinutes, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = minutes, range = 5f..10_080f) { minutes = it }
    }
}

// ================= 每月 N 号 =================

@Composable
fun MonthlyDayForm(onConfirm: (UnlockCondition.MonthlyDay) -> Unit) {
    val L = LocalStrings.current
    var day by remember { mutableIntStateOf(1) }
    val condition = UnlockCondition.MonthlyDay(day)

    ExtendFormScaffold(title = L.condMonthlyDay, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = day, range = 1f..28f, steps = 26) { day = it }
    }
}

// ================= 每年纪念日 =================

@Composable
fun YearlyDateForm(onConfirm: (UnlockCondition.YearlyDate) -> Unit) {
    val L = LocalStrings.current
    var month by remember { mutableIntStateOf(1) }
    var day by remember { mutableIntStateOf(1) }
    val condition = UnlockCondition.YearlyDate(month, day)

    ExtendFormScaffold(title = L.condYearly, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = month, range = 1f..12f, steps = 10) { month = it }
        IntSlider(value = day, range = 1f..31f, steps = 29) { day = it }
    }
}

// ================= 日出 / 日落 / 白天夜晚 =================

@Composable
fun SunPhaseForm(onConfirm: (UnlockCondition.SunPhase) -> Unit) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf(SunPhaseKind.SUNRISE)) }
    val kinds = SunPhaseKind.entries

    ExtendFormScaffold(
        title = L.condSunPhase,
        valueCondition = UnlockCondition.SunPhase(selected),
        enabled = selected.isNotEmpty(),
        onConfirm = { onConfirm(UnlockCondition.SunPhase(selected)) },
    ) {
        kinds.chunked(2).forEach { rowKinds ->
            Row(modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()) {
                rowKinds.forEach { kind ->
                    SelectPill(
                        label = ConditionText.sunName(kind, RuntimeSettings.resolvedLang),
                        selected = kind in selected,
                        onClick = {
                            selected = if (kind in selected) selected - kind else selected + kind
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                }
                repeat(2 - rowKinds.size) { androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

// ================= 天气指标（湿度/风速/气压/紫外线） =================

@Composable
fun WeatherMetricForm(onConfirm: (UnlockCondition.WeatherMetric) -> Unit) {
    val L = LocalStrings.current
    var metric by remember { mutableStateOf(WeatherMetricKind.HUMIDITY) }
    var useMin by remember { mutableStateOf(true) }
    var useMax by remember { mutableStateOf(false) }
    var minV by remember { mutableStateOf(50f) }
    var maxV by remember { mutableStateOf(60f) }
    val ranges = mapOf(
        WeatherMetricKind.HUMIDITY to 0f..100f,
        WeatherMetricKind.WIND to 0f..120f,
        WeatherMetricKind.PRESSURE to 950f..1050f,
        WeatherMetricKind.UV to 0f..12f,
    )

    ExtendFormScaffold(
        title = L.condWeatherMetric,
        valueCondition = UnlockCondition.WeatherMetric(
            metric = metric,
            min = if (useMin) minV.toDouble() else null,
            max = if (useMax) maxV.toDouble() else null,
        ),
        enabled = useMin || useMax,
        onConfirm = {
            onConfirm(
                UnlockCondition.WeatherMetric(
                    metric = metric,
                    min = if (useMin) minV.toDouble() else null,
                    max = if (useMax) maxV.toDouble() else null,
                ),
            )
        },
    ) {
        WeatherMetricKind.entries.chunked(2).forEach { rowMetrics ->
            Row(modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()) {
                rowMetrics.forEach { kind ->
                    SelectPill(
                        label = ConditionText.metricName(kind, RuntimeSettings.resolvedLang),
                        selected = kind == metric,
                        onClick = { metric = kind },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                }
                repeat(2 - rowMetrics.size) { androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f)) }
            }
        }
        val range = ranges[metric]!!
        IntSlider(value = minV.toInt(), range = range, steps = ((range.endInclusive - range.start) / 5).toInt() - 1) { minV = it.toFloat() }
        IntSlider(value = maxV.toInt(), range = range, steps = ((range.endInclusive - range.start) / 5).toInt() - 1) { maxV = it.toFloat() }
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

// ================= 月相 =================

@Composable
fun MoonPhaseForm(onConfirm: (UnlockCondition.MoonPhase) -> Unit) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf(MoonPhaseKind.FULL)) }

    ExtendFormScaffold(
        title = L.condMoonPhase,
        valueCondition = UnlockCondition.MoonPhase(selected),
        enabled = selected.isNotEmpty(),
        onConfirm = { onConfirm(UnlockCondition.MoonPhase(selected)) },
    ) {
        MoonPhaseKind.entries.chunked(2).forEach { rowKinds ->
            Row(modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()) {
                rowKinds.forEach { kind ->
                    SelectPill(
                        label = ConditionText.moonName(kind, RuntimeSettings.resolvedLang),
                        selected = kind in selected,
                        onClick = {
                            selected = if (kind in selected) selected - kind else selected + kind
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                }
                repeat(2 - rowKinds.size) { androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

// ================= 下一个闹钟之前 / 省电 / 静音 / 耳机 =================

@Composable
fun BeforeNextAlarmForm(onConfirm: (UnlockCondition.BeforeNextAlarm) -> Unit) {
    val L = LocalStrings.current
    ExtendFormScaffold(
        title = L.condBeforeAlarm,
        valueCondition = UnlockCondition.BeforeNextAlarm,
        onConfirm = { onConfirm(UnlockCondition.BeforeNextAlarm) },
    ) {}
}

@Composable
fun PowerSaveModeForm(onConfirm: (UnlockCondition.PowerSaveMode) -> Unit) {
    val L = LocalStrings.current
    var active by remember { mutableStateOf(true) }
    val condition = UnlockCondition.PowerSaveMode(active)

    ExtendFormScaffold(title = L.condPowerSave, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(label = L.condStateOn, selected = active, onClick = { active = true }, modifier = Modifier.weight(1f))
            SelectPill(
                label = L.condStateOff,
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
fun SilentModeForm(onConfirm: (UnlockCondition.SilentMode) -> Unit) {
    val L = LocalStrings.current
    var silent by remember { mutableStateOf(true) }
    val condition = UnlockCondition.SilentMode(silent)

    ExtendFormScaffold(title = L.condSilent, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(label = L.condStateOn, selected = silent, onClick = { silent = true }, modifier = Modifier.weight(1f))
            SelectPill(
                label = L.condStateOff,
                selected = !silent,
                onClick = { silent = false },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun HeadphoneConnectedForm(onConfirm: (UnlockCondition.HeadphoneConnected) -> Unit) {
    val L = LocalStrings.current
    var connected by remember { mutableStateOf(true) }
    val condition = UnlockCondition.HeadphoneConnected(connected)

    ExtendFormScaffold(title = L.condHeadphone, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(label = L.condStateOn, selected = connected, onClick = { connected = true }, modifier = Modifier.weight(1f))
            SelectPill(
                label = L.condStateOff,
                selected = !connected,
                onClick = { connected = false },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
    }
}

// ================= 运动状态 / 指南针 / 海拔 =================

@Composable
fun MotionActivityForm(onConfirm: (UnlockCondition.MotionActivity) -> Unit) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf(MotionKind.WALKING)) }

    ExtendFormScaffold(
        title = L.condMotion,
        valueCondition = UnlockCondition.MotionActivity(selected),
        enabled = selected.isNotEmpty(),
        onConfirm = { onConfirm(UnlockCondition.MotionActivity(selected)) },
    ) {
        MotionKind.entries.chunked(2).forEach { rowKinds ->
            Row(modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()) {
                rowKinds.forEach { kind ->
                    SelectPill(
                        label = ConditionText.motionName(kind, RuntimeSettings.resolvedLang),
                        selected = kind in selected,
                        onClick = {
                            selected = if (kind in selected) selected - kind else selected + kind
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                }
                repeat(2 - rowKinds.size) { androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f)) }
            }
        }
        Text(
            text = L.condMotionNote,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun CompassHeadingForm(onConfirm: (UnlockCondition.CompassHeading) -> Unit) {
    val L = LocalStrings.current
    var deg by remember { mutableIntStateOf(0) }
    var tolerance by remember { mutableIntStateOf(30) }
    val condition = UnlockCondition.CompassHeading(deg, tolerance)

    ExtendFormScaffold(title = L.condCompass, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        IntSlider(value = deg, range = 0f..359f, steps = 358) { deg = it }
        IntSlider(value = tolerance, range = 5f..90f, steps = 16) { tolerance = it }
    }
}

@Composable
fun AltitudeRangeForm(onConfirm: (UnlockCondition.AltitudeRange) -> Unit) {
    val L = LocalStrings.current
    var useMin by remember { mutableStateOf(true) }
    var useMax by remember { mutableStateOf(false) }
    var minM by remember { mutableIntStateOf(100) }
    var maxM by remember { mutableIntStateOf(1000) }
    val condition = UnlockCondition.AltitudeRange(
        minM = if (useMin) minM.toDouble() else null,
        maxM = if (useMax) maxM.toDouble() else null,
    )

    ExtendFormScaffold(title = L.condAltitude, valueCondition = condition, enabled = useMin || useMax, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = minM, range = 0f..8000f) { minM = it }
        IntSlider(value = maxM, range = 0f..8850f) { maxM = it }
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
