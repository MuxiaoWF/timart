package com.muxiao.timart.ui.create.seal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.l10n.currentStrings
import com.muxiao.timart.ui.components.visual.SectionHeader
import com.muxiao.timart.ui.create.CreateViewModel
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TrackHairline
import com.muxiao.timart.utils.RuntimeSettings

/**
 * 第三步「确认封存」（架构 §2.15，对齐设计稿 04）：
 * 草稿摘要卡（标题/正文预览/照片数/规则条件句/依赖/城市天气）+ 标签（1–5）
 * + 阅读后自动销毁（开启需二次确认）+ 封存 CTA。
 * 提交路径：无口令 → PASSWORD_SETUP 引导；密文失败 → toast 并保留草稿。
 */
@Composable
fun SealStep(
    vm: CreateViewModel,
    onBack: () -> Unit,
    onNeedPasswordSetup: () -> Unit,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    var showAutoDestroyConfirm by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        TextButton(
            onClick = onBack,
            colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
        ) {
            Text(text = L.prevStep, style = TimartType.caption)
        }

        SectionHeader(title = L.sealTitle, note = "CREATE 03")

        // ---- 摘要卡 ----
        Column(
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .background(SurfaceRaise, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text(
                text = vm.title.ifBlank { L.untitled },
                style = TimartType.titleSerif,
                color = InkPrimary,
            )
            if (vm.content.isNotBlank()) {
                Text(
                    text = vm.content.take(80) + if (vm.content.length > 80) "…" else "",
                    style = TimartType.body,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (vm.images.isNotEmpty()) {
                Text(
                    text = L.photosAttachedFmt.format(vm.images.size),
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            SummaryDivider()
            // 规则摘要
            Text(text = L.rulesTitle, style = TimartType.caption, color = InkDisabled)
            Text(
                text = ruleSummary(vm),
                style = TimartType.body,
                color = InkPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )
            SummaryDivider()
            // 城市天气
            Text(text = L.weatherCityLabel, style = TimartType.caption, color = InkDisabled)
            Text(
                text = vm.snapshot?.let { snap ->
                    "${snap.cityName} · %d°C · %s".format(
                        snap.tempC.toInt(),
                        ConditionText.weatherName(snap.weatherType.name, RuntimeSettings.resolvedLang) ?: L.unknown,
                    )
                } ?: L.weatherNoneRecorded,
                style = TimartType.body,
                color = InkPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (vm.note.isNotBlank()) {
                SummaryDivider()
                Text(text = L.noteLabel, style = TimartType.caption, color = InkDisabled)
                Text(
                    text = vm.note,
                    style = TimartType.body,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // ---- 标签（1–5） ----
        Text(
            text = L.tagsLabel,
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = L.tagsDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth(),
        ) {
            BasicTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                singleLine = true,
                textStyle = TimartType.body.copy(color = InkPrimary),
                cursorBrush = SolidColor(TimeGold),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .background(SurfaceRaise, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (tagInput.isEmpty()) {
                            Text(text = L.tagPlaceholder, style = TimartType.body, color = InkDisabled)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    vm.addTag(tagInput)
                    tagInput = ""
                },
                enabled = tagInput.isNotBlank() && vm.tags.size < CreateViewModel.TAG_MAX,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = TimeGold,
                    disabledContentColor = InkDisabled,
                ),
            ) {
                Text(text = L.tagAdd, style = TimartType.body)
            }
        }
        if (vm.tags.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                items(vm.tags.size) { index ->
                    val tag = vm.tags[index]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(TimeGold.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(text = tag, style = TimartType.caption, color = TimeGold)
                        Text(
                            text = " ×",
                            style = TimartType.caption,
                            color = InkSecondary,
                            modifier = Modifier.clickable { vm.removeTag(tag) },
                        )
                    }
                }
            }
        }

        // ---- 自动销毁 ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = L.autoDestroyLabel, style = TimartType.body, color = InkPrimary)
                Text(
                    text = L.autoDestroyDesc,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(
                checked = vm.autoDestroy,
                onCheckedChange = { checked ->
                    if (checked) {
                        showAutoDestroyConfirm = true
                    } else {
                        vm.updateAutoDestroy(false)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TimeGold,
                    checkedTrackColor = TimeGold.copy(alpha = 0.35f),
                    uncheckedThumbColor = InkDisabled,
                    uncheckedTrackColor = SurfaceRaise,
                ),
            )
        }

        // ---- 封存 CTA ----
        Button(
            onClick = {
                vm.submit(
                    onNeedPasswordSetup = onNeedPasswordSetup,
                    onFailed = { message ->
                        // 密文 / 图片加密失败：toast 提示并保留草稿（不清空输入）
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                )
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = TimeGold,
                contentColor = DeepCharcoal,
            ),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .padding(top = 28.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(text = L.sealConfirm, style = TimartType.body)
        }
        Text(
            text = L.sealConfirmDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier
                .padding(top = 10.dp)
                .align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    // ---- 自动销毁二次确认 ----
    if (showAutoDestroyConfirm) {
        AlertDialog(
            onDismissRequest = { showAutoDestroyConfirm = false },
            containerColor = SurfaceRaise,
            title = {
                Text(text = L.sealAskDestroyTitle, style = TimartType.titleSerif, color = InkPrimary)
            },
            text = {
                Text(
                    text = L.sealAskDestroyBody,
                    style = TimartType.body,
                    color = InkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAutoDestroyConfirm = false
                        vm.updateAutoDestroy(true)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.enable, style = TimartType.body)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAutoDestroyConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
                ) {
                    Text(text = L.cancel, style = TimartType.body)
                }
            },
        )
    }
}

/** 摘要卡内细分隔线 */
@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .padding(vertical = 14.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(TrackHairline),
    )
}

/** 规则摘要句：空 → 立即可开启；否则按 AND/OR 前缀 + 条件句列表 */
private fun ruleSummary(vm: CreateViewModel): String {
    val L = currentStrings()
    val lang = RuntimeSettings.resolvedLang
    val parts = mutableListOf<String>()
    if (vm.conditions.isNotEmpty()) {
        val prefix = if (vm.logic == LogicType.AND) L.summaryAndPrefix else L.summaryOrPrefix
        parts.add(prefix + vm.conditions.joinToString(L.summarySep) { ConditionText.conditionSentence(it, lang) })
    }
    vm.dependTitle?.let { parts.add(L.summaryDependFmt.format(it)) }
    if (vm.autoDestroy) parts.add(L.summaryReadDestroy)
    return parts.joinToString("\n").ifEmpty { L.summaryImmediate }
}
