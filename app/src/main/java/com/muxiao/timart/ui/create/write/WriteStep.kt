package com.muxiao.timart.ui.create.write

import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.components.visual.PaperStyle
import com.muxiao.timart.ui.components.visual.SectionHeader
import com.muxiao.timart.ui.create.CityPickerSheet
import com.muxiao.timart.ui.create.CityWeatherSection
import com.muxiao.timart.ui.create.CreateViewModel
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * 第一步「写下」（架构 §2.15，对齐设计稿 02）：
 * SurfaceRaise 大卡片：标题大字 + 无边框正文 + 0/2000 计数；
 * 照片条（系统 PhotoPicker 多选，旧机 ACTION_OPEN_DOCUMENT 回退，选中即复制字节入 VM 内存）；
 * 备注；底部金色 CTA「下一步：设置开启方式」。
 * 正文输入时在光标处发射输入飘粒（onSpark 根坐标回调，画布层由 CreateScreen 提供）。
 */
@Composable
fun WriteStep(
    vm: CreateViewModel,
    onSpark: (rootX: Float, rootY: Float) -> Unit,
    onNext: () -> Unit,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    val contentValid = vm.title.isNotBlank() || vm.content.isNotBlank()
    var showCityPicker by remember { mutableStateOf(false) }
    var showHandDraw by remember { mutableStateOf(false) }

    // ---- 声音留言（体验储备池 §1）：点按开始/停止，MediaRecorder AAC 落 cache 临时文件，
    //      停止后字节进 VM 内存（封存时才加密落盘），临时文件即删 ----
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    var voicePermDenied by remember { mutableStateOf(false) }
    val recorderRef = remember { java.util.concurrent.atomic.AtomicReference<android.media.MediaRecorder?>(null) }

    LaunchedEffect(recording) {
        while (recording) {
            delay(1_000.milliseconds)
            recordSeconds++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 离开页面未停止录音：放弃录制并释放
            if (recording) {
                stopVoiceRecord(vm, recorderRef, context.cacheDir, keep = false, seconds = recordSeconds)
                recording = false
            }
        }
    }

    fun startVoiceRecord(): Boolean {
        val temp = java.io.File(context.cacheDir, "timart_voice_record.m4a")
        runCatching { temp.delete() }
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.media.MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            android.media.MediaRecorder()
        }
        val ok = runCatching {
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(temp.absolutePath)
            recorder.prepare()
            recorder.start()
            true
        }.getOrDefault(false)
        if (!ok) {
            runCatching { recorder.release() }
            temp.delete()
            return false
        }
        recorderRef.set(recorder)
        recordSeconds = 0
        recording = true
        return true
    }

    fun stopAndKeep() {
        if (recording) {
            stopVoiceRecord(vm, recorderRef, context.cacheDir, keep = true, seconds = recordSeconds)
        }
        recording = false
    }

    val voicePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        voicePermDenied = !granted
        if (granted) startVoiceRecord()
    }

    // 正文光标坐标（根坐标系）：字段原点 + 光标矩形中心
    var bodyFieldOrigin by remember { mutableStateOf(Offset.Zero) }
    var bodyLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    /** 在正文光标处发射飘粒（布局为旧文本时按新文本长度钳制到末位） */
    fun sparkAtCaret(newTextLength: Int) {
        val layout = bodyLayout ?: return
        val text = layout.layoutInput.text
        if (text.isEmpty()) return
        val rect = layout.getCursorRect(newTextLength.coerceIn(0, text.length))
        onSpark(
            bodyFieldOrigin.x + rect.center.x,
            bodyFieldOrigin.y + rect.center.y,
        )
    }

    // 系统 PhotoPicker（无权限；API 33+）；旧机回退 ACTION_OPEN_DOCUMENT 多选
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(CreateViewModel.IMAGE_MAX),
    ) { uris ->
        // 字节读取在 IO（这里简化为回调线程读小图；上限 9 张）
        // 注：PhotoPicker 回调在主线程，uri 数量有限，读字节放 VM（内部 IO）
        vm.addImagesFromUris(uris)
    }
    val openDocuments = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        vm.addImagesFromUris(uris)
    }
    val useModernPicker = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // 信笺卡入场：页面转场落位前静置 70ms，再上浮淡入（与整页 scaleIn 错开，突出主角）
    val cardReveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70.milliseconds)
        cardReveal.animateTo(1f, tween(280, easing = LinearOutSlowInEasing))
    }
    // 标题下金色 hairline：卡落位后从左向右划出（呼应详情页信笺划线语言）
    val lineReveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(270.milliseconds)
        lineReveal.animateTo(1f, tween(300, easing = LinearOutSlowInEasing))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        SectionHeader(title = L.writeTitle, note = "CREATE 01")

        Text(
            text = L.writeNarrative,
            style = TimartType.body,
            color = InkSecondary,
            modifier = Modifier.padding(top = 14.dp),
        )

        // 信笺草稿大卡
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .graphicsLayer {
                    alpha = cardReveal.value
                    translationY = (1f - cardReveal.value) * 12.dp.toPx()
                }
                .background(SurfaceRaise, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text(text = L.titleLabel, style = TimartType.caption, color = InkSecondary)
            BasicTextField(
                value = vm.title,
                onValueChange = vm::updateTitle,
                singleLine = true,
                textStyle = TimartType.titleSerif.copy(color = InkPrimary),
                cursorBrush = SolidColor(TimeGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(lineReveal.value)
                    .padding(top = 8.dp)
                    .height(1.dp)
                    .background(TrackHairlineColor),
            )

            // 无边框正文（大面积留白；输入飘粒在光标处上飘）
            BasicTextField(
                value = vm.content,
                onValueChange = { v ->
                    vm.updateContent(v)
                    // 输入飘粒：250ms 节流（engine 内部实现；LOW 档或设置页关闭时 onSpark 内不发射）
                    sparkAtCaret(v.length)
                },
                onTextLayout = { bodyLayout = it },
                textStyle = TimartType.body.copy(color = InkPrimary, lineHeight = 26.sp),
                cursorBrush = SolidColor(TimeGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .height(280.dp)
                    .onGloballyPositioned { bodyFieldOrigin = it.localToRoot(Offset.Zero) },
            )
            Text(
                text = "${vm.content.length} / ${CreateViewModel.CONTENT_MAX}",
                style = TimartType.caption,
                color = InkDisabled,
                modifier = Modifier.align(Alignment.End),
            )

            // 多章节信件（N10）：分章开关 + 第 2/3 章输入（创建时一次加密入库）
            TextButton(
                onClick = { vm.updateChaptersEnabled(!vm.chaptersEnabled) },
                colors = ButtonDefaults.textButtonColors(contentColor = if (vm.chaptersEnabled) TimeGold else InkSecondary),
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    text = if (vm.chaptersEnabled) L.chapterToggleOn else L.chapterToggleOff,
                    style = TimartType.caption,
                )
            }
            if (vm.chaptersEnabled) {
                vm.chapterTexts.forEachIndexed { index, text ->
                    Text(
                        text = L.chapterLabelFmt.format(index + 2),
                        style = TimartType.caption,
                        color = InkSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    BasicTextField(
                        value = text,
                        onValueChange = { v -> vm.updateChapterText(index, v) },
                        textStyle = TimartType.body.copy(color = InkPrimary, lineHeight = 24.sp),
                        cursorBrush = SolidColor(TimeGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(150.dp),
                    )
                }
                if (vm.chapterTexts.size < com.muxiao.timart.domain.usecase.ChapterLetter.MAX_CHAPTERS - 1) {
                    TextButton(
                        onClick = vm::addChapter,
                        colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(text = L.chapterAdd, style = TimartType.caption)
                    }
                }
            }
        }

        // 灵感卡（封存问答卡）：随机一叶引导句，轻触翻牌换下一张（Crossfade 淡换，克制不抢主焦点）
        val prompts = L.sealPrompts
        if (prompts.isNotEmpty()) {
            var promptIndex by remember { mutableIntStateOf(kotlin.random.Random.nextInt(prompts.size)) }
            fun drawNext() {
                if (prompts.size < 2) return
                var next = kotlin.random.Random.nextInt(prompts.size - 1)
                if (next >= promptIndex) next++
                promptIndex = next
            }
            Text(
                text = L.promptSectionLabel,
                style = TimartType.body,
                color = InkPrimary,
                modifier = Modifier.padding(top = 22.dp),
            )
            Text(
                text = L.promptSectionHint,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceRaise,
                border = androidx.compose.foundation.BorderStroke(1.dp, TimeGold.copy(alpha = 0.35f)),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clickable(onClick = ::drawNext),
            ) {
                Crossfade(
                    targetState = promptIndex,
                    animationSpec = tween(250),
                    label = "promptCard",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                ) { index ->
                    Column {
                        Text(
                            text = prompts[index % prompts.size],
                            style = TimartType.titleSerif.copy(lineHeight = 24.sp),
                            color = InkPrimary,
                        )
                        Text(
                            text = L.promptDrawHint,
                            style = TimartType.caption,
                            color = InkDisabled,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        // 信纸样式（体验储备池 §1）：纸色 × 墨色三档，封存时选定，解封信笺按此呈现
        Text(
            text = L.paperLabel,
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 22.dp),
        )
        Text(
            text = L.paperHint,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            PaperStyle.entries.forEachIndexed { index, style ->
                val selected = vm.paperStyle == index
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) TimeGold.copy(alpha = 0.14f) else SurfaceRaise)
                        .clickable { vm.updatePaperStyle(index) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(style.paper)
                            .border(1.dp, TrackHairlineColor, RoundedCornerShape(7.dp)),
                    )
                    Text(
                        text = paperName(L, index),
                        style = TimartType.caption,
                        color = if (selected) TimeGold else InkSecondary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        // 城市 · 温度 · 天气（快照行；失败可重试 / 跳过）
        CityWeatherSection(
            selectedCity = vm.selectedCity,
            snapshot = vm.snapshot,
            status = vm.cityStatus,
            onPickCity = { showCityPicker = true },
            onRetry = vm::retrySnapshot,
            onSkip = vm::skipSnapshot,
            modifier = Modifier.padding(top = 22.dp),
        )

        // 照片
        Text(
            text = L.photoLabel,
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 22.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            items(vm.images.size) { index ->
                ImageThumb(bytes = vm.images[index], onRemove = { vm.removeImage(index) })
            }
            if (vm.images.size < CreateViewModel.IMAGE_MAX) {
                // 手绘附件（体验储备池 §1）：画布手绘 → PNG → 与照片并列走既有加密图片管线
                item {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(SurfaceRaise, RoundedCornerShape(12.dp))
                            .border(1.dp, TrackHairlineColor, RoundedCornerShape(12.dp))
                            .clickable { showHandDraw = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = L.handDrawTile,
                            style = TimartType.caption,
                            color = InkSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(SurfaceRaise, RoundedCornerShape(12.dp))
                            .border(1.dp, TrackHairlineColor, RoundedCornerShape(12.dp))
                            .clickable {
                                if (useModernPicker) {
                                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } else {
                                    openDocuments.launch(arrayOf("image/*"))
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "+", style = TimartType.titleSerif, color = InkSecondary)
                    }
                }
            }
        }

        // 声音留言（体验储备池 §1）：点按开始/停止录音；封存时加密落盘，解封时信笺上方浮现播放键
        Text(
            text = L.voiceLabel,
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 22.dp),
        )
        Text(
            text = L.voiceHint,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (recording) TimeGold.copy(alpha = 0.14f) else SurfaceRaise,
                    )
                    .clickable {
                        voicePermDenied = false
                        if (recording) {
                            stopAndKeep()
                        } else {
                            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) startVoiceRecord() else voicePermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = when {
                        recording -> L.voiceRecordingFmt.format(recordSeconds)
                        vm.voice == null -> L.voiceRecordStart
                        else -> L.voiceRerecord
                    },
                    style = TimartType.body,
                    color = if (recording) TimeGold else InkPrimary,
                )
            }
            if (!recording && vm.voice != null) {
                Text(
                    text = L.voiceRecordedFmt.format(vm.voiceSeconds),
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(start = 12.dp),
                )
                Text(
                    text = "×",
                    style = TimartType.caption,
                    color = InkPrimary,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .clickable(onClick = vm::removeVoice),
                )
            }
        }
        if (voicePermDenied) {
            Text(
                text = L.voicePermHint,
                style = TimartType.caption,
                color = TimeGold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // 备注
        Text(
            text = L.noteLabel,
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 22.dp),
        )
        Text(
            text = L.noteHint,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        BasicTextField(
            value = vm.note,
            onValueChange = vm::updateNote,
            singleLine = true,
            textStyle = TimartType.body.copy(color = InkPrimary),
            cursorBrush = SolidColor(TimeGold),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(SurfaceRaise, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (vm.note.isEmpty()) {
                        Text(text = L.notePlaceholder, style = TimartType.body, color = InkDisabled)
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // 待答之问（N20，可选）：留空则不写键，回信占位回退默认文案
        Text(
            text = L.questionLabel,
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = L.questionHint,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        BasicTextField(
            value = vm.question,
            onValueChange = vm::updateQuestion,
            singleLine = true,
            textStyle = TimartType.body.copy(color = InkPrimary),
            cursorBrush = SolidColor(TimeGold),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(SurfaceRaise, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (vm.question.isEmpty()) {
                        Text(text = L.questionPlaceholder, style = TimartType.body, color = InkDisabled)
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // 火漆印章（N19，可选）：参数化矢量印章，落款呈现在信笺底部
        Text(
            text = L.sealLabel,
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 18.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            // 无印
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceRaise)
                    .clickable { vm.updateSealStyle(0) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = L.sealNone, style = TimartType.caption, color = InkSecondary)
            }
            for (style in 1..com.muxiao.timart.ui.components.visual.WaxSealStyle.COUNT) {
                val selected = vm.sealStyle == style
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) TimeGold.copy(alpha = 0.14f) else SurfaceRaise)
                        .clickable { vm.updateSealStyle(style) },
                ) {
                    com.muxiao.timart.ui.components.visual.WaxSeal(
                        style = style,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // CTA
        Button(
            onClick = onNext,
            enabled = contentValid,
            colors = ButtonDefaults.buttonColors(
                containerColor = TimeGold,
                contentColor = DeepCharcoal,
                disabledContainerColor = SurfaceRaise,
                disabledContentColor = InkDisabled,
            ),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(text = L.nextToRules, style = TimartType.body)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showCityPicker) {
        CityPickerSheet(vm = vm, onDismiss = { showCityPicker = false })
    }
    if (showHandDraw) {
        HandDrawSheet(
            onSave = { pngBytes ->
                vm.addImages(listOf(pngBytes))
                showHandDraw = false
            },
            onDismiss = { showHandDraw = false },
        )
    }
}

/** 信纸样式名（三语；index 越界回落原纸名） */
private fun paperName(L: com.muxiao.timart.l10n.Strings, index: Int): String = when (PaperStyle.of(index)) {
    PaperStyle.PLAIN -> L.paperNamePlain
    PaperStyle.MIST -> L.paperNameMist
    PaperStyle.EMBER -> L.paperNameEmber
}

/**
 * 停止录音（文件级助手，组合层 startVoiceRecord 的配对）：
 * MediaRecorder.stop（时长过短无有效数据会抛，按放弃处理）→ release；
 * [keep] = true 且停止成功时读临时文件字节进 VM（封存时才加密落盘），临时文件无论如何即删。
 */
private fun stopVoiceRecord(
    vm: CreateViewModel,
    recorderRef: java.util.concurrent.atomic.AtomicReference<android.media.MediaRecorder?>,
    cacheDir: java.io.File,
    keep: Boolean,
    seconds: Int,
) {
    val recorder = recorderRef.getAndSet(null) ?: return
    val stopped = runCatching { recorder.stop() }.isSuccess
    runCatching { recorder.release() }
    val temp = java.io.File(cacheDir, "timart_voice_record.m4a")
    if (keep && stopped) {
        runCatching { temp.readBytes() }.getOrNull()?.let { bytes -> vm.addVoice(bytes, seconds) }
    }
    runCatching { temp.delete() }
}

/** 已选图片缩略（解码采样 128px，UI 层 android.graphics 允许） */@Composable
private fun ImageThumb(bytes: ByteArray, onRemove: () -> Unit) {
    val bitmap = remember(bytes) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 128)
            BitmapFactory.Options().apply { inSampleSize = sample }
                .let { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
        }.getOrNull()
    }
    Box(modifier = Modifier.size(76.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .background(SurfaceRaise, RoundedCornerShape(12.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(SurfaceRaise, RoundedCornerShape(12.dp)),
            )
        }
        Text(
            text = "×",
            style = TimartType.caption,
            color = InkPrimary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable(onClick = onRemove)
                .padding(4.dp),
        )
    }
}

private val TrackHairlineColor = Color(0xFF2E2A25)
