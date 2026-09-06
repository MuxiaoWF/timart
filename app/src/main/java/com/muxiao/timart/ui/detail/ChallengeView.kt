package com.muxiao.timart.ui.detail

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.unlock.UnlockCondition.ChallengeCondition
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.GestureKind
import com.muxiao.timart.domain.model.unlock.NfcPairing
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.utils.device.NfcCardWriter
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

/**
 * 现场挑战区（LOCKED 态渲染；架构 §2.16 增补）：
 * 挑战条件不参与周期快照判定（fail-closed），在本区当场完成：
 * 问答/谜题（文本应答）· 摇一摇（加速度峰值计数）· 翻面/静置（重力朝向 + 计时）· NFC（reader mode 贴卡）。
 * 完成即回调 [onAnswer]，由 VM 携带应答复判；全部条件满足 → 解锁。
 */
@Composable
fun ChallengeSection(
    challenges: List<ChallengeCondition>,
    onAnswer: (ChallengeCondition, String) -> Unit,
    modifier: Modifier = Modifier,
    /** 会话内已完成的挑战 id（权威状态源 = 判定结果时间线，VM 随每次复判刷新） */
    satisfiedIds: Set<String> = emptySet(),
) {
    if (challenges.isEmpty()) return
    val L = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 底部固定间距：下方 weight spacer 在内容超屏时会收缩为 0，无此间距挑战卡将贴住底部叙事
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = L.challengeSectionTitle,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 18.dp),
        )
        challenges.forEach { condition ->
            ChallengeCard(
                condition = condition,
                satisfied = condition.challengeId in satisfiedIds,
                onAnswer = onAnswer,
            )
        }
    }
}

