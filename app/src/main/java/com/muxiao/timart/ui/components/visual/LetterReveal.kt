package com.muxiao.timart.ui.components.visual

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxiao.timart.l10n.LocalStrings
import kotlinx.coroutines.isActive
import kotlin.math.sin

/**
 * 信笺文字显现效果（对应动效手册：Typewriter / Text Scramble / Blur Reveal / Split Text Wave）：
 * 每次进入阅读随机抽取一种，标题获得完整效果，正文以同族节奏跟进——
 * 打字机正文逐字敲出；扰乱正文按段淡入（长文逐字扰乱成本过高）；
 * 模糊正文整段由虚化对焦；波浪标题逐字起伏后归位、正文按段淡入。
 *
 * - 效果只播一次（进入组合启动，播完静止，无循环）；
 * - 模糊依赖 RenderEffect（API 31+），低版本自动降级为纯透明度对焦；
 * - mode = true 播放 / false 停在初始 / null 直接落成完成态（消散重放等静态场景）。
 */
enum class LetterRevealEffect { TYPEWRITER, SCRAMBLE, BLUR, WAVE }

/** 上一次抽中的效果（进程级记忆）：连开信笺时至少换一种，避免连续同名被感知为"固定" */
private var lastRolledEffect: LetterRevealEffect? = null

/** 抽取本次显现效果：从上次效果之外的 3 种中随机（每次打开必然可感知地变化） */
private fun rollRevealEffect(): LetterRevealEffect {
    val pool = LetterRevealEffect.entries.filter { it != lastRolledEffect }
    return pool.random().also { lastRolledEffect = it }
}

/**
 * 标题显现行数上限与超长处理：信笺卡片固定两行 + 省略号。
 * 原本由 RevealTitle 参数传入，但全项目唯一调用点恒传同一组值，故下沉为常量。
 */
private const val TITLE_MAX_LINES = 2
private val TITLE_OVERFLOW = TextOverflow.Ellipsis

/** 扰乱字符池（块状/线状字形，视觉上更像未解码的尘粒） */
private const val SCRAMBLE_POOL = "█▓▒░<>/\\[]{}#*+=-·"

/** 显现总时长（标题在前 32% 完成，正文随后跟进）；标题追求"立刻可读"，总长从 2200ms 收紧 */
private const val REVEAL_TOTAL_MS = 1500L

/** 标题显现窗口（= REVEAL_TOTAL_MS × 0.32，即标题进度走完 0→1 的实际毫秒数） */
private const val TITLE_WINDOW_MS = REVEAL_TOTAL_MS * 0.32f

/**
 * WAVE 波浪的单字周期（A9 固定时长制）：每个字的升起-归位时长恒定，
 * 不随标题长度变化；字与字的错峰间隔按剩余窗口自适应收紧，总时长恒等于标题窗口。
 */
private const val WAVE_CHAR_CYCLE_MS = 300f

class LetterRevealState internal constructor(
    val effect: LetterRevealEffect,
    val titleP: Float,
    val bodyP: Float,

    /** 标题窗口内已流逝的毫秒（0..TITLE_WINDOW_MS）：WAVE 固定时长制的时钟 */
    val titleMs: Float,
)

/**
 * 每次进入组合（= 每次打开信笺）重抽显现效果，且不与上一次打开重复（四种至少轮换可感知）。
 *
 * @param mode true = 播放显现动画；false = 停在初始（内容未就绪）；null = 直接完成态
 */
