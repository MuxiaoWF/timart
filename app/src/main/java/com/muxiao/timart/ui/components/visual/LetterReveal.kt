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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * 信笺显现时钟（稳定标识，跨重组持有进度状态；由 [rememberLetterReveal] / [rememberRevealClock] 创建）：
 * - 快照 getter（titleP/bodyP）在组合期读取 = 选择性重组（打字机/扰乱逐帧改文本，必须重组）；
 * - raw 状态字段供 WAVE/BLUR/正文淡入在 graphicsLayer / drawBehind **层相位**内读取——
 *   波形/对焦/段淡入不再逐帧重组（显现期的逐帧重组是信笺开卡时刻最大的掉帧源）；
 * - [startNanos] 支持揭封 hold 期起笔 + CONTENT 卡片接管同一时钟：交接零跳变，
 *   hold 前跳过则时钟未起跑，由 CONTENT 在交接时起跑，两条路径语义自动一致。
 * 每次打开信笺新建（效果重抽 + 进度归零）。
 */
@Stable
class LetterRevealClock internal constructor(
    val effect: LetterRevealEffect,
) {
    internal val titlePState = mutableFloatStateOf(0f)
    internal val bodyPState = mutableFloatStateOf(0f)
    internal val titleMsState = mutableFloatStateOf(0f)

    /** 起跑帧时间戳（0 = 未起跑）：由首个 mode=true 的驱动方写入 */
    internal var startNanos: Long = 0L

    /** 标题进度 0..1（组合期读取会订阅重组；层相位请直读 [titlePState]） */
    val titleP: Float get() = titlePState.floatValue

    /** 正文进度 0..1 */
    val bodyP: Float get() = bodyPState.floatValue

}

/**
 * 新建独立显现时钟（每次进入组合 = 每次打开信笺重抽效果，且不与上一次打开重复、四种至少轮换可感知）。
 * 揭封 hold 期与 CONTENT 卡片共享同一时钟时，由 DetailScreen 持有并分别传入两侧。
 */
@Composable
fun rememberRevealClock(): LetterRevealClock = remember { LetterRevealClock(rollRevealEffect()) }

/**
 * 驱动显现时钟（进入组合启动，播完静止，无循环）。
 *
 * @param mode true = 播放显现动画；false = 停在初始（内容未就绪）；null = 直接完成态（消散重放等静态场景）
 * @param clock 外部共享时钟（揭封 hold 交接重叠用）：提供时不重抽效果，mode=true 时起跑/续走同一时间轴
 */
@Composable
fun rememberLetterReveal(
    mode: Boolean?,
    clock: LetterRevealClock? = null,
): LetterRevealClock {
    val resolved = clock ?: remember { LetterRevealClock(rollRevealEffect()) }
    LaunchedEffect(resolved, mode) {
        if (mode == null) {
            resolved.titlePState.floatValue = 1f
            resolved.bodyPState.floatValue = 1f
            resolved.titleMsState.floatValue = TITLE_WINDOW_MS
            return@LaunchedEffect
        }
        if (!mode) {
            resolved.titlePState.floatValue = 0f
            resolved.bodyPState.floatValue = 0f
            resolved.titleMsState.floatValue = 0f
            return@LaunchedEffect
        }
        // 揭封 hold 已起跑则续走（不重置进度）；hold 前跳过则时钟未起跑，在此起跑
        if (resolved.startNanos == 0L) {
            resolved.startNanos = withFrameNanos { it }
        }
        while (isActive) {
            var ms = 0L
            withFrameNanos { frame -> ms = (frame - resolved.startNanos) / 1_000_000L }
            val overall = (ms / REVEAL_TOTAL_MS.toFloat()).coerceIn(0f, 1f)
            resolved.titlePState.floatValue = (overall / 0.32f).coerceIn(0f, 1f)
            resolved.bodyPState.floatValue = ((overall - 0.32f) / 0.68f).coerceIn(0f, 1f)
            resolved.titleMsState.floatValue = ms.toFloat().coerceAtMost(TITLE_WINDOW_MS)
            if (ms >= REVEAL_TOTAL_MS) break
        }
    }

    return resolved
}

/** 标题显现（四种效果完整版） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RevealTitle(
    text: String,
    state: LetterRevealClock,
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
            // A10：模糊半径量化 + derivedStateOf——仅整数 dp 档位变化时重组（全程 ≤10 次）；
            // 对焦 alpha 改走 graphicsLayer 层相位读取，titleP 不再逐帧进入组合。
            // API < 31 无 RenderEffect：blurDp 恒 0 不模糊，降级为纯透明度对焦
            val blurDp by remember { derivedStateOf { ((1f - state.titlePState.floatValue) * 9f).toInt() } }
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = TITLE_MAX_LINES,
                overflow = TITLE_OVERFLOW,
                modifier = modifier
                    .then(if (canBlur) Modifier.blur(blurDp.dp) else Modifier)
                    .graphicsLayer { alpha = 0.15f + 0.85f * state.titlePState.floatValue },
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
                    if (ch == ' ') {
                        Text(" ", style = style, color = color)
                    } else {
                        Text(
                            text = ch.toString(),
                            style = style,
                            color = color,
                            modifier = Modifier.graphicsLayer {
                                // A10：单字进度在层相位内读快照状态——波形起落只更新 layer，不逐帧重组
                                val charP = ((state.titleMsState.floatValue - i * stagger) / WAVE_CHAR_CYCLE_MS)
                                    .coerceIn(0f, 1f)
                                alpha = charP
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
    state: LetterRevealClock,
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

    val fallbackText = emptyText ?: LocalStrings.current.letterNoText
    val paras = remember(paragraphs, fallbackText) {
        paragraphs.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(fallbackText) }
    }
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
                Text(
                    text = para,
                    style = style,
                    color = color,
                    modifier = modifier
                        .let { if (i > 0) it.padding(top = 18.dp) else it }
                        .graphicsLayer {
                            // A10：段进度层相位读取（段淡入 + 上浮），显现期免逐帧重组
                            val paraP = (state.bodyPState.floatValue * (n + 1.5f) - i).coerceIn(0f, 1f)
                            alpha = paraP
                            translationY = (1f - paraP) * 8.dp.toPx()
                        },
                )
            }
        }
    }
}
