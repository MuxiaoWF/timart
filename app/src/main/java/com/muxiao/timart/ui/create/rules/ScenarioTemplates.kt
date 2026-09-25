package com.muxiao.timart.ui.create.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.create.CreateViewModel
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import java.time.DayOfWeek
import kotlinx.coroutines.launch

/**
 * 场景模板（储备池 §6.2；引擎零改动，纯创建侧编排）：
 * 一键生成一组自包含条件（不依赖额外输入、不依赖快照城市），命中高频"仪式场景"，
 * 解决大量条件的可发现性问题。模板只做条件编排，落库前仍走创建页常规确认流。
 */
internal enum class ScenarioTemplate(
    val label: @Composable (com.muxiao.timart.l10n.Strings) -> String,
    val note: @Composable (com.muxiao.timart.l10n.Strings) -> String,
    val conditions: () -> List<UnlockCondition>,
) {
    NIGHT(
        label = { it.tplNightName },
        note = { it.tplNightNote },
        conditions = {
            listOf(
                UnlockCondition.DarkTheme(isDark = true),
                UnlockCondition.DoNotDisturb(isActive = true),
                UnlockCondition.TimeRange(startHour = 22, endHour = 6),
            )
        },
    ),
    FAR_AWAY(
        label = { it.tplFarName },
        note = { it.tplFarNote },
        conditions = {
            listOf(
                UnlockCondition.TimezoneChange(homeZoneId = java.time.ZoneId.systemDefault().id),
                UnlockCondition.Hemisphere(north = false),
            )
        },
    ),
    MORNING_RUN(
        label = { it.tplRunName },
        note = { it.tplRunNote },
        conditions = {
            listOf(
                UnlockCondition.WeekDay(weekSet = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
                UnlockCondition.StepCount(minTodayStep = 6000, maxTodayStep = null),
                UnlockCondition.TimeRange(startHour = 5, endHour = 9),
            )
        },
    ),
    FULL_MOON(
        label = { it.tplMoonName },
        note = { it.tplMoonNote },
        conditions = {
            listOf(
                UnlockCondition.MoonPhase(phases = setOf(com.muxiao.timart.domain.model.unlock.MoonPhaseKind.FULL)),
                UnlockCondition.TimeRange(startHour = 20, endHour = 23),
            )
        },
    ),
    LUNAR_NEW_YEAR(
        label = { it.tplLunarName },
        note = { it.tplLunarNote },
        conditions = {
            listOf(
                UnlockCondition.LunarDate(month = 1, day = 1),
                UnlockCondition.TimeRange(startHour = 8, endHour = 12),
            )
        },
    ),
    MOTHERS_DAY(
        label = { it.tplMotherName },
        note = { it.tplMotherNote },
        conditions = {
            listOf(UnlockCondition.YearlyNthWeekday(month = 5, nth = 2, dayOfWeek = DayOfWeek.SUNDAY))
        },
    ),
    UNPLUG(
        label = { it.tplUnplugName },
        note = { it.tplUnplugNote },
        conditions = {
            listOf(
                UnlockCondition.AirplaneMode(isEnabled = true),
                UnlockCondition.TimeRange(startHour = 21, endHour = 23),
            )
        },
    ),
    CAFE(
        label = { it.tplCafeName },
        note = { it.tplCafeNote },
        conditions = {
            listOf(
                UnlockCondition.NetworkType(types = setOf(com.muxiao.timart.domain.model.unlock.NetType.WIFI)),
                UnlockCondition.WeekDay(
                    weekSet = setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                    ),
                ),
                UnlockCondition.TimeRange(startHour = 14, endHour = 17),
            )
        },
    ),
}

