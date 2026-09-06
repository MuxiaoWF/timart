package com.muxiao.timart.ui.create.seal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.components.particle.ParticleCanvas
import com.muxiao.timart.ui.components.particle.ParticleEngine
import com.muxiao.timart.ui.components.particle.ParticlePreset
import com.muxiao.timart.ui.components.visual.GlowOrb
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.GlowGold
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * ASSEMBLE 封存聚合全屏 overlay（架构 §2.15 / §8）：
 * 三段动画 0–350 聚拢 / 350–800 内光悬停 / 800–1200 成球（engine 内部时间轴）。
 * 播放期全屏吞手势禁编辑；完成回调（或 1.5s 兜底超时）才导航回时轨——
 * 保存本身不等待动画，动画失败也不阻塞（delay 兜底强返）。
 */
@Composable
fun AssembleOverlay(
    engine: ParticleEngine,
    onFinished: () -> Unit,
) {
    val L = LocalStrings.current
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val finished = remember { AtomicBoolean(false) }

    // 球体实测中心（换算到画布局部坐标）：粒子聚合锚点必须对准球心——
    // 球体与文案同列居中时，球心在画布几何中心上方（文案把列顶高），几何中心会让粒子收在半空
    var orbCenter by remember { mutableStateOf(Offset.Zero) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }

    // 幂等收尾：序列完成回调与兜底超时只走一次
    fun finishNow() {
        if (finished.compareAndSet(false, true)) {
            engine.onSequenceFinished = null
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal.copy(alpha = 0.97f))
            .onSizeChanged { canvasSize = it }
            .onGloballyPositioned { boxOrigin = it.positionInRoot() }
            // 吞掉全部点击（播放期禁编辑）
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // 粒子画布（attach 由 onSizeChanged 内部完成）
        ParticleCanvas(engine = engine, modifier = Modifier.fillMaxSize())

        // 静态光晕核心 + 文案（单主焦点 = 粒子序列本身，核心不做动画）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center),
        ) {
            GlowOrb(
                radius = 26.dp,
                coreColor = TimeGold,
                glowColor = GlowGold,
                glowAlpha = 0.5f,
                modifier = Modifier.onGloballyPositioned { coords ->
                    orbCenter = coords.positionInRoot() +
                        Offset(coords.size.width / 2f, coords.size.height / 2f)
                },
            )
            Text(
                text = L.assembleTitle,
                style = TimartType.titleSerif,
                color = InkPrimary,
                modifier = Modifier.padding(top = 22.dp),
            )
            Text(
                text = L.assembleDesc,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    // A7：就绪标志一次性触发（原 keys = canvasSize/orbCenter/boxOrigin——
    // onGloballyPositioned 异步置位会让 effect 重启 1–2 次，ASSEMBLE 序列被
    // pool.releaseAll 静默清场重来；改为 ready 布尔键，false→true 只触发一次）
    val ready = canvasSize != IntSize.Zero && orbCenter != Offset.Zero && boxOrigin != Offset.Zero
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        val anchorRadius = with(density) { 48.dp.toPx() }
        engine.onSequenceFinished = { finishNow() }
        // 锚点 = 球体实测中心换算到画布局部坐标（根坐标 − overlay 原点）
        engine.fire(
            preset = ParticlePreset.ASSEMBLE,
            anchorX = orbCenter.x - boxOrigin.x,
            anchorY = orbCenter.y - boxOrigin.y,
            anchorRadius = anchorRadius,
            colorArgb = ParticleEngine.TIME_GOLD,
            sequence = true,
        )
        // 兜底：1.5s 后无论序列是否结束都强返（engine 暂停/异常也不阻塞导航）
        delay(1500.milliseconds)
        finishNow()
    }
}
