package com.muxiao.timart.ui.create.rules.condition

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.SolarTermKind
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.ZodiacKind
import com.muxiao.timart.l10n.LocalStrings
import java.time.DayOfWeek

/**
 * 储备池 v4 时间 / 天文条件表单（delta-prd-vs-code.md D-1.2 §1）：
 * 节气 / 满整数纪念日 / 季节 / 满 N 个月 / 每月第 N 个星期几 / 每年浮动节日 / 闰日 /
 * 每月最后一天 / 封存后的第一个星期几 / 黄道十二宫 / 农历整月 / 每月固定多天。
 * 表单只产出条件对象；动态值文案统一走 ConditionText（三语同源）。
 */

/** 多选药丸网格（储备池 v4 各表单共用；columns 控制每列个数） */
@Composable
internal fun <T> SelectionGrid(
    items: List<T>,
    columns: Int = 2,
    selected: (T) -> Boolean,
    label: (T) -> String,
    onToggle: (T) -> Unit,
) {
    items.chunked(columns).forEach { rowItems ->
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth(),
        ) {
            rowItems.forEach { item ->
                SelectPill(
                    label = label(item),
                    selected = selected(item),
                    onClick = { onToggle(item) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
            }
            repeat(columns - rowItems.size) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 星期单选行（第 N 个星期几类表单共用） */
@Composable
internal fun WeekdayPicker(
    selected: DayOfWeek,
    onSelect: (DayOfWeek) -> Unit,
) {
    val ordered = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )
    SelectionGrid(
        items = ordered,
        columns = 4,
        selected = { it == selected },
        label = { ConditionText.dayShortName(it, com.muxiao.timart.utils.RuntimeSettings.resolvedLang) },
        onToggle = onSelect,
    )
}

// ================= 二十四节气 =================

@Composable
fun SolarTermForm(onConfirm: (UnlockCondition.SolarTerm) -> Unit) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf(SolarTermKind.CHUNFEN)) }

    ExtendFormScaffold(
        title = L.condSolarTerm,
        valueCondition = UnlockCondition.SolarTerm(selected),
        enabled = selected.isNotEmpty(),
        onConfirm = { onConfirm(UnlockCondition.SolarTerm(selected)) },
    ) {
        SelectionGrid(
            items = SolarTermKind.entries.toList(),
            columns = 3,
            selected = { it in selected },
            label = { ConditionText.solarTermName(it, com.muxiao.timart.utils.RuntimeSettings.resolvedLang) },
            onToggle = { kind -> selected = if (kind in selected) selected - kind else selected + kind },
        )
    }
}

// ================= 满整数纪念日 =================

@Composable
fun RoundDaysForm(onConfirm: (UnlockCondition.RoundDaysElapsed) -> Unit) {
    val L = LocalStrings.current
    var modulus by remember { mutableIntStateOf(100) }
    val condition = UnlockCondition.RoundDaysElapsed(modulus)

    ExtendFormScaffold(title = L.condRoundDays, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = modulus, range = 10f..1000f, steps = 98) { modulus = it }
    }
}

// ================= 季节 =================

@Composable
fun SeasonForm(onConfirm: (UnlockCondition.Season) -> Unit) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf(com.muxiao.timart.domain.model.unlock.SeasonKind.SPRING)) }

    ExtendFormScaffold(
        title = L.condSeason,
        valueCondition = UnlockCondition.Season(selected),
        enabled = selected.isNotEmpty(),
        onConfirm = { onConfirm(UnlockCondition.Season(selected)) },
    ) {
        SelectionGrid(
            items = com.muxiao.timart.domain.model.unlock.SeasonKind.entries.toList(),
            selected = { it in selected },
            label = { ConditionText.seasonName(it, com.muxiao.timart.utils.RuntimeSettings.resolvedLang) },
            onToggle = { kind -> selected = if (kind in selected) selected - kind else selected + kind },
        )
    }
}

// ================= 封存满 N 个月 =================

@Composable
fun MinElapsedMonthsForm(onConfirm: (UnlockCondition.MinElapsedMonths) -> Unit) {
    val L = LocalStrings.current
    var months by remember { mutableIntStateOf(6) }
    val condition = UnlockCondition.MinElapsedMonths(months)

    ExtendFormScaffold(title = L.condElapsedMonths, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = months, range = 1f..120f) { months = it }
    }
}

// ================= 每月第 N 个星期几 =================

