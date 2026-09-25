package com.muxiao.timart.ui.create.chain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.create.CreateViewModel
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType

/**
 * 连环信向导（储备池暂缓项落地）：一次封存一系列信，读一封出下一封。
 *
 * - 信件列表步：2–[com.muxiao.timart.ui.create.CreateViewModel.CHAIN_MAX_LETTERS] 封逐封撰写（纯文本）；
 * - 节奏确认步：首封「满 N 天」与后续「上封读后满 N 天」两档滑条 + 汇总；
 * - 封存 = 批量复用 `CapsuleCrudUseCase.create`：首封常规胶囊，第 2 封起藏进上一封
 *   （meta `capsule.seedOf.<id>`，父读后萌芽出现——嵌套种子既有语义，引擎零改动），
 *   解锁规则 = `DaysSinceCapsuleRead(间隔, 上一封)`。
 * 纯文本信（无图片/语音/盲盒/标签）；每封封存后仍可经「后悔药」单颗改造。
 */
@Composable
fun ChainWizardStep(
    vm: CreateViewModel,
    onBackToSingle: () -> Unit,
    onNeedPasswordSetup: () -> Unit,
    onFailed: (String) -> Unit,
) {
    val L = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        // 步内返回（退出连环信模式，回到单颗三步流程）
        TextButton(
            onClick = {
                vm.exitChainMode()
                onBackToSingle()
            },
            colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
        ) {
            Text(text = L.prevStep, style = TimartType.caption)
        }

        Text(
            text = L.chainWizTitle,
            style = TimartType.titleSerif,
            color = InkPrimary,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = L.chainDesc,
            style = TimartType.body,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )

        when (vm.chainSubStep) {
            0 -> LettersStep(vm)
            else -> RhythmStep(
                vm = vm,
                onNeedPasswordSetup = onNeedPasswordSetup,
                onFailed = onFailed,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** 信件列表步：逐封撰写 + 加一封/删一封 + 前往节奏步 */
@Composable
private fun LettersStep(vm: CreateViewModel) {
    val L = LocalStrings.current
    Text(
        text = L.chainCountFmt.format(vm.chainLetters.size),
        style = TimartType.caption,
        color = InkSecondary,
        modifier = Modifier.padding(top = 20.dp),
    )
    vm.chainLetters.forEachIndexed { index, letter ->
        LetterCard(
            index = index,
            title = letter.title,
            content = letter.content,
            onTitle = { vm.updateChainLetterTitle(index, it) },
            onContent = { vm.updateChainLetterContent(index, it) },
            onRemove = if (vm.chainLetters.size > CreateViewModel.CHAIN_MIN_LETTERS) {
                { vm.removeChainLetter(index) }
            } else {
                null
            },
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    if (vm.chainLetters.size < CreateViewModel.CHAIN_MAX_LETTERS) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .background(SurfaceRaise, RoundedCornerShape(12.dp))
                .clickable { vm.addChainLetter() }
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Text(text = "＋", style = TimartType.body, color = TimeGold)
            Text(
                text = L.chainAddLetter,
                style = TimartType.body,
                color = InkPrimary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }

    Button(
        onClick = { vm.gotoChainSubStep(1) },
        enabled = vm.chainLettersComplete(),
        colors = ButtonDefaults.buttonColors(
            containerColor = TimeGold,
            contentColor = DeepCharcoal,
        ),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .padding(top = 24.dp)
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            text = if (vm.chainLettersComplete()) L.chainRhythmNext else L.chainIncomplete,
            style = TimartType.body,
        )
    }
}

/** 单封信卡（标题单行 + 正文多行；删除按钮仅在多于下限时出现） */
@Composable
private fun LetterCard(
    index: Int,
    title: String,
    content: String,
    onTitle: (String) -> Unit,
    onContent: (String) -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DeepCharcoal.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = L.chainLetterNFmt.format(index + 1),
                style = TimartType.caption,
                color = TimeGold,
            )
            Spacer(modifier = Modifier.weight(1f))
            onRemove?.let {
                Text(
                    text = L.batchDelete,
                    style = TimartType.caption,
                    color = InkDisabled,
                    modifier = Modifier
                        .clickable(onClick = it)
                        .padding(start = 12.dp),
                )
            }
        }
        BasicTextField(
            value = title,
            onValueChange = onTitle,
            singleLine = true,
            textStyle = TimartType.body.copy(color = InkPrimary),
            cursorBrush = SolidColor(TimeGold),
            decorationBox = { inner ->
                if (title.isEmpty()) {
                    Text(
                        text = L.chainTitleHintFmt.format(index + 1),
                        style = TimartType.body,
                        color = InkDisabled,
                    )
                } else {
                    inner()
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
        )
        BasicTextField(
            value = content,
            onValueChange = onContent,
            textStyle = TimartType.caption.copy(color = InkPrimary),
            cursorBrush = SolidColor(TimeGold),
            decorationBox = { inner ->
                if (content.isEmpty()) {
                    Text(
                        text = L.chainContentHint,
                        style = TimartType.caption,
                        color = InkDisabled,
                    )
                } else {
                    inner()
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(96.dp),
        )
    }
}

/** 节奏与确认步：两档滑条 + 链式摘要 + 批量封存 CTA */
@Composable
private fun RhythmStep(
    vm: CreateViewModel,
    onNeedPasswordSetup: () -> Unit,
    onFailed: (String) -> Unit,
) {
    val L = LocalStrings.current
    Text(
        text = L.chainFirstDaysFmt.format(vm.chainFirstDays),
        style = TimartType.body,
        color = InkPrimary,
        modifier = Modifier.padding(top = 22.dp),
    )
    Text(
        text = L.chainFirstDaysDesc,
        style = TimartType.caption,
        color = InkSecondary,
        modifier = Modifier.padding(top = 2.dp),
    )
    DaySlider(
        value = vm.chainFirstDays,
        maxValue = CreateViewModel.CHAIN_FIRST_DAYS_MAX,
        onValueChange = vm::updateChainFirstDays,
        modifier = Modifier.padding(top = 6.dp),
    )
    Text(
        text = L.chainIntervalDaysFmt.format(vm.chainIntervalDays),
        style = TimartType.body,
        color = InkPrimary,
        modifier = Modifier.padding(top = 18.dp),
    )
    Text(
        text = L.chainIntervalDesc,
        style = TimartType.caption,
        color = InkSecondary,
        modifier = Modifier.padding(top = 2.dp),
    )
    DaySlider(
        value = vm.chainIntervalDays,
        maxValue = CreateViewModel.CHAIN_INTERVAL_DAYS_MAX,
        onValueChange = vm::updateChainIntervalDays,
        modifier = Modifier.padding(top = 6.dp),
    )

    Column(
        modifier = Modifier
            .padding(top = 22.dp)
            .fillMaxWidth()
            .background(DeepCharcoal.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = L.chainSummaryCountFmt.format(vm.chainLetters.size),
            style = TimartType.caption,
            color = InkPrimary,
        )
        Text(
            text = if (vm.chainFirstDays == 0) {
                L.chainSummaryFirstNow
            } else {
                L.chainSummaryFirstFmt.format(vm.chainFirstDays)
            },
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = L.chainSummaryIntervalFmt.format(vm.chainIntervalDays),
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = L.chainSummarySeedNote,
            style = TimartType.caption,
            color = TimeGold,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    Row(modifier = Modifier.padding(top = 10.dp)) {
        TextButton(onClick = { vm.gotoChainSubStep(0) }) {
            Text(text = L.prevStep, style = TimartType.caption, color = InkSecondary)
        }
        Spacer(modifier = Modifier.width(12.dp))
    }

    Button(
        onClick = { vm.submitChain(onNeedPasswordSetup = onNeedPasswordSetup, onFailed = onFailed) },
        colors = ButtonDefaults.buttonColors(
            containerColor = TimeGold,
            contentColor = DeepCharcoal,
        ),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(text = L.chainSealAllFmt.format(vm.chainLetters.size), style = TimartType.body)
    }
}

/** 天数滑条（整数步进；0 = 立即可解仅首封支持） */
@Composable
private fun DaySlider(
    value: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = 0f..maxValue.toFloat(),
        steps = (maxValue - 1).coerceAtLeast(0),
        colors = SliderDefaults.colors(
            thumbColor = TimeGold,
            activeTrackColor = TimeGold,
            inactiveTrackColor = DeepCharcoal,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
