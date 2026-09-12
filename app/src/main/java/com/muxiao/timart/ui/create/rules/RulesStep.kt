package com.muxiao.timart.ui.create.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.ui.create.CreateViewModel
import com.muxiao.timart.utils.location.GeocodeResolver
import com.muxiao.timart.ui.create.rules.condition.BatteryLevelForm
import com.muxiao.timart.ui.create.rules.condition.BiometricForm
import com.muxiao.timart.ui.create.rules.condition.AltitudeRangeForm
import com.muxiao.timart.ui.create.rules.condition.AirplaneModeForm
import com.muxiao.timart.ui.create.rules.condition.AmbientLightForm
import com.muxiao.timart.ui.create.rules.condition.BeforeNextAlarmForm
import com.muxiao.timart.ui.create.rules.condition.CapsuleCountForm
import com.muxiao.timart.ui.create.rules.condition.ChargingStateForm
import com.muxiao.timart.ui.create.rules.condition.CompassHeadingForm
import com.muxiao.timart.ui.create.rules.condition.DaysSinceLastOpenForm
import com.muxiao.timart.ui.create.rules.condition.FixedDateForm
import com.muxiao.timart.ui.create.rules.condition.FixedDateTimeForm
import com.muxiao.timart.ui.create.rules.condition.FlipOrHoldForm
import com.muxiao.timart.ui.create.rules.condition.GoldenHourForm
import com.muxiao.timart.ui.create.rules.condition.HoldPressForm
import com.muxiao.timart.ui.create.rules.condition.GpsConditionForm
import com.muxiao.timart.ui.create.rules.condition.HeadphoneConnectedForm
import com.muxiao.timart.ui.create.rules.condition.LunarDateForm
import com.muxiao.timart.ui.create.rules.condition.MeteorShowerForm
import com.muxiao.timart.ui.create.rules.condition.MinElapsedDayForm
import com.muxiao.timart.ui.create.rules.condition.MinElapsedMinutesForm
import com.muxiao.timart.ui.create.rules.condition.MoonPhaseForm
import com.muxiao.timart.ui.create.rules.condition.MotionActivityForm
import com.muxiao.timart.ui.create.rules.condition.MonthlyDayForm
import com.muxiao.timart.ui.create.rules.condition.MusicPlayingForm
import com.muxiao.timart.ui.create.rules.condition.MovingSpeedForm
import com.muxiao.timart.ui.create.rules.condition.NetworkTypeForm
import com.muxiao.timart.ui.create.rules.condition.NfcTapForm
import com.muxiao.timart.ui.create.rules.condition.OpenCountForm
import com.muxiao.timart.ui.create.rules.condition.OpenStreakForm
import com.muxiao.timart.ui.create.rules.condition.OtherCapsuleStateForm
import com.muxiao.timart.ui.create.rules.condition.PowerSaveModeForm
import com.muxiao.timart.ui.create.rules.condition.PuzzleAnswerForm
import com.muxiao.timart.ui.create.rules.condition.QuestionAnswerForm
import com.muxiao.timart.ui.create.rules.condition.ShakeCountForm
import com.muxiao.timart.ui.create.rules.condition.SilentModeForm
import com.muxiao.timart.ui.create.rules.condition.SsidMatchForm
import com.muxiao.timart.ui.create.rules.condition.StepCountForm
import com.muxiao.timart.ui.create.rules.condition.StepStreakForm
import com.muxiao.timart.ui.create.rules.condition.SunPhaseForm
import com.muxiao.timart.ui.create.rules.condition.TemperatureThresholdForm
import com.muxiao.timart.ui.create.rules.condition.TimeRangeForm
import com.muxiao.timart.ui.create.rules.condition.TimezoneAwayForm
import com.muxiao.timart.ui.create.rules.condition.ViewCountForm
import com.muxiao.timart.ui.create.rules.condition.PhotoKeepsakeForm
import com.muxiao.timart.ui.create.rules.condition.WeatherMetricForm
import com.muxiao.timart.ui.create.rules.condition.WeatherTypeForm
import com.muxiao.timart.ui.create.rules.condition.WeekDayForm
import com.muxiao.timart.ui.create.rules.condition.YearlyDateForm
import com.muxiao.timart.ui.components.visual.SectionHeader
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType

