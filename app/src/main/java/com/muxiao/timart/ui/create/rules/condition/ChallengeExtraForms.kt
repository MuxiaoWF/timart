package com.muxiao.timart.ui.create.rules.condition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.unlock.LiftDirection
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.usecase.UnlockJudgeUseCase
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import java.util.UUID

/**
 * 储备池 v4 现场挑战条件表单（delta-prd-vs-code.md D-1.2 §5）：
 * 手势图案 / 当场步数 / 转手机 / 音量键 / 静置 / 举高放低 / 语音口令 / 连点 / 爬楼 / 扫码 / 算力。
 * 全部产出 ChallengeCondition 子类，快照判定 fail-closed，详情页当场完成。
 */

// ================= 九宫格图案（创建与挑战共用） =================

/**
 * 3×3 九宫格手势板：拖动经过的点位按顺序记录，抬手回调完整序列
 * （序列以点位下标 0–8 组成，规范化串 = 下标以 "-" 连接；判定侧 SHA-256 比对）。
 */
@Composable
internal fun PatternPad(
    modifier: Modifier = Modifier,
    onPatternComplete: (List<Int>) -> Unit,
) {
    var sequence by remember { mutableStateOf(listOf<Int>()) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(DeepCharcoal, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    sequence = emptyList()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val pos = change.position
                        val cellWidth = size.width / 3f
                        val cellHeight = size.height / 3f
                        val col = (pos.x / cellWidth).toInt().coerceIn(0, 2)
                        val row = (pos.y / cellHeight).toInt().coerceIn(0, 2)
                        val idx = row * 3 + col
                        if (sequence.lastOrNull() != idx) sequence = sequence + idx
                    }
                    onPatternComplete(sequence.toList())
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellWidth = size.width / 3f
            val cellHeight = size.height / 3f
            val dotRadius = cellWidth * 0.08f
            fun center(idx: Int) = Offset(((idx % 3) + 0.5f) * cellWidth, ((idx / 3) + 0.5f) * cellHeight)
            for (i in 0 until sequence.size - 1) {
                drawLine(
                    color = TimeGold,
                    start = center(sequence[i]),
                    end = center(sequence[i + 1]),
                    strokeWidth = dotRadius * 0.7f,
                )
            }
            for (idx in 0 until 9) {
                val visited = idx in sequence
                drawCircle(
                    color = if (visited) TimeGold else SurfaceRaise,
                    radius = if (visited) dotRadius * 1.3f else dotRadius,
                    center = center(idx),
                )
            }
        }
    }
}

private fun sequenceString(sequence: List<Int>): String = sequence.joinToString("-")

// ================= 手势图案 =================

