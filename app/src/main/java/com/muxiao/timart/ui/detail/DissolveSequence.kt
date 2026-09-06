package com.muxiao.timart.ui.detail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.muxiao.timart.ui.components.particle.ParticleCanvas
import com.muxiao.timart.ui.components.particle.ParticleEngine
import com.muxiao.timart.ui.components.particle.ParticlePreset
import com.muxiao.timart.ui.theme.DustAsh
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * DISSOLVE 消散序列（架构 §2.16 / §8.1，0–1000ms 沿轨道散逸）：
 * - 粒子由共享引擎 DISSOLVE 预设承担（透明画布叠在信笺之上，内容本身由父层做淡出）；
 * - 任意点击 / 1s 后 → onFinished 落 ARCHIVED（业务销毁早已启动，此处纯视觉收尾）。
 */
@Composable
fun DissolveSequence(
    engine: ParticleEngine,
    onFinished: () -> Unit,
) {
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val finished = remember { AtomicBoolean(false) }

    fun finish() {
        if (finished.compareAndSet(false, true)) onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            // 点击跳过：直接落尘迹态（无静默销毁路径的对称面：无静默跳过销毁反馈）
            .pointerInput(Unit) { detectTapGestures { finish() } },
    ) {
        ParticleCanvas(
            engine = engine,
            modifier = Modifier.fillMaxSize(),
            owner = ParticleEngine.OWNER_DETAIL,
        )
    }

    LaunchedEffect(canvasSize) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        engine.fire(
            preset = ParticlePreset.DISSOLVE,
            anchorX = canvasSize.width / 2f,
            anchorY = canvasSize.height * 0.42f,
            anchorRadius = with(density) { 90.dp.toPx() },
            colorArgb = DustAsh.toArgb(),
        )
        delay(1000L.milliseconds)
        finish()
    }
}