/** 挑战完成标识（与时间线满足态同文案，金色低调不庆祝） */
@Composable
private fun DoneTag() {
    val L = LocalStrings.current
    Text(
        text = L.condSatisfiedTag,
        style = TimartType.caption,
        color = TimeGold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ChallengeCard(
    condition: ChallengeCondition,
    satisfied: Boolean,
    onAnswer: (ChallengeCondition, String) -> Unit,
) {
    // ChallengeCondition 是普通接口，本身不继承 UnlockCondition（各实现类才同时继承两者）；
    // 用 is 窄化取条件句，非 UnlockCondition 的理论分支取空句兜底
    val sentence = when (condition) {
        is UnlockCondition -> ConditionText.conditionSentence(condition, RuntimeSettings.resolvedLang)
        else -> ""
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Text(
            text = sentence,
            style = TimartType.body,
            color = if (satisfied) InkSecondary else InkPrimary,
        )
        if (satisfied) {
            // 权威完成态：替换交互件，明示该挑战已完成
            DoneTag()
        } else {
            when (condition) {
                is UnlockCondition.QuestionAnswer -> TextChallenge(onSubmit = { onAnswer(condition, it) })
                is UnlockCondition.PuzzleAnswer -> TextChallenge(onSubmit = { onAnswer(condition, it) })
                is UnlockCondition.ShakeCount -> ShakeChallenge(condition.shakes) { onAnswer(condition, DONE) }
                is UnlockCondition.FlipOrHold -> FlipHoldChallenge(condition) { onAnswer(condition, DONE) }
                is UnlockCondition.NfcTap -> NfcChallenge(condition) { onAnswer(condition, DONE) }
            }
        }
    }
}

private const val DONE = "DONE"

// ---- 问答 / 谜题：文本应答 ----

@Composable
private fun TextChallenge(onSubmit: (String) -> Unit) {
    val L = LocalStrings.current
    var text by remember { mutableStateOf("") }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = TimartType.body.copy(color = InkPrimary),
        cursorBrush = SolidColor(TimeGold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
    Button(
        onClick = { onSubmit(text) },
        enabled = text.isNotBlank(),
        colors = ButtonDefaults.buttonColors(
            containerColor = TimeGold,
            contentColor = DeepCharcoal,
            disabledContainerColor = SurfaceRaise,
            disabledContentColor = InkSecondary,
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth(),
    ) {
        Text(text = L.confirm, style = TimartType.caption)
    }
}

// ---- 传感器共享逻辑：加速度监听基座 ----

@Composable
private fun AccelerometerMonitor(onEvent: (FloatArray) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onEvent(event.values)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sm?.unregisterListener(listener) }
    }
}

// ---- 摇一摇 N 下 ----

@Composable
private fun ShakeChallenge(target: Int, onDone: () -> Unit) {
    val L = LocalStrings.current
    var count by remember { mutableIntStateOf(0) }
    var lastPeakAt by remember { mutableLongStateOf(0L) }
    AccelerometerMonitor { values ->
        val g = values.toList()
        val magnitude = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
        val now = System.currentTimeMillis()
        if (magnitude > SHAKE_THRESHOLD && now - lastPeakAt > SHAKE_DEBOUNCE_MS) {
            lastPeakAt = now
            count++
            if (count >= target) onDone()
        }
    }
    // 达标即本地即时反馈（权威态随后由 VM 复判回流接管）
    if (count >= target) {
        DoneTag()
    } else {
        Text(
            text = "$count / $target",
            style = TimartType.titleSerif.copy(color = TimeGold),
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = L.challengeKeepScreenOpen,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private const val SHAKE_THRESHOLD = 18f
private const val SHAKE_DEBOUNCE_MS = 350L

// ---- 翻面 / 屏幕朝下静置 ----

@Composable
private fun FlipHoldChallenge(condition: UnlockCondition.FlipOrHold, onDone: () -> Unit) {
    val L = LocalStrings.current
    var faceDown by remember { mutableStateOf(false) }
    var facedDownAt by remember { mutableLongStateOf(0L) }
    var progressSec by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }

    AccelerometerMonitor { values ->
        val z = values[2]
        val isDown = z < -7f
        if (isDown != faceDown) {
            faceDown = isDown
            if (isDown) facedDownAt = System.currentTimeMillis()
        }
    }

    // 翻面：朝下即完成；静置：朝下持续 N 秒（1s 步进计时）
    LaunchedEffect(faceDown) {
        if (condition.gesture == GestureKind.FLIP) {
            if (faceDown && !done) {
                done = true
                onDone()
            }
        } else {
            if (!faceDown) {
                progressSec = 0
            }
            while (faceDown && !done && progressSec < condition.holdSeconds) {
                delay(1000.milliseconds)
                progressSec++
                if (progressSec >= condition.holdSeconds) {
                    done = true
                    onDone()
                }
            }
        }
    }
    if (done) {
        // 本地即时反馈：翻面到位 / 静置计时满，不等复判回流
        DoneTag()
    } else {
        if (condition.gesture == GestureKind.HOLD) {
            Text(
                text = "$progressSec / ${condition.holdSeconds} s",
                style = TimartType.titleSerif.copy(color = TimeGold),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (!faceDown) {
            Text(
                text = L.challengeFaceDownHint,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ---- NFC 贴卡 ----

@Composable
private fun NfcChallenge(condition: UnlockCondition.NfcTap, onDone: () -> Unit) {
    val L = LocalStrings.current
    val context = LocalContext.current
    var detected by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    // 无 NFC 硬件的设备：明示不支持，而不是让用户拿着提示文案等一个永不到来的贴卡
    val nfcAvailable = remember(context) { NfcAdapter.getDefaultAdapter(context) != null }
    DisposableEffect(context) {
        val activity = context as? Activity
        val adapter = NfcAdapter.getDefaultAdapter(context)
        var callback: NfcAdapter.ReaderCallback?
        if (activity != null && adapter != null) {
            callback = NfcAdapter.ReaderCallback { tag: Tag ->
                if (detected) return@ReaderCallback
                // 任意标签模式直接通过；绑定模式读取卡上配对记录与 expectedPayload 比对
                val pass = condition.expectedPayload == null ||
                    NfcPairing.matches(condition.expectedPayload, NfcCardWriter.readPairingPayloads(tag))
                if (pass) {
                    detected = true
                    mismatch = false
                    onDone()
                } else {
                    mismatch = true
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
    if (detected) {
        // 本地即时反馈：贴卡核验通过
        DoneTag()
    } else if (mismatch) {
        Text(
            text = L.challengeNfcMismatch,
            style = TimartType.caption,
            color = TimeGold,
            modifier = Modifier.padding(top = 4.dp),
        )
    } else if (!nfcAvailable) {
        Text(
            text = L.condDeviceUnsupported,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    } else {
        Text(
            text = if (condition.expectedPayload == null) L.challengeNfcHint else L.challengeNfcHintPaired,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
