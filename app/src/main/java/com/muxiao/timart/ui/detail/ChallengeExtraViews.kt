package com.muxiao.timart.ui.detail

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.usecase.UnlockJudgeUseCase
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.create.rules.condition.PatternPad
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.utils.app.VolumeKeyEventBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

/**
 * 储备池 v4 现场挑战执行区（delta-prd-vs-code.md D-1.2 §5；架构 §2.16 增补）：
 * 图案（九宫格拖画）/ 当场步数（计步器差值）/ 转机（陀螺仪积分）/ 音量键（事件总线）/
 * 静置（加速度方差）/ 举高放低（气压差）/ 语音（系统识别）/ 连点 / 爬楼（气压差）/
 * 扫码（系统相机 + zxing 解码）/ 算力（SHA-256 前导零）。
 * 完成即回调 onAnswer，由 VM 携带应答复判；权威完成态由时间线回流。
 */

// ---- 手势图案 ----

@Composable
internal fun GesturePatternChallenge(
    onAnswer: (String) -> Unit,
) {
    val L = LocalStrings.current
    var submitted by remember { mutableStateOf(false) }
    PatternPad(modifier = Modifier.padding(top = 8.dp)) { sequence ->
        if (!submitted && sequence.size >= 4) {
            submitted = true
            onAnswer(sequence.joinToString("-"))
        }
    }
    Text(
        text = L.gesturePadHint,
        style = TimartType.caption,
        color = InkSecondary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

// ---- 当场步数（TYPE_STEP_COUNTER 会话差值） ----

@Composable
internal fun WalkNowChallenge(target: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    var baseline by remember { mutableLongStateOf(-1L) }
    var steps by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
                val total = event.values.firstOrNull()?.toLong() ?: return
                if (baseline < 0) baseline = total
                val delta = (total - baseline).toInt()
                steps = delta
                if (delta >= target) onDone()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose { sm?.unregisterListener(listener) }
    }
    ProgressText(progress = steps.coerceAtMost(target), target = target)
}

// ---- 转手机一圈（陀螺仪 z 轴积分） ----

@Composable
internal fun SpinChallenge(targetDeg: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    var degrees by remember { mutableIntStateOf(0) }
    var lastAt by remember { mutableLongStateOf(0L) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
                val now = System.currentTimeMillis()
                if (lastAt == 0L) {
                    lastAt = now
                    return
                }
                val dtSec = (now - lastAt) / 1000f
                lastAt = now
                val zDegPerSec = Math.toDegrees(event.values.getOrNull(2)?.toDouble() ?: 0.0)
                val accumulated = degrees + (abs(zDegPerSec) * dtSec).toInt()
                degrees = accumulated
                if (accumulated >= targetDeg) onDone()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sm?.unregisterListener(listener) }
    }
    ProgressText(progress = degrees.coerceAtMost(targetDeg), target = targetDeg, suffix = "°")
}

// ---- 音量键同按 ----

@Composable
internal fun VolumeKeysChallenge(holdSeconds: Int, onDone: () -> Unit) {
    val L = LocalStrings.current
    var upPressed by remember { mutableStateOf(false) }
    var downPressed by remember { mutableStateOf(false) }
    var progressSec by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val both = upPressed && downPressed

    LaunchedEffect(Unit) {
        VolumeKeyEventBus.events.collect { (keyCode, pressed) ->
            when (keyCode) {
                VolumeKeyEventBus.KEY_UP -> upPressed = pressed
                VolumeKeyEventBus.KEY_DOWN -> downPressed = pressed
            }
        }
    }
    // 两键同按期间 1s 步进计时；任一抬起归零重来
    LaunchedEffect(both) {
        if (!both) {
            progressSec = 0
            return@LaunchedEffect
        }
        while (!done && progressSec < holdSeconds) {
            delay(1000.milliseconds)
            progressSec++
            if (progressSec >= holdSeconds) {
                done = true
                onDone()
            }
        }
    }
    Text(
        text = if (done) "" else L.challengeVolumeKeysHint,
        style = TimartType.caption,
        color = InkSecondary,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (both && !done) {
        ProgressText(progress = progressSec, target = holdSeconds, suffix = " s")
    }
}

// ---- 静置挑战（加速度方差） ----

@Composable
internal fun StayStillChallenge(holdSeconds: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    var deviation by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val v = event.values
                val magnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                deviation = abs(magnitude - SensorManager.GRAVITY_EARTH)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose { sm?.unregisterListener(listener) }
    }

    var progressSec by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(deviation) {
        val dev = deviation ?: return@LaunchedEffect
        if (dev > STILL_TOLERANCE_MPS2) {
            progressSec = 0
        } else if (!done && progressSec < holdSeconds) {
            delay(1000.milliseconds)
            progressSec++
            if (progressSec >= holdSeconds) {
                done = true
                onDone()
            }
        }
    }
    if (!done) {
        ProgressText(progress = progressSec, target = holdSeconds, suffix = " s")
    }
}

private const val STILL_TOLERANCE_MPS2 = 0.35f

