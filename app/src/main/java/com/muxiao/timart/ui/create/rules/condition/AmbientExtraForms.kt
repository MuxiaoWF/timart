package com.muxiao.timart.ui.create.rules.condition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.muxiao.timart.domain.model.City
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.LiftDirection
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.WindDirKind
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.create.CreateViewModel
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.TimartType

/**
 * 储备池 v4 位置 / 天气条件表单（delta-prd-vs-code.md D-1.2 §3）：
 * 白昼长度 / 日出钟点 / 南北半球 / 移动速度区间 / 城市级定位 / 相对海拔 /
 * 空气质量 / 风向 / 大幅降温 / 降水概率。
 */

// ================= 白昼长度 =================

@Composable
fun DayLengthForm(onConfirm: (UnlockCondition.DayLength) -> Unit) {
    val L = LocalStrings.current
    var useMin by remember { mutableStateOf(true) }
    var useMax by remember { mutableStateOf(false) }
    var minH by remember { mutableIntStateOf(10) }
    var maxH by remember { mutableIntStateOf(14) }
    val condition = UnlockCondition.DayLength(
        minHours = if (useMin) minH.toDouble() else null,
        maxHours = if (useMax) maxH.toDouble() else null,
    )

    ExtendFormScaffold(title = L.condDayLength, valueCondition = condition, enabled = useMin || useMax, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = minH, range = 0f..24f, steps = 23) { minH = it }
        IntSlider(value = maxH, range = 0f..24f, steps = 23) { maxH = it }
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
        Text(
            text = L.condGpsNeeded,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ================= 日出钟点 =================

@Composable
fun SunriseRangeForm(onConfirm: (UnlockCondition.SunriseTimeRange) -> Unit) {
    val L = LocalStrings.current
    var useMin by remember { mutableStateOf(true) }
    var useMax by remember { mutableStateOf(false) }
    var minMinute by remember { mutableIntStateOf(360) }
    var maxMinute by remember { mutableIntStateOf(420) }
    val condition = UnlockCondition.SunriseTimeRange(
        minMinute = if (useMin) minMinute else null,
        maxMinute = if (useMax) maxMinute else null,
    )

    ExtendFormScaffold(title = L.condSunriseRange, valueCondition = condition, enabled = useMin || useMax, onConfirm = {
        onConfirm(condition)
    }) {
        Text(
            text = "${ConditionText.formatMinuteOfDay(minMinute)} – ${ConditionText.formatMinuteOfDay(maxMinute)}",
            style = TimartType.body.copy(color = com.muxiao.timart.ui.theme.TimeGold),
            modifier = Modifier.padding(top = 6.dp),
        )
        IntSlider(value = minMinute, range = 0f..1439f, steps = 1438) { minMinute = it }
        IntSlider(value = maxMinute, range = 0f..1439f, steps = 1438) { maxMinute = it }
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
        Text(
            text = L.condGpsNeeded,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ================= 南北半球 =================

@Composable
fun HemisphereForm(onConfirm: (UnlockCondition.Hemisphere) -> Unit) {
    val L = LocalStrings.current
    var north by remember { mutableStateOf(false) }
    val condition = UnlockCondition.Hemisphere(north)

    ExtendFormScaffold(title = L.condHemisphere, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.Hemisphere(true), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = north,
                onClick = { north = true },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.Hemisphere(false), com.muxiao.timart.utils.RuntimeSettings.resolvedLang),
                selected = !north,
                onClick = { north = false },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
        Text(
            text = L.condGpsNeeded,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ================= 移动速度区间 =================

@Composable
fun SpeedRangeForm(onConfirm: (UnlockCondition.SpeedRange) -> Unit) {
    val L = LocalStrings.current
    var useMin by remember { mutableStateOf(true) }
    var useMax by remember { mutableStateOf(false) }
    var minKmh by remember { mutableIntStateOf(5) }
    var maxKmh by remember { mutableIntStateOf(60) }
    val condition = UnlockCondition.SpeedRange(
        minKmh = if (useMin) minKmh else null,
        maxKmh = if (useMax) maxKmh else null,
    )

    ExtendFormScaffold(title = L.condSpeedRange, valueCondition = condition, enabled = useMin || useMax, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = minKmh, range = 0f..200f) { minKmh = it }
        IntSlider(value = maxKmh, range = 0f..200f) { maxKmh = it }
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
        Text(
            text = L.condGpsNeeded,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ================= 城市级定位 =================

@Composable
fun CityLocationForm(
    vm: CreateViewModel,
    onConfirm: (UnlockCondition) -> Unit,
) {
    val L = LocalStrings.current
    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var picked by remember { mutableStateOf<City?>(null) }
    var radius by remember { mutableIntStateOf(30) }

    val condition: UnlockCondition? = picked?.let {
        UnlockCondition.CityLocation(
            cityName = it.name,
            lat = it.lat,
            lng = it.lng,
            radiusMeter = radius * 1000,
        )
    }

    ExtendFormScaffold(
        title = L.condCityLocation,
        valueCondition = condition,
        enabled = picked != null,
        onConfirm = { condition?.let(onConfirm) },
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = keyword,
            onValueChange = {
                keyword = it
                results = vm.searchCities(it).take(8)
            },
            singleLine = true,
            textStyle = TimartType.body.copy(color = com.muxiao.timart.ui.theme.InkPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(com.muxiao.timart.ui.theme.TimeGold),
            decorationBox = { inner ->
                androidx.compose.foundation.layout.Box {
                    if (keyword.isEmpty()) {
                        Text(text = L.citySearchHint, style = TimartType.caption, color = InkSecondary)
                    }
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .background(com.muxiao.timart.ui.theme.SurfaceRaise)
                .padding(12.dp),
        )
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(top = 8.dp)) {
            results.forEach { city ->
                SelectPill(
                    label = "${city.name} · ${city.province}",
                    selected = city.id == picked?.id,
                    onClick = { picked = city },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
        if (picked != null) {
            Text(
                text = L.gfRadiusFmt.format(radius),
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
            IntSlider(value = radius, range = 5f..100f) { radius = it }
        }
        Text(
            text = L.cityLocNote,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ================= 相对海拔 =================

@Composable
fun RelativeAltitudeForm(
    vm: CreateViewModel,
    onConfirm: (UnlockCondition.RelativeAltitude) -> Unit,
) {
    val L = LocalStrings.current
    // 封存时刻的气压计海拔即「基准海拔」（CompassAltitudeReader 常驻监听的最新快照）
    var direction by remember { mutableStateOf(LiftDirection.UP) }
    var meters by remember { mutableIntStateOf(30) }
    var baseAltM by remember { mutableStateOf<Double?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        baseAltM = vm.currentAltitudeMeters()
    }

    val condition: UnlockCondition.RelativeAltitude? = baseAltM?.let {
        UnlockCondition.RelativeAltitude(
            baseAltM = it,
            deltaM = meters.toDouble(),
            direction = direction,
        )
    }

    ExtendFormScaffold(
        title = L.condRelAltitude,
        valueCondition = condition,
        enabled = condition != null,
        onConfirm = { condition?.let(onConfirm) },
    ) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(
                    UnlockCondition.RelativeAltitude(0.0, meters.toDouble(), LiftDirection.UP),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = direction == LiftDirection.UP,
                onClick = { direction = LiftDirection.UP },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = ConditionText.conditionSentence(
                    UnlockCondition.RelativeAltitude(0.0, meters.toDouble(), LiftDirection.DOWN),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = direction == LiftDirection.DOWN,
                onClick = { direction = LiftDirection.DOWN },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
        IntSlider(value = meters, range = 1f..500f) { meters = it }
        if (baseAltM == null) {
            Text(
                text = L.condDeviceUnsupported,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ================= 空气质量 =================

@Composable
fun AirQualityForm(onConfirm: (UnlockCondition.AirQuality) -> Unit) {
    val L = LocalStrings.current
    var aqi by remember { mutableIntStateOf(100) }
    val condition = UnlockCondition.AirQuality(aqi)

    ExtendFormScaffold(title = L.condAirQuality, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = aqi, range = 10f..300f) { aqi = it }
    }
}

// ================= 风向 =================

@Composable
fun WindDirectionForm(onConfirm: (UnlockCondition.WindDirection) -> Unit) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf(WindDirKind.E)) }

    ExtendFormScaffold(
        title = L.condWindDir,
        valueCondition = UnlockCondition.WindDirection(selected),
        enabled = selected.isNotEmpty(),
        onConfirm = { onConfirm(UnlockCondition.WindDirection(selected)) },
    ) {
        SelectionGrid(
            items = WindDirKind.entries.toList(),
            columns = 4,
            selected = { it in selected },
            label = { ConditionText.windDirName(it, com.muxiao.timart.utils.RuntimeSettings.resolvedLang) },
            onToggle = { kind -> selected = if (kind in selected) selected - kind else selected + kind },
        )
    }
}

// ================= 大幅降温 =================

@Composable
fun TempDeltaForm(onConfirm: (UnlockCondition.TempDelta) -> Unit) {
    val L = LocalStrings.current
    var drop by remember { mutableIntStateOf(5) }
    val condition = UnlockCondition.TempDelta(drop.toDouble())

    ExtendFormScaffold(title = L.condTempDelta, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = drop, range = 1f..20f, steps = 18) { drop = it }
    }
}

// ================= 降水概率 =================

@Composable
fun PrecipitationProbabilityForm(onConfirm: (UnlockCondition.PrecipitationProbability) -> Unit) {
    val L = LocalStrings.current
    var prob by remember { mutableIntStateOf(60) }
    val condition = UnlockCondition.PrecipitationProbability(prob)

    ExtendFormScaffold(title = L.condPrecipProb, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = prob, range = 10f..100f, steps = 89) { prob = it }
    }
}

// ================= 今冬首雪（储备池 v5）=================

/**
 * 降雪观测表单：二选一语义（普通降雪 / 今冬首雪）。
 * 首雪 = 今日降雪且此前历史窗口（92 天）无雪记录，判定走天气链路与 30min 缓存。
 */
@Composable
fun SnowObservationForm(onConfirm: (UnlockCondition.SnowObservation) -> Unit) {
    val L = LocalStrings.current
    var firstOfSeason by remember { mutableStateOf(false) }

    ExtendFormScaffold(
        title = L.condSnowfall,
        valueCondition = UnlockCondition.SnowObservation(firstOfSeason),
        onConfirm = { onConfirm(UnlockCondition.SnowObservation(firstOfSeason)) },
    ) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(
                    UnlockCondition.SnowObservation(false),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = !firstOfSeason,
                onClick = { firstOfSeason = false },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = ConditionText.conditionSentence(
                    UnlockCondition.SnowObservation(true),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = firstOfSeason,
                onClick = { firstOfSeason = true },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
    }
}

// ================= 连续降雨 / 雨后初晴（储备池 v6）=================

/**
 * 连续降雨观测表单：天数滑条（2–30 天，1 天等价普通天气条件）+ 二选一语义
 * （连续降雨 / 雨后初晴）。判定走天气链路 daily 历史窗口与 30min 缓存。
 */
@Composable
fun RainStreakForm(onConfirm: (UnlockCondition.RainStreak) -> Unit) {
    val L = LocalStrings.current
    var days by remember { mutableIntStateOf(3) }
    var afterRain by remember { mutableStateOf(false) }

    ExtendFormScaffold(
        title = L.condRainStreak,
        valueCondition = UnlockCondition.RainStreak(days, afterRain),
        onConfirm = { onConfirm(UnlockCondition.RainStreak(days, afterRain)) },
    ) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(
                    UnlockCondition.RainStreak(days, false),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = !afterRain,
                onClick = { afterRain = false },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = ConditionText.conditionSentence(
                    UnlockCondition.RainStreak(days, true),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = afterRain,
                onClick = { afterRain = true },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
        Text(
            text = ConditionText.conditionSentence(
                UnlockCondition.RainStreak(days, afterRain),
                com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
            ),
            style = TimartType.body.copy(color = com.muxiao.timart.ui.theme.TimeGold),
            modifier = Modifier
                .padding(top = 14.dp)
                .align(Alignment.CenterHorizontally),
        )
        IntSlider(value = days, range = 2f..30f, steps = 28) { days = it }
    }
}

// ================= 封存日温差（储备池 v6）=================

/**
 * 封存日温差表单：更冷/更热二选一 + 温差滑条（1–20 °C，步进 0.5）。
 * 封存温度取创建时的天气快照（Capsule.weather），未选天气城市的胶囊判定时显式报「封存时未记录天气」。
 */
@Composable
fun TempVsSealForm(onConfirm: (UnlockCondition.TempVsSealDay) -> Unit) {
    val L = LocalStrings.current
    var deltaInt by remember { mutableIntStateOf(10) } // 0.5°C 步进，10 = 5°C
    var hotter by remember { mutableStateOf(false) }
    val deltaC = deltaInt / 2.0

    ExtendFormScaffold(
        title = L.condTempVsSeal,
        valueCondition = UnlockCondition.TempVsSealDay(deltaC, hotter),
        onConfirm = { onConfirm(UnlockCondition.TempVsSealDay(deltaC, hotter)) },
    ) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(
                    UnlockCondition.TempVsSealDay(deltaC, false),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = !hotter,
                onClick = { hotter = false },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = ConditionText.conditionSentence(
                    UnlockCondition.TempVsSealDay(deltaC, true),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = hotter,
                onClick = { hotter = true },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
        Text(
            text = ConditionText.conditionSentence(
                UnlockCondition.TempVsSealDay(deltaC, hotter),
                com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
            ),
            style = TimartType.body.copy(color = com.muxiao.timart.ui.theme.TimeGold),
            modifier = Modifier
                .padding(top = 14.dp)
                .align(Alignment.CenterHorizontally),
        )
        IntSlider(value = deltaInt, range = 2f..40f, steps = 38) { deltaInt = it }
    }
}

// ================= 今季首雷（储备池 v7）=================

/**
 * 雷暴观测表单：二选一语义（普通雷暴 / 今季首雷）。
 * 首雷 = 今日雷暴且此前历史窗口（92 天）无雷记录，判定走天气链路与 30min 缓存。
 */
@Composable
fun ThunderObservationForm(onConfirm: (UnlockCondition.ThunderObservation) -> Unit) {
    val L = LocalStrings.current
    var firstOfSeason by remember { mutableStateOf(false) }

    ExtendFormScaffold(
        title = L.condThunder,
        valueCondition = UnlockCondition.ThunderObservation(firstOfSeason),
        onConfirm = { onConfirm(UnlockCondition.ThunderObservation(firstOfSeason)) },
    ) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(
                    UnlockCondition.ThunderObservation(false),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = !firstOfSeason,
                onClick = { firstOfSeason = false },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = ConditionText.conditionSentence(
                    UnlockCondition.ThunderObservation(true),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = firstOfSeason,
                onClick = { firstOfSeason = true },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
    }
}

// ================= 气压骤降（储备池 v7）=================

/**
 * 气压骤降表单：较昨日均压下降 ≥ N hPa（0.5 步进）。当前气压与昨日基线
 * 均走天气链路（daily pressure_msl_mean），基线缺失按「指标不可用」fail-closed。
 */
@Composable
fun PressureDeltaForm(onConfirm: (UnlockCondition.PressureDelta) -> Unit) {
    val L = LocalStrings.current
    var deltaInt by remember { mutableIntStateOf(10) } // 0.5 hPa 步进，10 = 5 hPa
    val deltaHpa = deltaInt / 2.0
    val condition = UnlockCondition.PressureDelta(deltaHpa)

    ExtendFormScaffold(
        title = L.condPressureDrop,
        valueCondition = condition,
        onConfirm = { onConfirm(condition) },
    ) {
        Text(
            text = ConditionText.conditionSentence(
                UnlockCondition.PressureDelta(deltaHpa),
                com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
            ),
            style = TimartType.body.copy(color = com.muxiao.timart.ui.theme.TimeGold),
            modifier = Modifier
                .padding(top = 14.dp)
                .align(Alignment.CenterHorizontally),
        )
        IntSlider(value = deltaInt, range = 2f..60f, steps = 58) { deltaInt = it }
    }
}
