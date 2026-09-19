package com.muxiao.timart.ui.create.write

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.PaperCream
import com.muxiao.timart.ui.theme.PaperInk
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TrackHairline
import androidx.core.graphics.createBitmap

/**
 * 手绘附件弹层（体验储备池 §1）：单指拖拽画笔画线 → 渲染为 PNG 字节 →
 * 走既有图片管线（与照片并列「随信附件」，封存时同样加密落盘）。
 * 笔画为点位序列（画布 px 坐标），保存时按同几何渲染 android.graphics.Canvas，所见即所得。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandDrawSheet(
    onSave: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    val density = LocalDensity.current
    // 已完成笔画 + 进行中笔画（画布 px 坐标）
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSizePx by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val strokePx = with(density) { 3.dp.toPx() }
    val inkColor = PaperInk
    val inkArgb = inkColor.toArgb()
    val paperArgb = PaperCream.toArgb()

    /** 渲染 PNG（与画布同尺寸同几何；纸底 + 深墨圆头笔画） */
    fun renderPng(): ByteArray? {
        if (canvasSizePx <= 0 || strokes.isEmpty()) return null
        return runCatching {
            val bmp = createBitmap(canvasSizePx, canvasSizePx)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(paperArgb)
            val paint = android.graphics.Paint().apply {
                color = inkArgb
                strokeWidth = strokePx
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                isAntiAlias = true
            }
            strokes.forEach { stroke ->
                val path = android.graphics.Path()
                stroke.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                canvas.drawPath(path, paint)
            }
            val out = java.io.ByteArrayOutputStream()
            val ok = bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
            if (ok) out.toByteArray() else null
        }.getOrNull()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(text = L.handDrawTitle, style = TimartType.titleSerif, color = InkPrimary)
            Text(
                text = L.handDrawHint,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PaperCream)
                    .border(1.dp, TrackHairline, RoundedCornerShape(14.dp))
                    .onSizeChanged { canvasSizePx = it.width }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos -> current = listOf(pos) },
                            onDrag = { change, _ ->
                                change.consume()
                                current = current + change.position
                            },
                            onDragEnd = {
                                if (current.size > 1) strokes.add(current)
                                current = emptyList()
                            },
                            onDragCancel = { current = emptyList() },
                        )
                    },
            ) {
                // 进行中笔画随拖拽即时呈现（绘制相位读状态，无动画循环）
                val all = if (current.isNotEmpty()) strokes + listOf(current) else strokes
                all.forEach { stroke ->
                    val path = Path()
                    stroke.forEachIndexed { i, p ->
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    drawPath(
                        path = path,
                        color = inkColor,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            ) {
                Text(
                    text = L.handDrawUndo,
                    style = TimartType.caption,
                    color = if (strokes.isNotEmpty()) InkPrimary else InkDisabled,
                    modifier = Modifier.clickable(enabled = strokes.isNotEmpty()) {
                        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                    },
                )
                Text(
                    text = L.handDrawClear,
                    style = TimartType.caption,
                    color = if (strokes.isNotEmpty()) InkPrimary else InkDisabled,
                    modifier = Modifier.clickable(enabled = strokes.isNotEmpty()) { strokes.clear() },
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = L.confirm,
                    style = TimartType.body,
                    color = if (!saving && strokes.isNotEmpty()) {
                        com.muxiao.timart.ui.theme.TimeGold
                    } else {
                        InkDisabled
                    },
                    modifier = Modifier.clickable(enabled = !saving && strokes.isNotEmpty()) {
                        saving = true
                        renderPng()?.let(onSave) ?: run { saving = false }
                    },
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
