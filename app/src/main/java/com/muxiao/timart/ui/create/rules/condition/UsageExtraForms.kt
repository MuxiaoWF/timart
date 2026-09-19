package com.muxiao.timart.ui.create.rules.condition

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth as foundationFillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.create.CreateViewModel
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.TimartType

/**
 * 储备池 v4 应用内统计条件表单（delta-prd-vs-code.md D-1.2 §4）：
 * 凝视时长 / 已阅胶囊数 / 尘迹数量 / 另一颗仍锁定 / 完成过备份 / 累计封存数 /
 * 同天解锁联动 / 开启后满 N 天 / 桌面小组件 / 今日打开次数。
 */

@Composable
fun WatchDurationForm(onConfirm: (UnlockCondition.WatchDurationAtLeast) -> Unit) {
    val L = LocalStrings.current
    var minutes by remember { mutableIntStateOf(10) }
    val condition = UnlockCondition.WatchDurationAtLeast(minutes * 60)

    ExtendFormScaffold(title = L.condWatchDuration, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = minutes, range = 1f..120f) { minutes = it }
        Text(
            text = L.viewCountNote,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun ReadCountForm(onConfirm: (UnlockCondition.ReadCountAtLeast) -> Unit) {
    val L = LocalStrings.current
    var count by remember { mutableIntStateOf(3) }
    val condition = UnlockCondition.ReadCountAtLeast(count)

    ExtendFormScaffold(title = L.condReadCount, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = count, range = 1f..50f, steps = 48) { count = it }
    }
}

@Composable
fun DestroyCountForm(onConfirm: (UnlockCondition.DestroyCountAtLeast) -> Unit) {
    val L = LocalStrings.current
    var count by remember { mutableIntStateOf(3) }
    val condition = UnlockCondition.DestroyCountAtLeast(count)

    ExtendFormScaffold(title = L.condDestroyCount, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = count, range = 1f..50f, steps = 48) { count = it }
    }
}

@Composable
fun TotalCreatedForm(onConfirm: (UnlockCondition.TotalCreatedCount) -> Unit) {
    val L = LocalStrings.current
    var count by remember { mutableIntStateOf(5) }
    val condition = UnlockCondition.TotalCreatedCount(count)

    ExtendFormScaffold(title = L.condTotalCreated, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = count, range = 1f..100f) { count = it }
    }
}

@Composable
fun TodayOpenForm(onConfirm: (UnlockCondition.TodayOpenCount) -> Unit) {
    val L = LocalStrings.current
    var count by remember { mutableIntStateOf(3) }
    val condition = UnlockCondition.TodayOpenCount(count)

    ExtendFormScaffold(title = L.condTodayOpen, valueCondition = condition, onConfirm = {
        onConfirm(condition)
    }) {
        IntSlider(value = count, range = 1f..20f, steps = 18) { count = it }
    }
}

@Composable
fun BackupDoneForm(onConfirm: (UnlockCondition.BackupDone) -> Unit) {
    val L = LocalStrings.current
    ExtendFormScaffold(
        title = L.condBackupDone,
        valueCondition = UnlockCondition.BackupDone,
        onConfirm = { onConfirm(UnlockCondition.BackupDone) },
    ) {}
}

@Composable
fun WidgetBoundForm(onConfirm: (UnlockCondition.WidgetBound) -> Unit) {
    val L = LocalStrings.current
    ExtendFormScaffold(
        title = L.condWidgetBound,
        valueCondition = UnlockCondition.WidgetBound,
        onConfirm = { onConfirm(UnlockCondition.WidgetBound) },
    ) {}
}

/** 指定胶囊仍锁定（否定依赖）：选一颗胶囊 */
@Composable
fun OtherCapsuleStillLockedForm(
    vm: CreateViewModel,
    onConfirm: (UnlockCondition) -> Unit,
) {
    val L = LocalStrings.current
    var capsules by remember { mutableStateOf<List<Capsule>>(emptyList()) }
    var pickedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        capsules = vm.allCapsules()
    }

    ExtendFormScaffold(
        title = L.condStillLocked,
        valueCondition = pickedId?.let { UnlockCondition.OtherCapsuleStillLocked(it) },
        enabled = pickedId != null,
        onConfirm = { pickedId?.let { onConfirm(UnlockCondition.OtherCapsuleStillLocked(it)) } },
    ) {
        Column(modifier = Modifier.padding(top = 10.dp)) {
            if (capsules.isEmpty()) {
                Text(text = L.condNoOtherCapsule, style = TimartType.caption, color = InkSecondary)
            }
            capsules.forEach { capsule ->
                SelectPill(
                    label = capsule.title.ifBlank { L.untitled },
                    selected = capsule.id == pickedId,
                    onClick = { pickedId = capsule.id },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
    }
}

/** 同天解锁联动 / 开启后满 N 天：选一颗已开启过阅读的胶囊（语义依赖 readAt meta） */
@Composable
fun CapsuleReadLinkForm(
    vm: CreateViewModel,
    mode: ReadLinkMode,
    onConfirm: (UnlockCondition) -> Unit,
) {
    val L = LocalStrings.current
    var capsules by remember { mutableStateOf<List<Capsule>>(emptyList()) }
    var pickedId by remember { mutableStateOf<String?>(null) }
    var days by remember { mutableIntStateOf(30) }

    LaunchedEffect(Unit) {
        capsules = vm.allCapsules()
    }

    val condition: UnlockCondition? = pickedId?.let {
        when (mode) {
            ReadLinkMode.SAME_DAY -> UnlockCondition.SameDayAsCapsuleRead(it)
            ReadLinkMode.DAYS_SINCE -> UnlockCondition.DaysSinceCapsuleRead(days, it)
        }
    }

    ExtendFormScaffold(
        title = if (mode == ReadLinkMode.SAME_DAY) L.condSameDayRead else L.condDaysSinceRead,
        valueCondition = condition,
        enabled = pickedId != null,
        onConfirm = { condition?.let(onConfirm) },
    ) {
        if (mode == ReadLinkMode.DAYS_SINCE) {
            IntSlider(value = days, range = 1f..365f) { days = it }
        }
        Column(modifier = Modifier.padding(top = 10.dp)) {
            if (capsules.isEmpty()) {
                Text(text = L.condNoOtherCapsule, style = TimartType.caption, color = InkSecondary)
            }
            capsules.forEach { capsule ->
                SelectPill(
                    label = capsule.title.ifBlank { L.untitled },
                    selected = capsule.id == pickedId,
                    onClick = { pickedId = capsule.id },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
    }
}

enum class ReadLinkMode { SAME_DAY, DAYS_SINCE }

/** Local fillMaxWidth alias（避免与外层 Column 域冲突的小封装） */
@Composable
private fun Modifier.fillMaxWidth(): Modifier =
    this.foundationFillMaxWidth()
