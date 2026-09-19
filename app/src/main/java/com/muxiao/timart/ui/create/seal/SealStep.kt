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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.Capsule
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
    var showBlindBoxConfirm by remember { mutableStateOf(false) }
    var showPuzzleSheet by remember { mutableStateOf(false) }
    var showSeedPicker by remember { mutableStateOf(false) }
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

        // ---- 盲盒封存（连自己也保密） ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = L.blindBoxLabel, style = TimartType.body, color = InkPrimary)
                Text(
                    text = L.blindBoxDesc,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(
                checked = vm.blindBox,
                onCheckedChange = { checked ->
                    if (checked) {
                        showBlindBoxConfirm = true
                    } else {
                        vm.updateBlindBox(false)
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

        // ---- 拼图分组（体验储备池 §3） ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = L.puzzleLabel, style = TimartType.body, color = InkPrimary)
                Text(
                    text = L.puzzleDesc,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = if (vm.puzzleGroupId != null) {
                    L.puzzleJoinedFmt.format(vm.puzzleIndex + 1, vm.puzzleTotal)
                } else {
                    L.puzzleJoin
                },
                style = TimartType.caption,
                color = TimeGold,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable {
                        if (vm.puzzleGroupId != null) vm.leavePuzzle() else showPuzzleSheet = true
                    },
            )
        }

        // ---- 嵌套种子（体验储备池 §3） ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = L.seedLabel, style = TimartType.body, color = InkPrimary)
                Text(
                    text = L.seedDesc,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = if (vm.seedParentId != null) {
                    L.seedBoundFmt.format(vm.seedParentTitle ?: L.untitled)
                } else {
                    L.seedPick
                },
                style = TimartType.caption,
                color = TimeGold,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable {
                        if (vm.seedParentId != null) vm.setSeedParent(null, null) else showSeedPicker = true
                    },
            )
        }

        // ---- 触觉签名 / 环境音（体验储备池 §6） ----
        Text(
            text = L.hapticLabel,
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = L.hapticDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            listOf(L.hapticNone, L.hapticDouble, L.hapticPulse, L.hapticRipple).forEachIndexed { index, label ->
                val selected = vm.hapticStyle == index
                Text(
                    text = label,
                    style = TimartType.caption,
                    color = if (selected) TimeGold else InkSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) TimeGold.copy(alpha = 0.14f) else SurfaceRaise)
                        .clickable { vm.updateHapticStyle(index) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        Text(
            text = L.ambientLabel,
            style = TimartType.body,
            color = InkPrimary,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = L.ambientDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            listOf(
                com.muxiao.timart.utils.audio.AmbientSoundPlayer.Scene.OFF,
                com.muxiao.timart.utils.audio.AmbientSoundPlayer.Scene.RAIN,
                com.muxiao.timart.utils.audio.AmbientSoundPlayer.Scene.DRONE,
            ).forEach { scene ->
                val selected = vm.ambientScene == scene.name
                Text(
                    text = when (scene) {
                        com.muxiao.timart.utils.audio.AmbientSoundPlayer.Scene.OFF -> L.ambientNone
                        com.muxiao.timart.utils.audio.AmbientSoundPlayer.Scene.RAIN -> L.ambientRain
                        com.muxiao.timart.utils.audio.AmbientSoundPlayer.Scene.DRONE -> L.ambientDrone
                    },
                    style = TimartType.caption,
                    color = if (selected) TimeGold else InkSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) TimeGold.copy(alpha = 0.14f) else SurfaceRaise)
                        .clickable { vm.updateAmbientScene(scene.name) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        // ---- 口令分片（体验储备池 §7.1）：集齐 M 份线下分片才能开封 ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = L.shardLabel, style = TimartType.body, color = InkPrimary)
                Text(
                    text = L.shardDesc,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(
                checked = vm.shardEnabled,
                onCheckedChange = { vm.updateShardEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TimeGold,
                    checkedTrackColor = TimeGold.copy(alpha = 0.35f),
                    uncheckedThumbColor = InkDisabled,
                    uncheckedTrackColor = SurfaceRaise,
                ),
            )
        }
        if (vm.shardEnabled) {
            Text(
                text = L.shardNote,
                style = TimartType.caption,
                color = TimeGold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = L.shardTotalFmt.format(vm.shardTotal),
                    style = TimartType.caption,
                    color = InkPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.updateShardTotal(vm.shardTotal - 1) }, enabled = vm.shardTotal > 2) {
                    Text(text = "−", color = InkPrimary)
                }
                TextButton(
                    onClick = { vm.updateShardTotal(vm.shardTotal + 1) },
                    enabled = vm.shardTotal < com.muxiao.timart.domain.usecase.ShardSecretUseCase.MAX_TOTAL,
                ) {
                    Text(text = "+", color = InkPrimary)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = L.shardThresholdFmt.format(vm.shardThreshold),
                    style = TimartType.caption,
                    color = InkPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.updateShardThreshold(vm.shardThreshold - 1) }, enabled = vm.shardThreshold > 2) {
                    Text(text = "−", color = InkPrimary)
                }
                TextButton(
                    onClick = { vm.updateShardThreshold(vm.shardThreshold + 1) },
                    enabled = vm.shardThreshold < vm.shardTotal - 1,
                ) {
                    Text(text = "+", color = InkPrimary)
                }
            }
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
    // ---- 盲盒封存二次确认（与自动销毁同级：开启需明确意图） ----
    if (showBlindBoxConfirm) {
        AlertDialog(
            onDismissRequest = { showBlindBoxConfirm = false },
            containerColor = SurfaceRaise,
            title = {
                Text(text = L.blindAskTitle, style = TimartType.titleSerif, color = InkPrimary)
            },
            text = {
                Text(
                    text = L.blindAskBody,
                    style = TimartType.body,
                    color = InkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlindBoxConfirm = false
                        vm.updateBlindBox(true)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.enable, style = TimartType.body)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBlindBoxConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
                ) {
                    Text(text = L.cancel, style = TimartType.body)
                }
            },
        )
    }
    // ---- 拼图分组弹窗（体验储备池 §3）：新建组（份数 2–9）或加入未满员既有组 ----
    if (showPuzzleSheet) {
        var pieceCount by remember { mutableIntStateOf(2) }
        var groups by remember { mutableStateOf(emptyList<CreateViewModel.PuzzleGroupOption>()) }
        LaunchedEffect(Unit) {
            groups = vm.puzzleGroups()
        }
        AlertDialog(
            onDismissRequest = { showPuzzleSheet = false },
            containerColor = SurfaceRaise,
            title = { Text(text = L.puzzleLabel, style = TimartType.titleSerif, color = InkPrimary) },
            text = {
                Column {
                    Text(text = L.puzzleSheetDesc, style = TimartType.caption, color = InkSecondary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text(
                            text = L.puzzlePieceCountFmt.format(pieceCount),
                            style = TimartType.body,
                            color = InkPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { if (pieceCount > 2) pieceCount-- }) {
                            Text(text = "−", color = InkPrimary)
                        }
                        TextButton(onClick = { if (pieceCount < 9) pieceCount++ }) {
                            Text(text = "+", color = InkPrimary)
                        }
                    }
                    Text(
                        text = L.puzzleNewGroup,
                        style = TimartType.caption,
                        color = TimeGold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .clickable {
                                vm.joinPuzzle(
                                    java.util.UUID.randomUUID().toString(),
                                    0,
                                    pieceCount,
                                )
                                showPuzzleSheet = false
                            },
                    )
                    if (groups.isNotEmpty()) {
                        Text(
                            text = L.puzzleExistingGroups,
                            style = TimartType.caption,
                            color = InkDisabled,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        groups.forEach { option ->
                            Text(
                                text = L.puzzleGroupRowFmt.format(option.total, option.filledCount),
                                style = TimartType.caption,
                                color = InkPrimary,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.joinPuzzle(
                                            option.groupId,
                                            option.filledCount,
                                            option.total,
                                        )
                                        showPuzzleSheet = false
                                    },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPuzzleSheet = false }) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            },
        )
    }

    // ---- 嵌套种子父胶囊选择（体验储备池 §3） ----
    if (showSeedPicker) {
        var candidates by remember { mutableStateOf(emptyList<Capsule>()) }
        LaunchedEffect(Unit) {
            candidates = vm.seedCandidates()
        }
        AlertDialog(
            onDismissRequest = { showSeedPicker = false },
            containerColor = SurfaceRaise,
            title = { Text(text = L.seedPickTitle, style = TimartType.titleSerif, color = InkPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(text = L.seedPickDesc, style = TimartType.caption, color = InkSecondary)
                    if (candidates.isEmpty()) {
                        Text(
                            text = L.seedEmpty,
                            style = TimartType.caption,
                            color = InkDisabled,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    candidates.forEach { capsule ->
                        Text(
                            text = capsule.title,
                            style = TimartType.body,
                            color = InkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth()
                                .clickable {
                                    vm.setSeedParent(capsule.id, capsule.title)
                                    showSeedPicker = false
                                },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSeedPicker = false }) {
                    Text(text = L.cancel, color = InkSecondary)
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

/** 规则摘要句：空 → 立即可开启；否则按 AND/OR/任选M 前缀 + 条件句列表 */
private fun ruleSummary(vm: CreateViewModel): String {
    val L = currentStrings()
    val lang = RuntimeSettings.resolvedLang
    val parts = mutableListOf<String>()
    if (vm.conditions.isNotEmpty()) {
        val prefix = when (vm.logic) {
            LogicType.AND -> L.summaryAndPrefix
            LogicType.OR -> L.summaryOrPrefix
            LogicType.AT_LEAST -> L.summaryAtLeastPrefix.format(
                vm.logicThreshold.coerceIn(1, vm.conditions.size),
                vm.conditions.size,
            )
        }
        parts.add(prefix + vm.conditions.joinToString(L.summarySep) { ConditionText.conditionSentence(it, lang) })
    }
    vm.dependTitle?.let { parts.add(L.summaryDependFmt.format(it)) }
    if (vm.autoDestroy) parts.add(L.summaryReadDestroy)
    if (vm.blindBox) parts.add(L.summaryBlindBox)
    return parts.joinToString("\n").ifEmpty { L.summaryImmediate }
}