/**
 * 第二步「开启方式」（架构 §2.15，对齐设计稿 03）：
 * 渐进披露——初始仅「什么时候可以打开？」一个入口；加首条后出现「再加一个现实条件」；
 * 52 种条件表单全实现；AND/OR 切换；依赖另一颗胶囊（走 DependencyGraphUseCase 校验）。
 */
@Composable
fun RulesStep(
    vm: CreateViewModel,
    geocodeResolver: GeocodeResolver,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val L = LocalStrings.current
    var showTypeSheet by remember { mutableStateOf(false) }
    var activeForm by remember { mutableStateOf<ConditionType?>(null) }
    var showDependencySheet by remember { mutableStateOf(false) }
    val ruleEmpty = vm.conditions.isEmpty() && vm.dependCapsuleId == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        // 步内返回（回到写下）
        TextButton(
            onClick = onBack,
            colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
            modifier = Modifier.padding(start = 0.dp),
        ) {
            Text(text = L.prevStep, style = TimartType.caption)
        }

        SectionHeader(title = L.rulesTitle, note = "CREATE 02")

        Text(
            text = L.rulesNarrative,
            style = TimartType.titleSerif,
            color = InkPrimary,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = L.rulesSub,
            style = TimartType.body,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )

        // 条件卡列表
        if (vm.conditions.isNotEmpty()) {
            ConditionSectionTitle(
                conditionsCount = vm.conditions.size,
                modifier = Modifier.padding(top = 24.dp),
            )
            ConditionCardList(
                conditions = vm.conditions,
                logic = vm.logic,
                onRemove = vm::removeCondition,
                modifier = Modifier.padding(top = 10.dp),
            )
            // AND/OR 只在两条及以上时有意义
            if (vm.conditions.size >= 2) {
                LogicSwitch(
                    logic = vm.logic,
                    onLogicChange = vm::updateLogic,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            // 渐进披露：加首条后出现「再加一个现实条件」
            if (vm.conditions.size < CreateViewModel.CONDITION_MAX) {
                AddConditionEntry(
                    primary = false,
                    onClick = { showTypeSheet = true },
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        } else {
            // 初始：仅一个主入口
            AddConditionEntry(
                primary = true,
                onClick = { showTypeSheet = true },
                modifier = Modifier.padding(top = 22.dp),
            )
        }

        // 依赖另一颗胶囊
        DependencyEntry(
            dependTitle = vm.dependTitle,
            onClick = { showDependencySheet = true },
            modifier = Modifier.padding(top = 18.dp),
        )

        if (ruleEmpty) {
            Text(
                text = L.rulesNoCondition,
                style = TimartType.caption,
                color = InkDisabled,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // CTA
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(
                containerColor = TimeGold,
                contentColor = DeepCharcoal,
            ),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .padding(top = 28.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(text = L.nextToSeal, style = TimartType.body)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    // ---- 条件类型选择面板 ----
    if (showTypeSheet) {
        ConditionTypeSheet(
            onPick = { type ->
                showTypeSheet = false
                activeForm = type
            },
            onDismiss = { showTypeSheet = false },
        )
    }

    // ---- 具体条件表单面板 ----
    activeForm?.let { type ->
        ConditionFormSheet(
            type = type,
            vm = vm,
            snapshotCityName = vm.snapshot?.cityName,
            geocodeResolver = geocodeResolver,
            onConfirm = { condition ->
                vm.addCondition(condition)
                activeForm = null
            },
            onDismiss = { activeForm = null },
        )
    }

    // ---- 依赖选择面板 ----
    if (showDependencySheet) {
        DependencyPickerSheet(
            vm = vm,
            onDismiss = { showDependencySheet = false },
        )
    }
}

// ================= 入口小件 =================

/** 添加条件入口：初始为大卡（primary），之后为细边框次级入口 */
@Composable
private fun AddConditionEntry(
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (primary) TimeGold.copy(alpha = 0.1f) else SurfaceRaise,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (primary) 20.dp else 13.dp),
    ) {
        Text(
            text = "＋",
            style = if (primary) TimartType.titleSerif else TimartType.body,
            color = TimeGold,
        )
        Text(
            text = if (primary) L.rulesNarrative else L.rulesAddMore,
            style = if (primary) TimartType.titleSerif else TimartType.body,
            color = if (primary) TimeGold else InkPrimary,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** 依赖入口行 */
@Composable
private fun DependencyEntry(
    dependTitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceRaise, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = L.dependRowTitle, style = TimartType.body, color = InkPrimary)
            Text(
                text = L.dependRowDesc,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (dependTitle != null) {
            Text(
                text = "《$dependTitle》",
                style = TimartType.caption,
                color = TimeGold,
                maxLines = 1,
            )
        } else {
            Text(text = L.unset, style = TimartType.caption, color = InkDisabled)
        }
        Text(
            text = "›",
            style = TimartType.titleSerif,
            color = InkDisabled,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

// ================= 条件类型 =================

/** 52 种条件类型（时间 / 设备 / 网络&环境 / 应用内 / 现场挑战） */
internal enum class ConditionType {
    FIXED_DATE,
    MIN_ELAPSED_DAY,
    WEEK_DAY,
    TIME_RANGE,
    FIXED_DATE_TIME,
    MIN_ELAPSED_MINUTES,
    MONTHLY_DAY,
    YEARLY_DATE,
    LUNAR_DATE,
    BATTERY,
    CHARGING,
    STEP,
    STEP_STREAK,
    NETWORK,
    WIFI_SSID,
    GPS,
    AWAY_FROM,
    WEATHER,
    TEMPERATURE,
    SUN_PHASE,
    MOON_PHASE,
    METEOR_SHOWER,
    GOLDEN_HOUR,
    AMBIENT_LIGHT,
    TIMEZONE_AWAY,
    MOVING_SPEED,
    WEATHER_METRIC,
    BEFORE_ALARM,
    POWER_SAVE,
    SILENT,
    HEADPHONE,
    AIRPLANE,
    MUSIC,
    MOTION,
    COMPASS,
    ALTITUDE,
    OPEN_COUNT,
    OPEN_STREAK,
    LAST_OPEN,
    CAPSULE_COUNT,
    OTHER_UNLOCKED,
    OTHER_DESTROYED,
    OTHER_READ,
    VIEW_COUNT,
    QUESTION,
    PUZZLE,
    SHAKE,
    FLIP_HOLD,
    HOLD_PRESS,
    BIOMETRIC,
    PHOTO_KEEPSAKE,
    NFC,
}

/** 条件类型显示名（随界面语言） */
private fun ConditionType.label(L: com.muxiao.timart.l10n.Strings): String = when (this) {
    ConditionType.FIXED_DATE -> L.condFixedDate
    ConditionType.MIN_ELAPSED_DAY -> L.condMinDays
    ConditionType.WEEK_DAY -> L.condWeekDay
    ConditionType.TIME_RANGE -> L.condTimeRange
    ConditionType.BATTERY -> L.condBattery
    ConditionType.CHARGING -> L.condCharging
    ConditionType.STEP -> L.condStep
    ConditionType.STEP_STREAK -> L.condStreak
    ConditionType.NETWORK -> L.condNetwork
    ConditionType.WIFI_SSID -> L.condSsid
    ConditionType.GPS -> L.condGps
    ConditionType.WEATHER -> L.condWeather
    ConditionType.TEMPERATURE -> L.condTemp
    ConditionType.FIXED_DATE_TIME -> L.condExactTime
    ConditionType.MIN_ELAPSED_MINUTES -> L.condMinMinutes
    ConditionType.MONTHLY_DAY -> L.condMonthlyDay
    ConditionType.YEARLY_DATE -> L.condYearly
    ConditionType.LUNAR_DATE -> L.condLunarDate
    ConditionType.AWAY_FROM -> L.condAwayFrom
    ConditionType.SUN_PHASE -> L.condSunPhase
    ConditionType.MOON_PHASE -> L.condMoonPhase
    ConditionType.METEOR_SHOWER -> L.condMeteorShower
    ConditionType.GOLDEN_HOUR -> L.condGoldenHour
    ConditionType.AMBIENT_LIGHT -> L.condAmbientLight
    ConditionType.TIMEZONE_AWAY -> L.condTimezoneAway
    ConditionType.MOVING_SPEED -> L.condMoving
    ConditionType.WEATHER_METRIC -> L.condWeatherMetric
    ConditionType.BEFORE_ALARM -> L.condBeforeAlarm
    ConditionType.POWER_SAVE -> L.condPowerSave
    ConditionType.SILENT -> L.condSilent
    ConditionType.HEADPHONE -> L.condHeadphone
    ConditionType.AIRPLANE -> L.condAirplane
    ConditionType.MUSIC -> L.condMusic
    ConditionType.MOTION -> L.condMotion
    ConditionType.COMPASS -> L.condCompass
    ConditionType.ALTITUDE -> L.condAltitude
    ConditionType.OPEN_COUNT -> L.condOpenCount
    ConditionType.OPEN_STREAK -> L.condOpenStreak
    ConditionType.LAST_OPEN -> L.condLastOpen
    ConditionType.CAPSULE_COUNT -> L.condCapsuleCount
    ConditionType.OTHER_UNLOCKED -> L.condOtherUnlocked
    ConditionType.OTHER_DESTROYED -> L.condOtherDestroyed
    ConditionType.OTHER_READ -> L.condOtherRead
    ConditionType.VIEW_COUNT -> L.condViewCount
    ConditionType.QUESTION -> L.condQuestion
    ConditionType.PUZZLE -> L.condPuzzle
    ConditionType.SHAKE -> L.condShake
    ConditionType.FLIP_HOLD -> L.condFlipHold
    ConditionType.HOLD_PRESS -> L.condHoldPress
    ConditionType.BIOMETRIC -> L.condBiometric
    ConditionType.PHOTO_KEEPSAKE -> L.condPhotoKeepsake
    ConditionType.NFC -> L.condNfc
}

/** 条件分组名（随界面语言） */
private fun ConditionType.group(L: com.muxiao.timart.l10n.Strings): String = when (this) {
    ConditionType.FIXED_DATE,
    ConditionType.MIN_ELAPSED_DAY,
    ConditionType.WEEK_DAY,
    ConditionType.TIME_RANGE,
    ConditionType.FIXED_DATE_TIME,
    ConditionType.MIN_ELAPSED_MINUTES,
    ConditionType.MONTHLY_DAY,
    ConditionType.YEARLY_DATE,
    ConditionType.LUNAR_DATE,
    -> L.catTime

    ConditionType.BATTERY,
    ConditionType.CHARGING,
    ConditionType.STEP,
    ConditionType.STEP_STREAK,
    ConditionType.BEFORE_ALARM,
    ConditionType.POWER_SAVE,
    ConditionType.SILENT,
    ConditionType.HEADPHONE,
    ConditionType.AIRPLANE,
    ConditionType.MUSIC,
    ConditionType.MOTION,
    ConditionType.COMPASS,
    ConditionType.ALTITUDE,
    -> L.catDevice

    ConditionType.NETWORK,
    ConditionType.WIFI_SSID,
    ConditionType.GPS,
    ConditionType.AWAY_FROM,
    ConditionType.WEATHER,
    ConditionType.TEMPERATURE,
    ConditionType.SUN_PHASE,
    ConditionType.MOON_PHASE,
    ConditionType.METEOR_SHOWER,
    ConditionType.GOLDEN_HOUR,
    ConditionType.AMBIENT_LIGHT,
    ConditionType.TIMEZONE_AWAY,
    ConditionType.MOVING_SPEED,
    ConditionType.WEATHER_METRIC,
    -> L.catNet

    ConditionType.OPEN_COUNT,
    ConditionType.OPEN_STREAK,
    ConditionType.LAST_OPEN,
    ConditionType.CAPSULE_COUNT,
    ConditionType.OTHER_UNLOCKED,
    ConditionType.OTHER_DESTROYED,
    ConditionType.OTHER_READ,
    ConditionType.VIEW_COUNT,
    -> L.catUsage

    ConditionType.QUESTION,
    ConditionType.PUZZLE,
    ConditionType.SHAKE,
    ConditionType.FLIP_HOLD,
    ConditionType.HOLD_PRESS,
    ConditionType.BIOMETRIC,
    ConditionType.PHOTO_KEEPSAKE,
    ConditionType.NFC,
    -> L.catChallenge
}

/** 条件类型选择面板：分组 + 双列网格。
 *  设备能力门控：依赖本机缺失硬件的条件禁用（附短注），不在封存后才于判定侧报「不可用」。
 *  [excluded] 强制不展示的类型（后悔药编辑场景排除依赖类条件）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConditionTypeSheet(
    onPick: (ConditionType) -> Unit,
    onDismiss: () -> Unit,
    excluded: Set<ConditionType> = emptySet(),
) {
    val L = LocalStrings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // 硬件探测统一走 DeviceConditionSupport（备份提示共用同一事实源），此处仅做 类型→硬件 映射
    val unavailable = remember(context) {
        val missing = com.muxiao.timart.utils.device.missingDeviceHardware(context)
        buildSet {
            if (com.muxiao.timart.utils.device.DeviceHardware.PRESSURE in missing) add(ConditionType.ALTITUDE)
            if (com.muxiao.timart.utils.device.DeviceHardware.ROTATION_VECTOR in missing) add(ConditionType.COMPASS)
            if (com.muxiao.timart.utils.device.DeviceHardware.STEP_COUNTER in missing) {
                add(ConditionType.STEP)
                add(ConditionType.STEP_STREAK)
            }
            if (com.muxiao.timart.utils.device.DeviceHardware.STEP_DETECTOR in missing) add(ConditionType.MOTION)
            if (com.muxiao.timart.utils.device.DeviceHardware.ACCELEROMETER in missing) {
                add(ConditionType.SHAKE)
                add(ConditionType.FLIP_HOLD)
            }
            if (com.muxiao.timart.utils.device.DeviceHardware.NFC in missing) add(ConditionType.NFC)
            if (com.muxiao.timart.utils.device.DeviceHardware.LIGHT_SENSOR in missing) add(ConditionType.AMBIENT_LIGHT)
            if (com.muxiao.timart.utils.device.DeviceHardware.CAMERA in missing) add(ConditionType.PHOTO_KEEPSAKE)
            if (com.muxiao.timart.utils.device.DeviceHardware.BIOMETRIC in missing) add(ConditionType.BIOMETRIC)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        // 直接全屏展开：默认两段式（半展开 → 手势扩展占满屏 → 嵌套滚动让渡给内层）在占满屏
        // 瞬间有一帧手势交接，表现为上滑滚动顿一下；跳过半展开后滚动全程由内层接管
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
            // 52 种条件 × 5 组远超 sheet 最大高度：必须可滚动，否则底部组（现场挑战等）触不到
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(text = L.condPickerTitle, style = TimartType.titleSerif, color = InkPrimary)
            ConditionType.entries
                .filter { it !in excluded }
                .groupBy { it.group(L) }
                .forEach { (group, types) ->
                Text(
                    text = group,
                    style = TimartType.caption,
                    color = InkDisabled,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                )
                types.chunked(2).forEach { rowTypes ->
                    Row(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        rowTypes.forEach { type ->
                            val disabled = type in unavailable
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(DeepCharcoal, RoundedCornerShape(12.dp))
                                    .then(if (disabled) Modifier else Modifier.clickable { onPick(type) })
                                    .padding(vertical = 14.dp),
                            ) {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = type.label(L),
                                        style = TimartType.body,
                                        color = if (disabled) InkDisabled else InkPrimary,
                                    )
                                    if (disabled) {
                                        Text(
                                            text = L.condDeviceUnsupported,
                                            style = TimartType.caption,
                                            color = InkDisabled,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                        repeat(2 - rowTypes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 按类型挂载对应条件表单。[vm] 仅依赖类条件（OTHER_*）需要；后悔药编辑场景排除该类，传 null */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConditionFormSheet(
    type: ConditionType,
    vm: CreateViewModel?,
    snapshotCityName: String?,
    geocodeResolver: GeocodeResolver,
    onConfirm: (UnlockCondition) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        // 与条件选择面板同口径：跳过半展开，避免占满屏瞬间的嵌套滚动交接卡顿
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            when (type) {
                ConditionType.FIXED_DATE -> FixedDateForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.MIN_ELAPSED_DAY -> MinElapsedDayForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.WEEK_DAY -> WeekDayForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.TIME_RANGE -> TimeRangeForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.BATTERY -> BatteryLevelForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.CHARGING -> ChargingStateForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.STEP -> StepCountForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.NETWORK -> NetworkTypeForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.GPS -> GpsConditionForm(
                    geocodeResolver = geocodeResolver,
                    onConfirm = onConfirm,
                )

                ConditionType.WEATHER -> WeatherTypeForm(
                    snapshotCityName = snapshotCityName,
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.TEMPERATURE -> TemperatureThresholdForm(
                    snapshotCityName = snapshotCityName,
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.WIFI_SSID -> SsidMatchForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.STEP_STREAK -> StepStreakForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.FIXED_DATE_TIME -> FixedDateTimeForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.MIN_ELAPSED_MINUTES -> MinElapsedMinutesForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.MONTHLY_DAY -> MonthlyDayForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.YEARLY_DATE -> YearlyDateForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.AWAY_FROM -> GpsConditionForm(
                    geocodeResolver = geocodeResolver,
                    onConfirm = onConfirm,
                    away = true,
                )

                ConditionType.SUN_PHASE -> SunPhaseForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.MOON_PHASE -> MoonPhaseForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.METEOR_SHOWER -> MeteorShowerForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.GOLDEN_HOUR -> GoldenHourForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.LUNAR_DATE -> LunarDateForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.AMBIENT_LIGHT -> AmbientLightForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.TIMEZONE_AWAY -> TimezoneAwayForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.MOVING_SPEED -> MovingSpeedForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.WEATHER_METRIC -> WeatherMetricForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.BEFORE_ALARM -> BeforeNextAlarmForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.POWER_SAVE -> PowerSaveModeForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.SILENT -> SilentModeForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.HEADPHONE -> HeadphoneConnectedForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.AIRPLANE -> AirplaneModeForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.MUSIC -> MusicPlayingForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.MOTION -> MotionActivityForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.COMPASS -> CompassHeadingForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.ALTITUDE -> AltitudeRangeForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.OPEN_COUNT -> OpenCountForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.OPEN_STREAK -> OpenStreakForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.LAST_OPEN -> DaysSinceLastOpenForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.CAPSULE_COUNT -> CapsuleCountForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.OTHER_UNLOCKED -> vm?.let {
                    OtherCapsuleStateForm(vm = it, onConfirm = onConfirm)
                }

                ConditionType.OTHER_DESTROYED -> vm?.let {
                    OtherCapsuleStateForm(vm = it, onConfirm = onConfirm)
                }

                ConditionType.OTHER_READ -> vm?.let {
                    OtherCapsuleStateForm(vm = it, onConfirm = onConfirm)
                }

                ConditionType.VIEW_COUNT -> ViewCountForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.QUESTION -> QuestionAnswerForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.PUZZLE -> PuzzleAnswerForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.SHAKE -> ShakeCountForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.FLIP_HOLD -> FlipOrHoldForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.HOLD_PRESS -> HoldPressForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.BIOMETRIC -> BiometricForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.PHOTO_KEEPSAKE -> PhotoKeepsakeForm(
                    onConfirm = { onConfirm(it) },
                )

                ConditionType.NFC -> NfcTapForm(
                    onConfirm = { onConfirm(it) },
                )
            }
        }
    }
}