@Composable
fun rememberLetterReveal(mode: Boolean?): LetterRevealState {
    val effect = remember { rollRevealEffect() }
    val titleRaw = remember { mutableFloatStateOf(0f) }
    val bodyRaw = remember { mutableFloatStateOf(0f) }
    var titleMs by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(mode) {
        if (mode == null) {
            titleRaw.floatValue = 1f
            bodyRaw.floatValue = 1f
            titleMs = TITLE_WINDOW_MS
            return@LaunchedEffect
        }
        if (!mode) {
            titleRaw.floatValue = 0f
            bodyRaw.floatValue = 0f
            titleMs = 0f
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (isActive) {
            var ms = 0L
            withFrameNanos { frame -> ms = (frame - startNanos) / 1_000_000L }
            val overall = (ms / REVEAL_TOTAL_MS.toFloat()).coerceIn(0f, 1f)
            titleRaw.floatValue = (overall / 0.32f).coerceIn(0f, 1f)
            bodyRaw.floatValue = ((overall - 0.32f) / 0.68f).coerceIn(0f, 1f)
            titleMs = ms.toFloat().coerceAtMost(TITLE_WINDOW_MS)
            if (ms >= REVEAL_TOTAL_MS) break
        }
    }

    return LetterRevealState(effect, titleRaw.floatValue, bodyRaw.floatValue, titleMs)
}

/** 标题显现（四种效果完整版） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RevealTitle(
    text: String,
    state: LetterRevealState,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    when (state.effect) {
        LetterRevealEffect.TYPEWRITER -> {
            val visible = (state.titleP * text.length).toInt().coerceIn(0, text.length)
            Text(
                text.take(visible),
                style = style,
                color = color,
                maxLines = TITLE_MAX_LINES,
                overflow = TITLE_OVERFLOW,
                modifier = modifier,
            )
        }

        LetterRevealEffect.SCRAMBLE -> {
            val sb = remember(text) { StringBuilder(text.length) }
            val rnd = remember(text) { java.util.Random() }
            val revealIdx = state.titleP * text.length
            sb.setLength(0)
            for (i in text.indices) {
                sb.append(if (i < revealIdx) text[i] else SCRAMBLE_POOL[rnd.nextInt(SCRAMBLE_POOL.length)])
            }
            Text(
                sb.toString(),
                style = style,
                color = color,
                maxLines = TITLE_MAX_LINES,
                overflow = TITLE_OVERFLOW,
                modifier = modifier,
            )
        }

        LetterRevealEffect.BLUR -> {
            val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            // 模糊半径量化到整 dp：减少 RenderEffect 每帧重建（性能）
            val blurMod = if (canBlur) Modifier.blur(((1f - state.titleP) * 9f).toInt().dp) else Modifier
            Text(
                text = text,
                style = style,
                color = lerp(color.copy(alpha = 0.15f), color, state.titleP),
                maxLines = TITLE_MAX_LINES,
                overflow = TITLE_OVERFLOW,
                modifier = modifier.then(blurMod),
            )
        }

        LetterRevealEffect.WAVE -> {
            // A9 固定时长制：单字升起-归位周期恒定（WAVE_CHAR_CYCLE_MS），字与字按
            // 剩余窗口自适应错峰（stagger = (窗口 − 周期) / (n − 1)），总时长恒等于
            // 标题窗口——长标题不再出现"每字被闪过"的变速感，波前经过时升起再归位
            FlowRow(modifier = modifier) {
                val len = text.length.coerceAtLeast(1)
                val stagger = if (len <= 1) {
                    0f
                } else {
                    ((TITLE_WINDOW_MS - WAVE_CHAR_CYCLE_MS) / (len - 1)).coerceAtLeast(0f)
                }
                text.forEachIndexed { i, ch ->
                    val charP = ((state.titleMs - i * stagger) / WAVE_CHAR_CYCLE_MS).coerceIn(0f, 1f)
                    if (ch == ' ') {
                        Text(" ", style = style, color = color)
                    } else {
                        Text(
                            text = ch.toString(),
                            style = style,
                            color = color.copy(alpha = charP),
                            modifier = Modifier.graphicsLayer {
                                translationY = -sin(charP * Math.PI).toFloat() * 6.dp.toPx()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 正文显现（打字机逐字 / 其余按段错峰淡入；未就绪时低亮骨架行） */
@Composable
fun RevealBody(
    paragraphs: List<String>,
    contentReady: Boolean,
    state: LetterRevealState,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    emptyText: String? = null,
) {
    if (!contentReady) {
        repeat(4) { row ->
            Box(
                modifier = modifier
                    .padding(top = if (row == 0) 0.dp else 10.dp)
                    .fillMaxWidth(if (row == 3) 0.6f else 1f)
                    .height(10.dp)
                    .background(color.copy(alpha = 0.10f), androidx.compose.foundation.shape.RoundedCornerShape(5.dp)),
            )
        }
        return
    }

    val paras = paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
        .ifEmpty { listOf(emptyText ?: LocalStrings.current.letterNoText) }
    when (state.effect) {
        LetterRevealEffect.TYPEWRITER -> {
            val total = paras.sumOf { it.length }.coerceAtLeast(1)
            var visible = (state.bodyP * total).toInt()
            paras.forEachIndexed { i, para ->
                val take = visible.coerceIn(0, para.length)
                visible -= para.length
                if (take > 0 || (i == 0 && state.bodyP > 0f)) {
                    Text(
                        text = para.take(take),
                        style = style,
                        color = color,
                        modifier = modifier.let { if (i > 0) it.padding(top = 18.dp) else it },
                    )
                }
            }
        }

        else -> {
            paras.forEachIndexed { i, para ->
                val n = paras.size
                val paraP = (state.bodyP * (n + 1.5f) - i).coerceIn(0f, 1f)
                Text(
                    text = para,
                    style = style,
                    color = color.copy(alpha = paraP),
                    modifier = modifier
                        .let { if (i > 0) it.padding(top = 18.dp) else it }
                        .graphicsLayer { translationY = (1f - paraP) * 8.dp.toPx() },
                )
            }
        }
    }
}