@Composable
fun GesturePatternForm(onConfirm: (UnlockCondition.GesturePattern) -> Unit) {
    val L = LocalStrings.current
    var pattern by remember { mutableStateOf(listOf<Int>()) }

    ExtendFormScaffold(
        title = L.condGesturePattern,
        valueCondition = if (pattern.size >= 4) UnlockCondition.GesturePattern("preview", "…") else null,
        enabled = pattern.size >= 4,
        onConfirm = {
            onConfirm(
                UnlockCondition.GesturePattern(
                    challengeId = UUID.randomUUID().toString(),
                    answerHash = UnlockJudgeUseCase.sha256Hex(sequenceString(pattern)),
                ),
            )
        },
    ) {
        PatternPad(modifier = Modifier.padding(top = 10.dp), onPatternComplete = { pattern = it })
        Text(
            text = L.gestureFormHint,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ================= 当场步数 =================

@Composable
fun WalkStepsNowForm(onConfirm: (UnlockCondition.WalkStepsNow) -> Unit) {
    val L = LocalStrings.current
    var steps by remember { mutableIntStateOf(100) }

    ExtendFormScaffold(title = L.condWalkNow, valueCondition = UnlockCondition.WalkStepsNow("preview", steps), onConfirm = {
        onConfirm(UnlockCondition.WalkStepsNow(UUID.randomUUID().toString(), steps))
    }) {
        IntSlider(value = steps, range = 20f..2000f) { steps = it }
        Text(
            text = L.stepLimitNote,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ================= 转手机一圈 =================

@Composable
fun SpinPhoneForm(onConfirm: (UnlockCondition.SpinPhone) -> Unit) {
    val L = LocalStrings.current
    var degrees by remember { mutableIntStateOf(360) }

    ExtendFormScaffold(title = L.condSpin, valueCondition = UnlockCondition.SpinPhone("preview", degrees), onConfirm = {
        onConfirm(UnlockCondition.SpinPhone(UUID.randomUUID().toString(), degrees))
    }) {
        IntSlider(value = degrees, range = 180f..1440f, steps = 8) { degrees = it }
    }
}

// ================= 音量键同按 =================

@Composable
fun VolumeKeyComboForm(onConfirm: (UnlockCondition.VolumeKeyCombo) -> Unit) {
    val L = LocalStrings.current
    var seconds by remember { mutableIntStateOf(5) }

    ExtendFormScaffold(title = L.condVolumeKeys, valueCondition = UnlockCondition.VolumeKeyCombo("preview", seconds), onConfirm = {
        onConfirm(UnlockCondition.VolumeKeyCombo(UUID.randomUUID().toString(), seconds))
    }) {
        IntSlider(value = seconds, range = 3f..30f, steps = 26) { seconds = it }
    }
}

// ================= 静置挑战 =================

@Composable
fun StayStillForm(onConfirm: (UnlockCondition.StayStill) -> Unit) {
    val L = LocalStrings.current
    var seconds by remember { mutableIntStateOf(15) }

    ExtendFormScaffold(title = L.condStayStill, valueCondition = UnlockCondition.StayStill("preview", seconds), onConfirm = {
        onConfirm(UnlockCondition.StayStill(UUID.randomUUID().toString(), seconds))
    }) {
        IntSlider(value = seconds, range = 5f..60f) { seconds = it }
    }
}

// ================= 举高 / 放低 =================

@Composable
fun LiftHighLowerLowForm(onConfirm: (UnlockCondition.LiftHighLowerLow) -> Unit) {
    val L = LocalStrings.current
    var direction by remember { mutableStateOf(LiftDirection.UP) }
    var meters by remember { mutableIntStateOf(2) }

    ExtendFormScaffold(
        title = L.condLift,
        valueCondition = UnlockCondition.LiftHighLowerLow("preview", direction, meters.toDouble()),
        onConfirm = {
            onConfirm(
                UnlockCondition.LiftHighLowerLow(UUID.randomUUID().toString(), direction, meters.toDouble()),
            )
        },
    ) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = com.muxiao.timart.domain.model.unlock.ConditionText.conditionSentence(
                    UnlockCondition.LiftHighLowerLow("p", LiftDirection.UP, meters.toDouble()),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = direction == LiftDirection.UP,
                onClick = { direction = LiftDirection.UP },
                modifier = Modifier.weight(1f),
            )
            SelectPill(
                label = com.muxiao.timart.domain.model.unlock.ConditionText.conditionSentence(
                    UnlockCondition.LiftHighLowerLow("p", LiftDirection.DOWN, meters.toDouble()),
                    com.muxiao.timart.utils.RuntimeSettings.resolvedLang,
                ),
                selected = direction == LiftDirection.DOWN,
                onClick = { direction = LiftDirection.DOWN },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
        IntSlider(value = meters, range = 1f..50f) { meters = it }
    }
}

// ================= 语音口令 =================

@Composable
fun VoicePasswordForm(onConfirm: (UnlockCondition.VoicePassword) -> Unit) {
    val L = LocalStrings.current
    var passphrase by remember { mutableStateOf("") }

    ExtendFormScaffold(
        title = L.condVoice,
        valueCondition = if (passphrase.isBlank()) null else UnlockCondition.VoicePassword("preview", passphrase),
        enabled = passphrase.isNotBlank(),
        onConfirm = {
            onConfirm(
                UnlockCondition.VoicePassword(
                    challengeId = UUID.randomUUID().toString(),
                    expectedAnswer = passphrase.trim(),
                ),
            )
        },
    ) {
        BasicTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
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
            text = L.voiceFormHint,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ================= 连点屏幕 =================

@Composable
fun TapCountForm(onConfirm: (UnlockCondition.TapCount) -> Unit) {
    val L = LocalStrings.current
    var taps by remember { mutableIntStateOf(20) }

    ExtendFormScaffold(title = L.condTap, valueCondition = UnlockCondition.TapCount("preview", taps), onConfirm = {
        onConfirm(UnlockCondition.TapCount(UUID.randomUUID().toString(), taps))
    }) {
        IntSlider(value = taps, range = 10f..200f, steps = 18) { taps = it }
    }
}

// ================= 当场爬楼 =================

@Composable
fun ClimbFloorsForm(onConfirm: (UnlockCondition.ClimbFloors) -> Unit) {
    val L = LocalStrings.current
    var floors by remember { mutableIntStateOf(3) }

    ExtendFormScaffold(title = L.condClimb, valueCondition = UnlockCondition.ClimbFloors("preview", floors), onConfirm = {
        onConfirm(UnlockCondition.ClimbFloors(UUID.randomUUID().toString(), floors))
    }) {
        IntSlider(value = floors, range = 1f..30f, steps = 28) { floors = it }
    }
}

// ================= 扫二维码 =================

@Composable
fun ScanQrForm(onConfirm: (UnlockCondition.ScanQr) -> Unit) {
    val L = LocalStrings.current
    var pairedMode by remember { mutableStateOf(false) }
    var payload by remember { mutableStateOf("") }
    val valid = !pairedMode || payload.trim().isNotEmpty()

    ExtendFormScaffold(
        title = L.condScanQr,
        valueCondition = if (pairedMode && payload.trim().isEmpty()) {
            null
        } else {
            UnlockCondition.ScanQr("preview", payload.trim().takeIf { pairedMode && it.isNotEmpty() })
        },
        enabled = valid,
        onConfirm = {
            onConfirm(
                UnlockCondition.ScanQr(
                    challengeId = UUID.randomUUID().toString(),
                    expectedPayload = payload.trim().takeIf { pairedMode && it.isNotEmpty() },
                ),
            )
        },
    ) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            SelectPill(
                label = L.nfcModeAny,
                selected = !pairedMode,
                onClick = {
                    pairedMode = false
                    payload = ""
                },
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
            BasicTextField(
                value = payload,
                onValueChange = { payload = it },
                singleLine = true,
                textStyle = TimartType.body.copy(color = InkPrimary),
                cursorBrush = SolidColor(TimeGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(SurfaceRaise)
                    .padding(12.dp),
            )
        }
    }
}

// ================= 算力挑战 =================

@Composable
fun ProofOfWorkForm(onConfirm: (UnlockCondition.ProofOfWork) -> Unit) {
    val L = LocalStrings.current
    var difficulty by remember { mutableIntStateOf(3) }

    ExtendFormScaffold(
        title = L.condPow,
        valueCondition = UnlockCondition.ProofOfWork("preview", difficulty),
        onConfirm = {
            onConfirm(UnlockCondition.ProofOfWork(UUID.randomUUID().toString(), difficulty))
        },
    ) {
        IntSlider(value = difficulty, range = 2f..4f, steps = 1) { difficulty = it }
        Text(
            text = L.powFormNote,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