/** 场景模板选择面板：卡片列出名称 + 说明，点选即按序加进当前草稿（超上限的条件静默丢弃，与手动添加同口径）。
 *  下方「我的模板」（体验储备池 §7.2）：把当前条件组合序列化为 meta 模板，可复用/删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScenarioTemplateSheet(
    vm: CreateViewModel,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    var userTemplates by remember { mutableStateOf(emptyList<CreateViewModel.UserTemplateDto>()) }
    var saving by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    // N7 模板二维码分享：导出（显示码图）/ 导入（拍照解码）两个状态位
    var qrRuleJson by remember { mutableStateOf<String?>(null) }
    var qrImportHint by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        userTemplates = vm.userTemplates()
    }
    val qrScanner = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        val text = com.muxiao.timart.utils.format.QrCodec.decodeText(bitmap)
        val ruleJson = text?.takeIf { it.startsWith(com.muxiao.timart.utils.format.QrCodec.TEMPLATE_PREFIX) }
            ?.removePrefix(com.muxiao.timart.utils.format.QrCodec.TEMPLATE_PREFIX)
        val rule = ruleJson?.let { json ->
            runCatching {
                com.muxiao.timart.data.local.db.mapper.UnlockRuleJson.fromJson(json)
            }.getOrNull()
        }
        if (rule != null) {
            vm.applyTemplate(rule)
            qrImportHint = null
            onDismiss()
        } else {
            qrImportHint = L.tplQrInvalid
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        // 与条件类型面板同口径：跳过半展开，避免占满屏瞬间的嵌套滚动交接卡顿
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(text = L.scenarioSheetTitle, style = TimartType.titleSerif, color = InkPrimary)
            ScenarioTemplate.entries.forEach { template ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .background(SurfaceRaise, RoundedCornerShape(12.dp))
                        .clickable {
                            template.conditions().forEach { vm.addCondition(it) }
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = template.label(L),
                            style = TimartType.body.copy(color = TimeGold),
                        )
                        Text(
                            text = template.note(L),
                            style = TimartType.caption,
                            color = InkSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(modifier = Modifier.padding(2.dp))
                    Text(
                        text = "›",
                        style = TimartType.titleSerif,
                        color = InkSecondary,
                    )
                }
            }

            // ---- 我的模板（体验储备池 §7.2） ----
            Text(
                text = L.tplUserSection,
                style = TimartType.caption,
                color = InkDisabled,
                modifier = Modifier.padding(top = 18.dp),
            )
            if (saving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = templateName,
                        onValueChange = { templateName = it.take(20) },
                        singleLine = true,
                        textStyle = TimartType.body.copy(color = InkPrimary),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = L.confirm,
                        style = TimartType.caption,
                        color = TimeGold,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clickable {
                                vm.saveUserTemplate(templateName)
                                saving = false
                                templateName = ""
                                // meta 落库后异步刷新列表
                                scope.launch { userTemplates = vm.userTemplates() }
                            },
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clickable {
                            if (vm.conditions.isNotEmpty()) saving = true
                        },
                ) {
                    Text(
                        text = if (vm.conditions.isEmpty()) L.tplUserNeedConditions else L.tplUserSaveCurrent,
                        style = TimartType.caption,
                        color = if (vm.conditions.isEmpty()) InkDisabled else TimeGold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            userTemplates.forEach { template ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .background(SurfaceRaise, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = template.name,
                        style = TimartType.body.copy(color = TimeGold),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val rule = runCatching {
                                    com.muxiao.timart.data.local.db.mapper.UnlockRuleJson.fromJson(template.ruleJson)
                                }.getOrNull()
                                if (rule != null) {
                                    vm.applyTemplate(rule)
                                    onDismiss()
                                }
                            },
                    )
                    // N7：模板二维码导出（只有规则结构，零密文零内容）
                    Text(
                        text = L.tplQrExport,
                        style = TimartType.caption,
                        color = InkSecondary,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clickable { qrRuleJson = template.ruleJson },
                    )
                    Text(
                        text = "×",
                        style = TimartType.caption,
                        color = InkSecondary,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clickable {
                                vm.deleteUserTemplate(template.name)
                                userTemplates = userTemplates.filterNot { it.name == template.name }
                            },
                    )
                }
            }

            // ---- N7 扫码导入：拍一张模板码 → 解码 → coerce 校验 → 应用为当前条件 ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clickable { qrScanner.launch(null) },
            ) {
                Text(
                    text = L.tplQrImport,
                    style = TimartType.caption,
                    color = TimeGold,
                    modifier = Modifier.weight(1f),
                )
            }
            qrImportHint?.let { hint ->
                Text(
                    text = hint,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(modifier = Modifier.padding(vertical = 12.dp))
        }
    }

    // ---- 模板码展示弹层（N7 导出侧；码图按内容生成，即用即弃） ----
    qrRuleJson?.let { ruleJson ->
        val qrBitmap = remember(ruleJson) {
            com.muxiao.timart.utils.format.QrCodec.encodeBitmap(
                com.muxiao.timart.utils.format.QrCodec.TEMPLATE_PREFIX + ruleJson,
            )
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { qrRuleJson = null },
            containerColor = SurfaceRaise,
            title = { Text(text = L.tplQrExportTitle, style = TimartType.titleSerif) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (qrBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = L.tplQrExportTitle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    } else {
                        Text(
                            text = L.tplQrEncodeFailed,
                            style = TimartType.body,
                            color = InkSecondary,
                        )
                    }
                    Text(
                        text = L.tplQrExportNote,
                        style = TimartType.caption,
                        color = InkDisabled,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { qrRuleJson = null }) {
                    Text(text = L.cancel, color = TimeGold)
                }
            },
        )
    }
}
