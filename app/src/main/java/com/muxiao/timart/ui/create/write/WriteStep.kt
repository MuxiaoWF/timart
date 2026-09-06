package com.muxiao.timart.ui.create

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.components.visual.SectionHeader
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
    engine: com.muxiao.timart.ui.components.particle.ParticleEngine,
    onSpark: (rootX: Float, rootY: Float) -> Unit,
    onNext: () -> Unit,
) {
    val L = LocalStrings.current
    val contentValid = vm.title.isNotBlank() || vm.content.isNotBlank()
    var showCityPicker by remember { mutableStateOf(false) }

    // 正文光标坐标（根坐标系）：字段原点 + 光标矩形中心
    var bodyFieldOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var bodyLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    /** 在正文光标处发射飘粒（布局为旧文本时按新文本长度钳制到末位） */
    fun sparkAtCaret(newTextLength: Int) {
        val layout = bodyLayout ?: return
        val text = layout.layoutInput.text
        if (text.length == 0) return
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
    val useModernPicker = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU

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
                    .onGloballyPositioned { bodyFieldOrigin = it.localToRoot(androidx.compose.ui.geometry.Offset.Zero) },
            )
            Text(
                text = "${vm.content.length} / ${CreateViewModel.CONTENT_MAX}",
                style = TimartType.caption,
                color = InkDisabled,
                modifier = Modifier.align(Alignment.End),
            )
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
}

/** 已选图片缩略（解码采样 128px，UI 层 android.graphics 允许） */
@Composable
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
            androidx.compose.foundation.Image(
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
