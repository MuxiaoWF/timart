package com.muxiao.timart.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import com.muxiao.timart.domain.model.Lang
import com.muxiao.timart.domain.model.WeatherType
import com.muxiao.timart.domain.usecase.ReadCapsuleUseCase
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.l10n.stringsFor
import com.muxiao.timart.ui.components.visual.PaperLetterCard
import com.muxiao.timart.ui.components.visual.PaperStyle
import com.muxiao.timart.ui.components.visual.RevealBody
import com.muxiao.timart.ui.components.visual.RevealTitle
import com.muxiao.timart.ui.components.visual.rememberLetterReveal
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.GlowGold
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkSecondary

import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.utils.format.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 状态 C：UNLOCKED 已解锁（PRD §3.4.4 状态 C / 设计稿 08）：
 * - 左上悬浮返回按钮（与系统返回同路，autoDestroy 未决策时先弹「销毁/保留」）；
 * - 悬浮信笺（微暖纸面 #F2EDE3，弱卡片感：无阴影、小圆角）；
 * - 标题 → 正文（长行高充足留白）→ 图片自然满宽；
 * - 封存凭证（创建/解锁时间、天气快照、标签、备注）置于内容末尾，视觉权重低于正文；
 * - autoDestroyAfterRead：底部唯一主动作「完成阅读并销毁」+ 副文案；
 * - 海报导出入口（次级文字按钮，T15 接 FileProvider 分享）。
 */