// ---- 举高 / 放低（气压差换算海拔） ----

@Composable
internal fun LiftChallenge(condition: UnlockCondition.LiftHighLowerLow, onDone: () -> Unit) {
    val context = LocalContext.current
    var deltaMeters by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        var baseAlt: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_PRESSURE) return
                val pressure = event.values.firstOrNull() ?: return
                val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
                if (baseAlt == null) baseAlt = alt
                deltaMeters = alt - baseAlt
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose { sm?.unregisterListener(listener) }
    }
    val delta = deltaMeters
    val achieved = delta != null && (
        (condition.direction == com.muxiao.timart.domain.model.unlock.LiftDirection.UP && delta >= condition.meters) ||
            (condition.direction == com.muxiao.timart.domain.model.unlock.LiftDirection.DOWN && -delta >= condition.meters)
        )
    LaunchedEffect(achieved) {
        if (achieved) onDone()
    }
    delta?.let {
        Text(
            text = "${it.toInt()} m",
            style = TimartType.titleSerif.copy(color = TimeGold),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---- 连点屏幕 ----

@Composable
internal fun TapChallenge(target: Int, onDone: () -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .background(DeepCharcoal, RoundedCornerShape(22.dp))
            .pointerInput(target) {
                detectTapGestures {
                    taps++
                    if (taps >= target) onDone()
                }
            }
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${taps.coerceAtMost(target)} / $target",
            style = TimartType.titleSerif.copy(color = TimeGold),
        )
    }
}

// ---- 当场爬楼（气压差 · 会话内净爬升） ----

@Composable
internal fun ClimbChallenge(floors: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    var climbedMeters by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        var minAlt: Float? = null
        var maxAlt: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_PRESSURE) return
                val pressure = event.values.firstOrNull() ?: return
                val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
                minAlt = minAlt?.coerceAtMost(alt) ?: alt
                maxAlt = maxOf(maxAlt ?: alt, alt)
                val climbed = (maxAlt - minAlt).toInt()
                climbedMeters = climbed
                if (climbed >= floors * METERS_PER_FLOOR) onDone()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose { sm?.unregisterListener(listener) }
    }
    ProgressText(progress = climbedMeters.coerceAtMost(floors * METERS_PER_FLOOR), target = floors * METERS_PER_FLOOR, suffix = " m")
}

private const val METERS_PER_FLOOR = 3

// ---- 语音口令（系统语音识别，无 RECORD_AUDIO 前置） ----

