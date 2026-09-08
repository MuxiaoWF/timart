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
 * DISSOLVE 消散序列（架构 §2.16 / §8.1）：
 * - 粒子由共享引擎 DISSOLVE 预设承担（透明画布叠在信笺之上，内容本身由父层做淡出）；
 * - 两幕编排：先等信笺淡出推进（~420ms，卡片残影已稀薄），再从**信笺卡片实际中心**
 *   发射散逸粒子（此前 t=0 即爆 + 锚点固定 h*0.42：卡片刚开始淡出、球体尚未出现时
 *   粒子就在屏幕中部的空白处提前播完，视觉上"无中生有"）；散逸 1s → 落 ARCHIVED。
 * - 任意点击 → onFinished 落 ARCHIVED（业务销毁早已启动，此处纯视觉收尾）。
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
        // 第一幕：信笺淡出先行——DetailScreen 的 700ms 淡出在此推进到 ~40% 残影，
        // 粒子出现时卡片已"正在消散"，因果衔接成立
        delay(420L.milliseconds)
        // 第二幕：锚点 = 信笺卡片实际中心（DISSOLVE 相位信笺无返回按钮 → 顶距 40dp；
        // 几何走 PaperCardGeometry 唯一来源，不再用屏幕比例位 h*0.42）
        val cardTop = with(density) { 40.dp.toPx() }
        val cardH = PaperCardGeometry.estimatedHeightPx(density, canvasSize.height.toFloat())
        val cardW = PaperCardGeometry.widthPx(density, canvasSize.width.toFloat())
        engine.fire(
            preset = ParticlePreset.DISSOLVE,
            anchorX = canvasSize.width / 2f,
            anchorY = cardTop + cardH / 2f,
            anchorRadius = maxOf(cardW, cardH) / 2f * 0.9f,
            colorArgb = DustAsh.toArgb(),
        )
        delay(1000L.milliseconds)
        finish()
    }
}
