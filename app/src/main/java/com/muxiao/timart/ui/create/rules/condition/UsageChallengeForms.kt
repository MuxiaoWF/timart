package com.muxiao.timart.ui.create.rules.condition

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.GestureKind
import com.muxiao.timart.domain.model.unlock.NfcPairing
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.usecase.UnlockJudgeUseCase
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.create.CreateViewModel
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.utils.device.NfcCardWriter
import java.util.UUID

/**
 * 使用统计 + 现场挑战条件表单（架构 §2.15 增补）：
 * 打开次数 / 连续打开 / 好久不见 / 胶囊数量 / 联动解锁销毁 /
 * 回答问题 / 谜题 / 摇一摇 / 翻面静置 / NFC。
 */

// ================= 使用统计 =================

@Composable
fun OpenCountForm(onConfirm: (UnlockCondition.OpenCountAtLeast) -> Unit) {
    val L = LocalStrings.current
    var count by remember { mutableIntStateOf(10) }
    val condition = UnlockCondition.OpenCountAtLeast(count)

    ExtendFormScaffold(title = L.condOpenCount, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        IntSlider(value = count, range = 1f..200f) { count = it }
    }
}

@Composable
fun OpenStreakForm(onConfirm: (UnlockCondition.OpenStreak) -> Unit) {
    val L = LocalStrings.current
    var days by remember { mutableIntStateOf(7) }
    val condition = UnlockCondition.OpenStreak(days)

    ExtendFormScaffold(title = L.condOpenStreak, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        IntSlider(value = days, range = 1f..60f) { days = it }
    }
}

@Composable
fun DaysSinceLastOpenForm(onConfirm: (UnlockCondition.DaysSinceLastOpen) -> Unit) {
    val L = LocalStrings.current
    var days by remember { mutableIntStateOf(7) }
    val condition = UnlockCondition.DaysSinceLastOpen(days)

    ExtendFormScaffold(title = L.condLastOpen, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        IntSlider(value = days, range = 1f..365f) { days = it }
    }
}

@Composable
fun CapsuleCountForm(onConfirm: (UnlockCondition.CapsuleCountAtLeast) -> Unit) {
    val L = LocalStrings.current
    var count by remember { mutableIntStateOf(3) }
    val condition = UnlockCondition.CapsuleCountAtLeast(count)

    ExtendFormScaffold(title = L.condCapsuleCount, valueCondition = condition, onConfirm = { onConfirm(condition) }) {
        IntSlider(value = count, range = 1f..100f) { count = it }
    }
}

