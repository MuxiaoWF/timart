package com.muxiao.timart.ui.create.rules.condition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
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
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 时间类条件表单（架构 §2.15）：指定日期 / 封存满 N 天 / 指定星期 / 一天时间段。
 * 全部为纯表单；确认回调产出对应 [UnlockCondition] 子类。
 */

// ================= 指定日期 =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedDateForm(
    onConfirm: (UnlockCondition.FixedDate) -> Unit,
) {
    val L = LocalStrings.current
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.tfFixedTitle, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = L.tfFixedDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        DatePicker(
            state = pickerState,
            showModeToggle = false,
            modifier = Modifier.padding(top = 8.dp),
        )
        FormConfirmButton(
            enabled = pickerState.selectedDateMillis != null,
            label = L.tfFixedConfirm,
        ) {
            val millis = pickerState.selectedDateMillis ?: return@FormConfirmButton
            // DatePicker 返回 UTC 零点毫秒，按 UTC 取日历日期避免时区偏移
            val date: LocalDate =
                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            onConfirm(UnlockCondition.FixedDate(targetDate = date))
        }
    }
}

// ================= 封存满 N 天 =================

@Composable
fun MinElapsedDayForm(
    onConfirm: (UnlockCondition.MinElapsedDay) -> Unit,
) {
    val L = LocalStrings.current
    var days by remember { mutableIntStateOf(30) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.tfDaysTitle, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = L.tfDaysDescFmt.format(days),
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        DaySlider(
            value = days.toFloat(),
            onValueChange = { days = it.toInt() },
            range = 1f..365f,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = L.tfDaysValueFmt.format(days),
            style = TimartType.body.copy(color = TimeGold),
            modifier = Modifier
                .padding(top = 8.dp)
                .align(Alignment.CenterHorizontally),
        )
        FormConfirmButton(enabled = true, label = L.confirm) {
            onConfirm(UnlockCondition.MinElapsedDay(days = days))
        }
    }
}

// ================= 指定星期 =================

@Composable
fun WeekDayForm(
    onConfirm: (UnlockCondition.WeekDay) -> Unit,
) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf<DayOfWeek>()) }
    val ordered = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.tfWeekTitle, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = L.tfWeekDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
        ) {
            ordered.forEach { day ->
                SelectPill(
                    label = ConditionText.dayShortName(day, RuntimeSettings.resolvedLang),
                    selected = day in selected,
                    onClick = {
                        selected = if (day in selected) selected - day else selected + day
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        FormConfirmButton(enabled = selected.isNotEmpty(), label = L.confirm) {
            onConfirm(UnlockCondition.WeekDay(weekSet = selected))
        }
    }
}

// ================= 一天时间段 =================

@Composable
fun TimeRangeForm(
    onConfirm: (UnlockCondition.TimeRange) -> Unit,
) {
    val L = LocalStrings.current
    var startHour by remember { mutableIntStateOf(9) }
    var endHour by remember { mutableIntStateOf(18) }
    val valid = startHour != endHour

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = L.tfRangeTitle, style = TimartType.titleSerif, color = InkPrimary)
        Text(
            text = if (valid) {
                if (endHour > startHour) L.tfRangeFmt.format(startHour, endHour) else L.tfRangeCrossFmt.format(startHour, endHour)
            } else {
                L.tfRangeError
            },
            style = TimartType.caption,
            color = if (valid) InkSecondary else InkDisabled,
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(
            text = L.tfFromFmt.format(startHour),
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 18.dp),
        )
        HourSlider(value = startHour, onValueChange = { startHour = it })

        Text(
            text = if (endHour > startHour) L.tfToFmt.format(endHour) else L.tfToCrossFmt.format(endHour),
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )
        HourSlider(value = endHour, onValueChange = { endHour = it })

        FormConfirmButton(enabled = valid, label = L.confirm) {
            onConfirm(UnlockCondition.TimeRange(startHour = startHour, endHour = endHour))
        }
    }
}

// ================= 共用小件 =================

/** 表单确认按钮（金色 CTA，统一样式） */
@Composable
internal fun FormConfirmButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = TimeGold,
            contentColor = DeepCharcoal,
            disabledContainerColor = SurfaceRaise,
            disabledContentColor = InkDisabled,
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Text(text = label, style = TimartType.body)
    }
}

/** 通用选择药丸（表单多选/单选共用） */
@Composable
internal fun SelectPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(
                color = if (selected) TimeGold else DeepCharcoal,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = TimartType.caption,
            color = if (selected) DeepCharcoal else InkSecondary,
            maxLines = 1,
        )
    }
}

/** 天数滑条 */
@Composable
private fun DaySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = TimeGold,
            activeTrackColor = TimeGold,
            inactiveTrackColor = SurfaceRaise,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** 小时滑条（0–23 整数步进） */
@Composable
private fun HourSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = 0f..23f,
        steps = 22,
        colors = SliderDefaults.colors(
            thumbColor = TimeGold,
            activeTrackColor = TimeGold,
            inactiveTrackColor = SurfaceRaise,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