@Composable
internal fun VoiceChallenge(onAnswer: (String) -> Unit) {
    val L = LocalStrings.current
    var unavailable by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) onAnswer(text)
    }
    Button(
        onClick = {
            runCatching {
                launcher.launch(
                    Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                    },
                )
            }.onFailure { unavailable = true }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = TimeGold,
            contentColor = DeepCharcoal,
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth(),
    ) {
        Text(text = L.confirm, style = TimartType.caption)
    }
    if (unavailable) {
        Text(
            text = L.condDeviceUnsupported,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ---- 扫二维码（系统相机拍照 → zxing 解码；不保存） ----

@Composable
internal fun ScanQrChallenge(condition: UnlockCondition.ScanQr, onAnswer: (String) -> Unit) {
    val L = LocalStrings.current
    var failed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null || done) return@rememberLauncherForActivityResult
        val decoded = decodeQrText(bitmap)
        if (decoded == null) {
            failed = true
            return@rememberLauncherForActivityResult
        }
        // 内容一致即通过；未绑定内容时任意可解码的码通过
        val expected = condition.expectedPayload
        if (expected == null || decoded.trim() == expected.trim()) {
            done = true
            onAnswer(decoded)
        } else {
            failed = true
        }
    }
    if (done) {
        DoneTagPublic()
    } else {
        Button(
            onClick = { launcher.launch(null) },
            colors = ButtonDefaults.buttonColors(
                containerColor = TimeGold,
                contentColor = DeepCharcoal,
            ),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
        ) {
            Text(text = L.challengePhotoStart, style = TimartType.caption)
        }
        if (failed) {
            Text(
                text = L.challengeNfcMismatch,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** bitmap → QR 解码文本（zxing core，纯 Java；失败返回 null） */
private fun decodeQrText(bitmap: android.graphics.Bitmap): String? = runCatching {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
    val bitmap2 = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
    val hints = mapOf(com.google.zxing.DecodeHintType.TRY_HARDER to true)
    com.google.zxing.qrcode.QRCodeReader().decode(bitmap2, hints).text
}.getOrNull()

// ---- 算力挑战（SHA-256 前导零；主线程分段计算保持响应） ----

@Composable
internal fun PowChallenge(condition: UnlockCondition.ProofOfWork, onAnswer: (String) -> Unit) {
    var tried by remember { mutableLongStateOf(0L) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(condition.challengeId) {
        val prefix = "0".repeat(condition.difficulty)
        var nonce = 0L
        while (isActive && !done) {
            repeat(POW_BATCH) {
                val candidate = nonce.toString()
                if (UnlockJudgeUseCase.sha256Hex("${condition.challengeId}:$candidate").startsWith(prefix)) {
                    done = true
                    onAnswer(candidate)
                    return@LaunchedEffect
                }
                nonce++
            }
            tried = nonce
            delay(1.milliseconds)
        }
    }
    ProgressText(progress = tried.coerceAtMost(999_999L).toInt(), target = 999_999, suffix = "+")
}

private const val POW_BATCH = 400

// ---- 共用小件 ----

@Composable
private fun ProgressText(progress: Int, target: Int, suffix: String = "") {
    Text(
        text = "$progress / $target$suffix",
        style = TimartType.titleSerif.copy(color = TimeGold),
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun DoneTagPublic() {
    val L = LocalStrings.current
    Text(
        text = L.condSatisfiedTag,
        style = TimartType.caption,
        color = TimeGold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

// ---- 声音纪念（储备池 v7）：当场录音 → 当场回放即删，不保存 ----

@Composable
internal fun VoiceKeepsakeChallenge(onDone: () -> Unit) {
    val L = LocalStrings.current
    val context = LocalContext.current
    var phase by remember { mutableIntStateOf(0) } // 0 待开始 / 1 录音中 / 2 已录成
    var permDenied by remember { mutableStateOf(false) }
    var recorderRef by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var playerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val tempFile = remember { java.io.File(context.cacheDir, "timart_challenge_voice.m4a") }

    fun startRecording() {
        runCatching {
            tempFile.delete()
            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") android.media.MediaRecorder()
            }
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(tempFile.absolutePath)
            recorder.prepare()
            recorder.start()
            recorderRef = recorder
            phase = 1
        }.onFailure { permDenied = true }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else permDenied = true
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorderRef?.stop() }
            recorderRef?.release()
            playerRef?.release()
            tempFile.delete()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = L.challengeVoiceKeepsakeHint,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        when (phase) {
            0 -> Button(
                onClick = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) startRecording() else {
                        permLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TimeGold, contentColor = DeepCharcoal),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(L.voiceKeepsakeRecord) }
            1 -> Text(
                text = L.voiceKeepsakeStop,
                style = TimartType.body.copy(color = TimeGold),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            runCatching { recorderRef?.stop() }
                            recorderRef?.release()
                            recorderRef = null
                            phase = 2
                        }
                    },
            )
            else -> Row {
                Button(
                    onClick = {
                        runCatching {
                            playerRef?.release()
                            val player = android.media.MediaPlayer()
                            player.setDataSource(tempFile.absolutePath)
                            player.prepare()
                            player.setOnCompletionListener { it.release() }
                            player.start()
                            playerRef = player
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepCharcoal, contentColor = TimeGold),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(L.voiceKeepsakeReplay) }
                Button(
                    onClick = {
                        tempFile.delete()
                        onDone()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TimeGold, contentColor = DeepCharcoal),
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                ) { Text(L.voiceKeepsakeDone) }
            }
        }
    }
}

// ---- 呐喊挑战（储备池 v7）：麦克风振幅，持续响亮 N 秒 ----

@Composable
internal fun ShoutChallenge(targetSeconds: Int, onDone: () -> Unit) {
    val L = LocalStrings.current
    val context = LocalContext.current
    var loudMs by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    var recorderRef by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    val tempFile = remember { java.io.File(context.cacheDir, "timart_shout_probe.m4a") }

    fun startProbe() {
        runCatching {
            tempFile.delete()
            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") android.media.MediaRecorder()
            }
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(tempFile.absolutePath)
            recorder.prepare()
            recorder.start()
            recorderRef = recorder
            running = true
        }
    }
    fun stopProbe() {
        runCatching { recorderRef?.stop() }
        recorderRef?.release()
        recorderRef = null
        running = false
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startProbe()
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var lastLoud = true
        while (isActive && running) {
            delay(100.milliseconds)
            val amplitude = runCatching { recorderRef?.maxAmplitude ?: 0 }.getOrDefault(0)
            val isLoud = amplitude >= SHOUT_AMPLITUDE_THRESHOLD
            loudMs = if (isLoud) {
                if (lastLoud) loudMs + 100 else 100
            } else {
                0L
            }
            lastLoud = isLoud
            if (loudMs >= targetSeconds * 1000L) {
                stopProbe()
                tempFile.delete()
                onDone()
                break
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            stopProbe()
            tempFile.delete()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = L.challengeShoutHint,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (running) {
            ProgressText(
                progress = (loudMs / 1000L).toInt().coerceAtMost(targetSeconds),
                target = targetSeconds,
            )
        } else {
            Button(
                onClick = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) startProbe() else {
                        permLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TimeGold, contentColor = DeepCharcoal),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(L.voiceKeepsakeRecord) }
        }
    }
}

/** 呐喊挑战的「响亮」门槛（MediaRecorder maxAmplitude 0–32767 的经验阈值） */
private const val SHOUT_AMPLITUDE_THRESHOLD = 5000