/** 「某颗已解锁 / 已销毁」表单：选择一枚胶囊 + 选择期望状态 */
@Composable
fun OtherCapsuleStateForm(
    vm: CreateViewModel,
    onConfirm: (UnlockCondition) -> Unit,
) {
    val L = LocalStrings.current
    var capsules by remember { mutableStateOf<List<Capsule>>(emptyList()) }
    var pickedId by remember { mutableStateOf<String?>(null) }
    var wantDestroyed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        capsules = vm.allCapsules()
    }

    val condition: UnlockCondition? = pickedId?.let {
        if (wantDestroyed) {
            UnlockCondition.OtherCapsuleDestroyed(it)
        } else {
            UnlockCondition.OtherCapsuleUnlocked(it)
        }
    }

    ExtendFormScaffold(
        title = if (wantDestroyed) L.condOtherDestroyed else L.condOtherUnlocked,
        valueCondition = condition,
        enabled = pickedId != null,
        onConfirm = { condition?.let(onConfirm) },
    ) {
        FilterChip(
            selected = !wantDestroyed,
            onClick = { wantDestroyed = false },
            label = { Text(text = L.condOtherUnlocked, style = TimartType.caption) },
            modifier = Modifier.padding(top = 8.dp),
        )
        FilterChip(
            selected = wantDestroyed,
            onClick = { wantDestroyed = true },
            label = { Text(text = L.condOtherDestroyed, style = TimartType.caption) },
            modifier = Modifier.padding(top = 4.dp),
        )
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

// ================= 现场挑战 =================

/** 文本输入小行（问题 / 答案共用） */
@Composable
private fun ChallengeTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
) {
    Text(
        text = label,
        style = TimartType.caption,
        color = InkSecondary,
        modifier = Modifier.padding(top = 12.dp),
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TimartType.body.copy(color = InkPrimary),
        cursorBrush = SolidColor(TimeGold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(SurfaceRaise)
            .padding(12.dp),
    )
}

@Composable
fun QuestionAnswerForm(onConfirm: (UnlockCondition.QuestionAnswer) -> Unit) {
    val L = LocalStrings.current
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

    ExtendFormScaffold(
        title = L.condQuestion,
        valueCondition = if (question.isBlank()) null else UnlockCondition.QuestionAnswer("preview", question, "…"),
        enabled = question.isNotBlank() && answer.isNotBlank(),
        onConfirm = {
            onConfirm(
                UnlockCondition.QuestionAnswer(
                    challengeId = UUID.randomUUID().toString(),
                    question = question.trim(),
                    expectedAnswer = answer.trim(),
                ),
            )
        },
    ) {
        ChallengeTextField(label = L.qaQuestionLabel, value = question, onValueChange = { question = it })
        ChallengeTextField(label = L.qaAnswerLabel, value = answer, onValueChange = { answer = it })
        Text(
            text = L.qaAnswerNote,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun PuzzleAnswerForm(onConfirm: (UnlockCondition.PuzzleAnswer) -> Unit) {
    val L = LocalStrings.current
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

    ExtendFormScaffold(
        title = L.condPuzzle,
        valueCondition = if (question.isBlank()) null else UnlockCondition.PuzzleAnswer("preview", question, "…"),
        enabled = question.isNotBlank() && answer.isNotBlank(),
        onConfirm = {
            onConfirm(
                UnlockCondition.PuzzleAnswer(
                    challengeId = UUID.randomUUID().toString(),
                    question = question.trim(),
                    answerHash = UnlockJudgeUseCase.sha256Hex(answer.trim().lowercase()),
                ),
            )
        },
    ) {
        ChallengeTextField(label = L.qaQuestionLabel, value = question, onValueChange = { question = it })
        ChallengeTextField(label = L.puzzleAnswerLabel, value = answer, onValueChange = { answer = it })
        Text(
            text = L.puzzleAnswerNote,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun ShakeCountForm(onConfirm: (UnlockCondition.ShakeCount) -> Unit) {
    val L = LocalStrings.current
    var shakes by remember { mutableIntStateOf(10) }
    val condition = UnlockCondition.ShakeCount("preview", shakes)

    ExtendFormScaffold(title = L.condShake, valueCondition = condition, onConfirm = {
        onConfirm(UnlockCondition.ShakeCount(UUID.randomUUID().toString(), shakes))
    }) {
        IntSlider(value = shakes, range = 1f..50f) { shakes = it }
    }
}

@Composable
fun FlipOrHoldForm(onConfirm: (UnlockCondition.FlipOrHold) -> Unit) {
    val L = LocalStrings.current
    var gesture by remember { mutableStateOf(GestureKind.FLIP) }
    var holdSeconds by remember { mutableIntStateOf(10) }
    val condition = UnlockCondition.FlipOrHold("preview", gesture, holdSeconds)

    ExtendFormScaffold(title = L.condFlipHold, valueCondition = condition, onConfirm = {
        onConfirm(UnlockCondition.FlipOrHold(UUID.randomUUID().toString(), gesture, holdSeconds))
    }) {
        Row(modifier = Modifier.padding(top = 10.dp)) {
            SelectPill(
                label = ConditionText.conditionSentence(UnlockCondition.FlipOrHold("p", GestureKind.FLIP, 0), RuntimeSettings.resolvedLang),
                selected = gesture == GestureKind.FLIP,
                onClick = { gesture = GestureKind.FLIP },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = L.condHoldOption,
                selected = gesture == GestureKind.HOLD,
                onClick = { gesture = GestureKind.HOLD },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
        if (gesture == GestureKind.HOLD) {
            IntSlider(value = holdSeconds, range = 3f..60f) { holdSeconds = it }
        }
    }
}

@Composable
fun NfcTapForm(onConfirm: (UnlockCondition.NfcTap) -> Unit) {
    val L = LocalStrings.current
    val challengeId = remember { UUID.randomUUID().toString() }
    var pairedMode by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(false) }

    val condition = UnlockCondition.NfcTap(
        challengeId = challengeId,
        expectedPayload = if (pairedMode && paired) NfcPairing.payloadFor(challengeId) else null,
    )

    ExtendFormScaffold(
        title = L.condNfc,
        valueCondition = if (pairedMode && !paired) null else condition,
        enabled = !pairedMode || paired,
        onConfirm = { onConfirm(condition) },
    ) {
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth(),
        ) {
            SelectPill(
                label = L.nfcModeAny,
                selected = !pairedMode,
                onClick = { pairedMode = false },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = L.nfcModePaired,
                selected = pairedMode,
                onClick = { pairedMode = true },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
        if (pairedMode) {
            NfcWritePanel(
                challengeId = challengeId,
                paired = paired,
                onPaired = { paired = true },
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = L.nfcPairNote,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                text = L.nfcFormHint,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ---- NFC 写卡面板（绑定指定卡） ----

private enum class NfcWriteStatus { IDLE, WAITING, SUCCESS, FAILURE }

@Composable
private fun NfcWritePanel(
    challengeId: String,
    paired: Boolean,
    onPaired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    var status by remember { mutableStateOf(NfcWriteStatus.IDLE) }
    var failMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context, challengeId) {
        val activity = context as? Activity
        val adapter = NfcAdapter.getDefaultAdapter(context)
        var callback: NfcAdapter.ReaderCallback?
        if (activity != null && adapter != null) {
            callback = NfcAdapter.ReaderCallback { tag: Tag ->
                // reader callback 在 binder 线程（非主线程），NDEF 写入可在此同步执行
                if (status != NfcWriteStatus.WAITING) return@ReaderCallback
                when (val result = NfcCardWriter.writePairing(tag, challengeId)) {
                    is NfcCardWriter.Result.Success -> {
                        status = NfcWriteStatus.SUCCESS
                        onPaired()
                    }
                    is NfcCardWriter.Result.Failure -> {
                        status = NfcWriteStatus.FAILURE
                        failMessage = when (result.reason) {
                            NfcCardWriter.FailReason.NO_NDEF -> L.nfcWriteFailNoNdef
                            NfcCardWriter.FailReason.TOO_SMALL -> L.nfcWriteFailSize
                            NfcCardWriter.FailReason.IO -> L.nfcWriteFailIo
                        }
                    }
                }
            }
            adapter.enableReaderMode(
                activity,
                callback,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                null,
            )
        }
        onDispose {
            if (activity != null && adapter != null) {
                runCatching { adapter.disableReaderMode(activity) }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            status == NfcWriteStatus.WAITING ->
                Text(text = L.nfcWriteWaiting, style = TimartType.caption, color = TimeGold)
            status == NfcWriteStatus.SUCCESS && paired -> {
                Text(text = L.nfcWriteSuccess, style = TimartType.caption, color = TimeGold)
                FormConfirmButton(enabled = true, label = L.nfcWriteRewrite, onClick = {
                    status = NfcWriteStatus.WAITING
                    failMessage = null
                })
            }
            status == NfcWriteStatus.FAILURE -> {
                Text(
                    text = failMessage ?: L.nfcWriteFailIo,
                    style = TimartType.caption,
                    color = InkSecondary,
                )
                FormConfirmButton(enabled = true, label = L.nfcWriteStart, onClick = {
                    status = NfcWriteStatus.WAITING
                    failMessage = null
                })
            }
            else ->
                FormConfirmButton(enabled = true, label = L.nfcWriteStart, onClick = {
                    status = NfcWriteStatus.WAITING
                    failMessage = null
                })
        }
    }
}
