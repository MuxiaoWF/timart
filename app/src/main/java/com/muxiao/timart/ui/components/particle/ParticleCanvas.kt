package com.muxiao.timart.ui.components.particle

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

/**
 * 引擎 ↔ Compose 桥接画布（架构 §2.18）：
 * - 首帧 onSizeChanged 后才 attach（引擎初始化延迟到有画布尺寸）；
 * - LaunchedEffect 帧循环：withFrameNanos 驱动 tick，同帧 draw（tick/draw 严格分离）；
 *   引擎热替换（档位切换）时重启循环并**用当前尺寸重新 attach**——
 *   onSizeChanged 只在尺寸变化时触发，不重启会让新引擎拿不到宽高、粒子聚在原点；
 * - tick 与 draw 失耦：draw 只在窗口存在其他逐帧失效源时才会重放。静态页面（写下/设置）
 *   没有失效源，引擎清场前的最后一帧会永远滞留（星屑冻在别的页面）——
 *   因此绘制相位读取 [drawTick] 订阅失效，tick 循环在有活动粒子时每帧写入；
 *   空池即静默不失效（静态页面零重绘开销）；
 * - [owner]：本画布归属页（OWNER_*）。循环型粒子（背景漂移/内流）只绘给宿主画布，
 *   非宿主页（默认 OWNER_NONE）只绘一次性粒子——切页重叠窗口不再出现他页尘盐残影；
 * - DisposableEffect 挂接宿主 lifecycle：ON_PAUSE → engine.pause()，ON_RESUME → engine.resume()；
 * - 离开组合时 detach 清场并移除生命周期观察（detach 不再动背景开关，归属权在 setBackground）。
 */
@Composable
fun ParticleCanvas(
    engine: ParticleEngine,
    modifier: Modifier = Modifier,
    owner: Int = ParticleEngine.OWNER_NONE,
) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var drawTick by remember { mutableLongStateOf(0L) }

    Canvas(
        modifier = modifier.onSizeChanged { size ->
            canvasSize = size
            engine.attach(size.width.toFloat(), size.height.toFloat(), density)
        },
    ) {
        // 读取失效源（订阅：tick 循环写入时触发本画布重绘）
        @Suppress("UNUSED_EXPRESSION") drawTick
        drawIntoCanvas { engine.draw(it.nativeCanvas, owner) }
    }

    DisposableEffect(engine, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> engine.pause()
                Lifecycle.Event.ON_RESUME -> engine.resume()
                else -> Unit
            }
        }
        val owner = view.findViewTreeLifecycleOwner()
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            engine.detach()
        }
    }

    LaunchedEffect(engine) {
        // 引擎热替换后用最近一次画布尺寸重新 attach（尺寸未变时 onSizeChanged 不会再触发）
        if (canvasSize != IntSize.Zero) {
            engine.attach(canvasSize.width.toFloat(), canvasSize.height.toFloat(), density)
        }
        var hadActive = engine.hasActiveParticles()
        while (true) {
            val frame = withFrameNanos { it }
            // tickAt 带同帧去重：多宿主画布同帧调用时引擎只推进一次（转场/翻页重叠窗口防倍速）
            engine.tickAt(frame)
            val active = engine.hasActiveParticles()
            // 有粒子：持续重绘；粒子刚被清空（切页/离场释放）：补画一帧空场擦除残影，随后静默
            if (active || hadActive) drawTick = frame
            hadActive = active
        }
    }
}