@Composable
fun NthWeekdayOfMonthForm(onConfirm: (UnlockCondition.NthWeekdayOfMonth) -> Unit) {
    val L = LocalStrings.current
    var nth by remember { mutableIntStateOf(1) }
    var weekday by remember { mutableStateOf(DayOfWeek.MONDAY) }
    val condition = UnlockCondition.NthWeekdayOfMonth(nth, weekday)

    ExtendFormScaffold(title = L.condNthWeekdayMonth, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = nth, range = 1f..5f, steps = 3) { nth = it }
        WeekdayPicker(selected = weekday, onSelect = { weekday = it })
    }
}

// ================= 每年浮动节日 =================

@Composable
fun YearlyNthWeekdayForm(onConfirm: (UnlockCondition.YearlyNthWeekday) -> Unit) {
    val L = LocalStrings.current
    var month by remember { mutableIntStateOf(5) }
    var nth by remember { mutableIntStateOf(2) }
    var weekday by remember { mutableStateOf(DayOfWeek.SUNDAY) }
    val condition = UnlockCondition.YearlyNthWeekday(month, nth, weekday)

    ExtendFormScaffold(title = L.condNthWeekdayYear, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = month, range = 1f..12f, steps = 10) { month = it }
        IntSlider(value = nth, range = 1f..5f, steps = 3) { nth = it }
        WeekdayPicker(selected = weekday, onSelect = { weekday = it })
    }
}

// ================= 闰日 / 每月最后一天 =================

@Composable
fun LeapDayForm(onConfirm: (UnlockCondition.LeapDay) -> Unit) {
    val L = LocalStrings.current
    ExtendFormScaffold(
        title = L.condLeapDay,
        valueCondition = UnlockCondition.LeapDay,
        onConfirm = { onConfirm(UnlockCondition.LeapDay) },
    ) {}
}

@Composable
fun LastDayOfMonthForm(onConfirm: (UnlockCondition.LastDayOfMonth) -> Unit) {
    val L = LocalStrings.current
    ExtendFormScaffold(
        title = L.condLastDayMonth,
        valueCondition = UnlockCondition.LastDayOfMonth,
        onConfirm = { onConfirm(UnlockCondition.LastDayOfMonth) },
    ) {}
}

// ================= 封存后的第一个星期几 =================

@Composable
fun NthWeekdaySinceForm(onConfirm: (UnlockCondition.NthWeekdaySince) -> Unit) {
    val L = LocalStrings.current
    var minDays by remember { mutableIntStateOf(30) }
    var weekday by remember { mutableStateOf(DayOfWeek.FRIDAY) }
    val condition = UnlockCondition.NthWeekdaySince(minDays, weekday)

    ExtendFormScaffold(title = L.condNthWeekdaySince, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = minDays, range = 1f..365f) { minDays = it }
        WeekdayPicker(selected = weekday, onSelect = { weekday = it })
    }
}

// ================= 黄道十二宫 =================

@Composable
fun ZodiacSeasonForm(onConfirm: (UnlockCondition.ZodiacSeason) -> Unit) {
    val L = LocalStrings.current
    var zodiac by remember { mutableStateOf(ZodiacKind.SCORPIO) }

    ExtendFormScaffold(
        title = L.condZodiac,
        valueCondition = UnlockCondition.ZodiacSeason(zodiac),
        onConfirm = { onConfirm(UnlockCondition.ZodiacSeason(zodiac)) },
    ) {
        SelectionGrid(
            items = ZodiacKind.entries.toList(),
            columns = 3,
            selected = { it == zodiac },
            label = { ConditionText.zodiacName(it, com.muxiao.timart.utils.RuntimeSettings.resolvedLang) },
            onToggle = { zodiac = it },
        )
    }
}

// ================= 农历整月 =================

@Composable
fun LunarMonthRangeForm(onConfirm: (UnlockCondition.LunarMonthRange) -> Unit) {
    val L = LocalStrings.current
    var month by remember { mutableIntStateOf(1) }
    val condition = UnlockCondition.LunarMonthRange(month)

    ExtendFormScaffold(title = L.condLunarMonth, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = month, range = 1f..12f, steps = 10) { month = it }
    }
}

// ================= 每月固定多天 =================

@Composable
fun MonthlyDaySetForm(onConfirm: (UnlockCondition.MonthlyDaySet) -> Unit) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf(1)) }

    ExtendFormScaffold(
        title = L.condMonthlyDays,
        valueCondition = UnlockCondition.MonthlyDaySet(selected),
        enabled = selected.isNotEmpty(),
        onConfirm = { onConfirm(UnlockCondition.MonthlyDaySet(selected)) },
    ) {
        SelectionGrid(
            items = (1..31).toList(),
            columns = 7,
            selected = { it in selected },
            label = { it.toString() },
            onToggle = { day -> selected = if (day in selected) selected - day else selected + day },
        )
    }
}