@Composable
fun UnlockedLetterView(
    content: ReadCapsuleUseCase.CapsuleContent?,
    unlockedAt: Long?,
    autoDestroyAfterRead: Boolean,
    modifier: Modifier = Modifier,
    loading: Boolean = false,

    /** autoDestroy 主动作（null = 不展示，如消散重放） */
    onCompleteRead: (() -> Unit)? = null,

    /** 看后销毁 → 保留 的切换（仅 autoDestroy 卡片显示；持久化写库） */
    onKeep: (() -> Unit)? = null,

    /** 保留胶囊的销毁入口（确认弹窗由外层承担；仅非 autoDestroy 卡片显示） */
    onDestroy: (() -> Unit)? = null,

    /** 海报导出（T15 接线 FileProvider 分享；null = 隐藏） */
    onPoster: (() -> Unit)? = null,

    /** 明文导出（N13：SAF 存 .txt；null = 隐藏。确认弹窗由外层承担） */
    onExportText: (() -> Unit)? = null,

    /** 左上返回按钮（null = 不展示，如消散重放） */
    onBack: (() -> Unit)? = null,

    /** 文字显现效果（打字机/扰乱/模糊/波浪 随机一种）；消散重放传 false */
    animateText: Boolean = true,

    /**
     * 内容未解密就绪时的标题兜底（Capsule.title 是明文，不经加密）。
     * 揭封路径落 CONTENT 时解密可能仍在跑，用它避免标题从真实值退回"…"。
     */
    titleHint: String? = null,

    /** 卡片高度实测回调（揭封舞台据此生成同高双翼，交接无几何差） */
    onCardHeightChanged: ((Int) -> Unit)? = null,

    /** 陀螺仪视差传感器（设置页开关开启时卡片 3D 倾斜） */
    parallax: com.muxiao.timart.utils.sensor.ParallaxSensor? = null,

    /** 声音留言（体验储备池 §1）：有无语音 / 播放态 / 播放开关（null = 不展示播放键，如消散重放） */
    voiceAvailable: Boolean = false,
    voicePlaying: Boolean = false,
    onToggleVoice: (() -> Unit)? = null,

    /** 信纸样式（体验储备池 §1；封存时选定，meta `capsule.paper.<id>`，0 = 原纸） */
    paperStyle: Int = 0,

    /** 回信（体验储备池 §5）：已写文本 / 写回信入口（null = 不展示，如消散重放） */
    reply: String? = null,
    onWriteReply: (() -> Unit)? = null,

    /** 火漆印章（N19）：非 0 时在信笺底部呈现落款印章 */
    sealStyle: Int = 0,

    /** 回信转新胶囊（N5）：已写回信后提供「封存进新胶囊」入口；预填与导航由外层承担 */
    onReplyToCapsule: (() -> Unit)? = null,

    /** 多章节信件（N10）：下一章提示（可揭示 = 引导句按钮 / 未到期 = 「N 天后可读」纯文本；null = 单章信或无后续章） */
    chapterHint: String? = null,
    onRevealNextChapter: (() -> Unit)? = null,

    /** 拼图分组（体验储备池 §3）：进度短句（如「拼图 2/3」）；就绪后可开合信视图（handler null = 不展示） */
    puzzleStatus: String? = null,
    puzzleReady: Boolean = false,
    onOpenPuzzle: (() -> Unit)? = null,
) {
    val L = LocalStrings.current
    // 信纸样式决定纸色与墨色（全信笺文本统一取 ink，噪点同墨）
    val paper = PaperStyle.of(paperStyle)
    val ink = paper.ink
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 有返回按钮时卡片整体下移，避免与左上悬浮按钮重叠
                // （B1：水平边距与顶部下移量走 PaperCardGeometry 唯一来源，与揭封舞台同值）
                .padding(
                    horizontal = PaperCardGeometry.HorizontalMargin,
                    vertical = if (onBack != null) PaperCardGeometry.TopWithBackButton else 40.dp,
                ),
        ) {
            PaperLetterCard(
                style = paper,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { onCardHeightChanged?.invoke(it.height) }
                    .graphicsLayer {
                        // 陀螺仪 3D 倾斜（draw/layer 相位读取，不触发重组）
                        if (parallax != null && RuntimeSettings.gyroEnabled) {
                            val t = parallax.tilt
                            rotationX = -t.y * 5f
                            rotationY = t.x * 5f
                            cameraDistance = 40f * this.density
                        }
                    },
            ) {
                Column(modifier = Modifier.padding(horizontal = 26.dp, vertical = 30.dp)) {
                    val reveal = rememberLetterReveal(
                        // content 就绪 → 播效果；未就绪但有 titleHint → 立即以 titleHint 播效果
                        //（标题是明文且与 content.title 同值，就绪后无缝衔接，不再"先完整→再重播"）
                        //；否则停在初始
                        mode = if (!animateText) {
                            null
                        } else if (content != null && !loading) {
                            true
                        } else if (titleHint != null) {
                            true
                        } else {
                            false
                        },
                    )

                    // 标题（随机显现效果）
                    RevealTitle(
                        text = content?.title ?: titleHint ?: "…",
                        state = reveal,
                        style = TimartType.titleSerif.copy(fontSize = 26.sp, lineHeight = 36.sp),
                        color = ink,
                    )

                    // 标题下金色划线：字符显现完成后从左扫入并保留（手账式短划线）
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth(0.34f)
                            .height(3.5.dp)
                            .drawBehind {
                                val lineP = (reveal.bodyP / 0.12f).coerceIn(0f, 1f)
                                if (lineP > 0f) {
                                    drawRect(
                                        color = TimeGold.copy(alpha = 0.9f),
                                        size = androidx.compose.ui.geometry.Size(size.width * lineP, size.height),
                                    )
                                }
                            },
                    )

                    // 元信息行：日期 · 城市 · 天气温度
                    content?.let { c ->
                        Text(
                            text = metaLineOf(c),
                            style = TimartType.caption,
                            color = ink.copy(alpha = 0.55f),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    // 声音留言播放键（体验储备池 §1）：信笺上方浮现，播完/停止即回初始态
                    if (voiceAvailable && onToggleVoice != null) {
                        Text(
                            text = if (voicePlaying) L.voiceStopPlaying else L.voicePlay,
                            style = TimartType.caption,
                            color = TimeGold,
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .clickable(onClick = onToggleVoice),
                        )
                    }

                    // 拼图进度与合信视图入口（体验储备池 §3）
                    if (puzzleStatus != null) {
                        Text(
                            text = puzzleStatus,
                            style = TimartType.caption,
                            color = ink.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        if (puzzleReady && onOpenPuzzle != null) {
                            Text(
                                text = L.puzzleOpen,
                                style = TimartType.caption,
                                color = TimeGold,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clickable(onClick = onOpenPuzzle),
                            )
                        }
                    }

                    // 细分隔线
                    Box(
                        modifier = Modifier
                            .padding(top = 18.dp, bottom = 20.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ink.copy(alpha = 0.14f)),
                    )

                    // 正文（显现效果；未就绪时骨架行）
                    RevealBody(
                        paragraphs = content?.paragraphs ?: emptyList(),
                        contentReady = content != null && !loading,
                        state = reveal,
                        style = TimartType.body.copy(lineHeight = 30.sp),
                        color = ink,
                    )

                    if (content != null && !loading) {
                        // 图片与凭证随正文显现节奏淡入（占位不跳动）
                        Column(modifier = Modifier.graphicsLayer { alpha = reveal.bodyP }) {
                            // 图片自然满宽落位（不做独立卡片；EXIF 摆正）
                            content.images.forEachIndexed { _, bytes ->
                                val bitmap by produceState<ImageBitmap?>(initialValue = null, bytes) {
                                    value = withContext(Dispatchers.Default) {
                                        com.muxiao.timart.utils.format.ImageDecode.decodeOriented(bytes, maxDimension = 2048)
                                            ?.asImageBitmap()
                                    }
                                }
                                bitmap?.let { bmp ->
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = null,
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .padding(top = 22.dp)
                                            .fillMaxWidth()
                                            .aspectRatio(
                                                (bmp.width.toFloat() / bmp.height.toFloat())
                                                    .coerceIn(0.6f, 2.2f),
                                            )
                                            .clip(RoundedCornerShape(6.dp)),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(34.dp))

                            // 封存凭证（元信息尾注，视觉权重低于正文）
                            Text(
                                text = L.voucherTitle,
                                style = TimartType.caption,
                                color = ink.copy(alpha = 0.45f),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            VoucherRow(label = L.createdLabel, value = TimeFormatter.dateTime(content.createdAt), ink = ink)
                            unlockedAt?.let { VoucherRow(label = L.unlockedLabel, value = TimeFormatter.dateTime(it), ink = ink) }
                            content.snapshot?.let { snapshot ->
                                VoucherRow(
                                    label = L.weatherLabel,
                                    value = "${snapshot.cityName} · ${weatherNameOf(snapshot.weatherType, RuntimeSettings.resolvedLang)} ${snapshot.tempC.toInt()}°C",
                                    ink = ink,
                                )
                            }
                            if (content.tags.isNotEmpty()) {
                                VoucherRow(label = L.tagsLabel, value = content.tags.joinToString(" · "), ink = ink)
                            }
                            if (content.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = L.noteLabel,
                                    style = TimartType.caption,
                                    color = ink.copy(alpha = 0.45f),
                                )
                                Text(
                                    text = content.note,
                                    style = TimartType.body.copy(fontSize = 14.sp, lineHeight = 24.sp),
                                    color = ink.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (autoDestroyAfterRead && onCompleteRead != null) {
                // 看后销毁：唯一主动作 + 副文案 + 「改为保留」切换
                Button(
                    onClick = onCompleteRead,
                    enabled = !loading && content != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TimeGold,
                        contentColor = DeepCharcoal,
                        disabledContainerColor = DeepCharcoal.copy(alpha = 0.6f),
                        disabledContentColor = InkDisabled,
                    ),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(text = L.readAndDestroy, style = TimartType.body)
                }
                Text(
                    text = L.readDestroyDesc,
                    style = TimartType.caption,
                    color = InkDisabled,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                if (onKeep != null) {
                    TextButton(
                        onClick = onKeep,
                        colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = L.keepInstead, style = TimartType.caption)
                    }
                }
            } else if (!autoDestroyAfterRead && onDestroy != null) {
                // 保留胶囊：提供销毁入口（显式确认弹窗在外层，无静默销毁）
                TextButton(
                    onClick = onDestroy,
                    colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = L.destroyThisCapsule, style = TimartType.caption)
                }
            }

            // 海报导出（次级文字按钮，T15 接 FileProvider）
            if (onPoster != null) {
                TextButton(
                    onClick = onPoster,
                    enabled = !loading && content != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = GlowGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(text = L.makePoster, style = TimartType.caption)
                }
            }

            // 明文导出（N13）：仅已解锁内容可用；导出即脱离加密保护，确认弹窗在外层
            if (onExportText != null) {
                TextButton(
                    onClick = onExportText,
                    enabled = !loading && content != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = L.letterExportText, style = TimartType.caption)
                }
            }

            // 回信（体验储备池 §5）：一句附言绑定档案，销毁后留存于尘迹
            // 多章节信件（N10）：下一章提示（可揭示 → 引导按钮；未到期 → 纯文本预告）
            chapterHint?.let { hint ->
                if (onRevealNextChapter != null) {
                    TextButton(
                        onClick = onRevealNextChapter,
                        enabled = !loading,
                        colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                    ) {
                        Text(text = hint, style = TimartType.caption)
                    }
                } else {
                    Text(
                        text = hint,
                        style = TimartType.caption,
                        color = InkSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    )
                }
            }
            if (onWriteReply != null) {
                TextButton(
                    onClick = onWriteReply,
                    enabled = !loading && content != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = GlowGold),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (reply == null) L.replyWrite else L.replyEdit,
                        style = TimartType.caption,
                    )
                }
                reply?.let { written ->
                    Text(
                        text = "${L.replyLabel}：$written",
                        style = TimartType.caption,
                        color = InkSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    )
                }
                // 火漆印章落款（N19）：封存时选印的信笺底部呈现（无印不占位）
                if (com.muxiao.timart.ui.components.visual.WaxSealStyle.isValid(sealStyle)) {
                    com.muxiao.timart.ui.components.visual.WaxSeal(
                        style = sealStyle,
                        modifier = Modifier
                            .size(56.dp)
                            .padding(bottom = 6.dp),
                    )
                }
                // 回信转新胶囊（N5）：仅在已有回信时出现，正文预填即这句回信
                if (reply != null && onReplyToCapsule != null) {
                    TextButton(
                        onClick = onReplyToCapsule,
                        enabled = !loading,
                        colors = ButtonDefaults.textButtonColors(contentColor = GlowGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                    ) {
                        Text(text = L.replyToNewCapsule, style = TimartType.caption)
                    }
                }
            }
        }

        // 左上返回按钮（共享组件；卡片列在有按钮时下移 64dp，此处不与卡片重叠）
        if (onBack != null) {
            DetailBackButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

@Composable
private fun VoucherRow(label: String, value: String, ink: Color) {
    Row(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = label,
            style = TimartType.caption,
            color = ink.copy(alpha = 0.45f),
            modifier = Modifier.width(52.dp),
        )
        Text(
            text = value,
            style = TimartType.caption,
            color = ink.copy(alpha = 0.8f),
        )
    }
}

/** 元信息行："2027.08.12 · 东京 · 小雨 21°C"（缺省段自动省略） */
private fun metaLineOf(content: ReadCapsuleUseCase.CapsuleContent): String {
    val parts = mutableListOf(TimeFormatter.date(content.createdAt))
    content.snapshot?.let { snapshot ->
        parts += snapshot.cityName
        parts += "${weatherNameOf(snapshot.weatherType, RuntimeSettings.resolvedLang)} ${snapshot.tempC.toInt()}°C"
    }
    return parts.joinToString(" · ")
}

/** WeatherType → 中文短名（信笺凭证行与 UNSEAL 元信息行共用） */
internal fun weatherNameOf(type: WeatherType, lang: Lang): String {
    val L = stringsFor(lang)
    return when (type) {
        WeatherType.CLEAR -> L.wShortClear
        WeatherType.CLOUDY -> L.wShortCloudy
        WeatherType.FOG -> L.wShortFog
        WeatherType.DRIZZLE -> L.wShortDrizzle
        WeatherType.RAIN -> L.wShortRain
        WeatherType.SNOW -> L.wShortSnow
        WeatherType.THUNDER -> L.wShortThunder
    }
}
