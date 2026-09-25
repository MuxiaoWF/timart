package com.muxiao.timart.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muxiao.timart.AppContainer
import com.muxiao.timart.BuildConfig
import com.muxiao.timart.domain.model.AnimationTier
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.l10n.Strings
import com.muxiao.timart.ui.components.particle.ParticleCanvas
import com.muxiao.timart.ui.components.particle.ParticleEngine
import com.muxiao.timart.ui.components.particle.ParticlePreset
import com.muxiao.timart.ui.components.visual.SectionHeader
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TrackHairline
import com.muxiao.timart.ui.theme.wideContentWidth
import com.muxiao.timart.data.remote.update.UpdateChecker
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.utils.AppLanguage
import com.muxiao.timart.utils.export.AnnualReport
import com.muxiao.timart.utils.export.AutoBackupManager
import com.muxiao.timart.utils.export.PosterComposer
import com.muxiao.timart.utils.format.TimeFormatter
import com.muxiao.timart.utils.permission.PERM_ACTIVITY_RECOGNITION
import com.muxiao.timart.utils.permission.PERM_POST_NOTIFICATIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import androidx.core.net.toUri

/**
 * 页面 4：设置（功能优先无深空背景）：
 * - 动效档位（热生效）/ 陀螺仪视差 / 输入飘粒 / 音效 / 语言 / 口令管理 / 备份导出导入 / 权限说明 / 天气数据 / 检查更新（GitHub Releases 占位）/ 关于；
 * - 全部读写 meta（SettingsViewModel）；仅「进入」类动作（语言选择/口令管理）给一次轻 ASSEMBLE 尘粒反馈，
 *   开关/档位切换不做粒子反馈（档位切换伴随引擎热重建，粒子序列会被中断，观感突兀）；
 * - 语言（settings.language）经 RuntimeSettings 快照状态热切换全树文案；
 * - 备份弹窗由 T15 的 BackupDialogs 提供实现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val L = LocalStrings.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val tier by vm.tier.collectAsStateWithLifecycle()
    val gyroEnabled by vm.gyroEnabled.collectAsStateWithLifecycle()
    val inputSparkEnabled by vm.inputSparkEnabled.collectAsStateWithLifecycle()
    val soundEnabled by vm.soundEnabled.collectAsStateWithLifecycle()
    val dawnDuskTint by vm.dawnDuskTint.collectAsStateWithLifecycle()
    val biometricLock by vm.biometricLock.collectAsStateWithLifecycle()
    val sessionTtl by vm.sessionTtlHours.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val hasPassword by vm.hasPassword.collectAsStateWithLifecycle()
    val passwordChange by vm.passwordChange.collectAsStateWithLifecycle()
    val autoBackup by vm.autoBackup.collectAsStateWithLifecycle()
    val backupPwSet by vm.backupPwSet.collectAsStateWithLifecycle()

    val engine = container.particleEngine
    val density = LocalDensity.current
    val context = LocalContext.current

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showWeatherDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showModifyPassword by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showGiftImportDialog by remember { mutableStateOf(false) }
    var showAnnualReport by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showHealthCheck by remember { mutableStateOf(false) }
    var showStarLedger by remember { mutableStateOf(false) }
    var showMigration by remember { mutableStateOf(false) }
    var showAutoBackupPeriod by remember { mutableStateOf(false) }
    var showAutoBackupHistory by remember { mutableStateOf(false) }
    var showBackupPwDialog by remember { mutableStateOf(false) }

    // 自动备份目录选择（ACTION_OPEN_TREE + 持久化授权）
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        vm.setAutoBackupTree(uri)
    }

    // 检查更新（GitHub Releases 占位）：请求在 IO 协程，结果落主线程弹窗
    val scope = rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }

    // 语言入口行中心：弹层是独立窗口，其内坐标无法映射主窗口，选择语言后锚回入口行给粒子反馈
    var langRowCenter by remember { mutableStateOf(IntOffset.Zero) }

    // 改动处一次星环余晖反馈（HALO 单发旋散渐淡 ~0.9–1.6s；较 1.2s ASSEMBLE 序列轻快，
    // 且不占用 UNSEAL/ASSEMBLE 的序列互斥通道；tick 保证同一位置可重复触发）
    var feedbackAnchor by remember { mutableStateOf(IntOffset.Zero) }
    var feedbackTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(feedbackTick) {
        if (feedbackTick == 0) return@LaunchedEffect
        engine.fire(
            preset = ParticlePreset.HALO,
            anchorX = feedbackAnchor.x.toFloat(),
            anchorY = feedbackAnchor.y.toFloat(),
            anchorRadius = with(density) { 36.dp.toPx() },
            colorArgb = ParticleEngine.TIME_GOLD,
        )
    }
    var rootOrigin by remember { mutableStateOf(IntOffset.Zero) }
    fun feedbackAt(positionInRoot: IntOffset) {
        feedbackAnchor = positionInRoot - rootOrigin
        feedbackTick++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal)
            .onGloballyPositioned { rootOrigin = it.localToRoot(Offset.Zero).let { p -> IntOffset(p.x.toInt(), p.y.toInt()) } },
    ) {
        ParticleCanvas(engine = engine, modifier = Modifier.matchParentSize())

        // 宽屏限宽居中（横屏适配，见 ui/theme/Adaptive.kt）：卡片行宽过长伤可读性；窄窗下不生效
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .wideContentWidth()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            SectionHeader(title = L.setTitle, note = "SETTINGS")

            Text(
                text = L.setIntro,
                style = TimartType.body,
                color = InkSecondary,
                modifier = Modifier.padding(top = 14.dp),
            )

            // ---- 1. 动效档位（热生效） ----
            SettingsCard(title = L.setMotionCard, modifier = Modifier.padding(top = 16.dp)) {
                Text(text = L.setTierLabel, style = TimartType.body, color = InkPrimary)
                Text(
                    text = L.setTierDesc,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    AnimationTier.entries.forEachIndexed { index, option ->
                        val selected = option == tier
                        SegmentedButton(
                            selected = selected,
                            onClick = { vm.setTier(option) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AnimationTier.entries.size,
                            ),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = TimeGold,
                                activeContentColor = DeepCharcoal,
                                inactiveContainerColor = DeepCharcoal,
                                inactiveContentColor = InkSecondary,
                            ),
                            border = BorderStroke(1.dp, if (selected) TimeGold else TrackHairline),
                            label = {
                                Text(
                                    text = when (option) {
                                        AnimationTier.HIGH -> L.tierHigh
                                        AnimationTier.MEDIUM -> L.tierMid
                                        AnimationTier.LOW -> L.tierLow
                                    },
                                    style = TimartType.caption,
                                    color = if (selected) DeepCharcoal else InkSecondary,
                                )
                            },
                        )
                    }
                }
            }

            // ---- 2–4. 陀螺仪 / 飘粒 / 音效 ----
            SettingsCard(title = L.setFeedbackCard, modifier = Modifier.padding(top = 12.dp)) {
                SwitchRow(
                    label = L.setGyro,
                    description = L.setGyroDesc,
                    checked = gyroEnabled,
                    onChanged = vm::setGyroEnabled,
                )
                SwitchRow(
                    label = L.setSpark,
                    description = L.setSparkDesc,
                    checked = inputSparkEnabled,
                    onChanged = vm::setInputSparkEnabled,
                )
                SwitchRow(
                    label = L.setSound,
                    description = L.setSoundDesc,
                    checked = soundEnabled,
                    onChanged = vm::setSoundEnabled,
                )
                // 晨昏暖色（N17，默认关）：清晨/黄昏时段主题加一层暖色浸染，只读本地时钟
                SwitchRow(
                    label = L.setDawnDusk,
                    description = L.setDawnDuskDesc,
                    checked = dawnDuskTint,
                    onChanged = vm::setDawnDuskTint,
                )
            }

            // ---- 5. 语言（入口行 + 底部单选列表；快照状态热切换，无需重启） ----
            SettingsCard(title = L.setLanguageCard, modifier = Modifier.padding(top = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguageSheet = true }
                        .onGloballyPositioned { coords ->
                            langRowCenter = IntOffset(
                                coords.localToRoot(Offset.Zero).x.toInt() + coords.size.width / 2,
                                coords.localToRoot(Offset.Zero).y.toInt() + coords.size.height / 2,
                            )
                        }
                        // 文字与热区边缘留出水平空隙（波纹不贴字）
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = languageName(language, L), style = TimartType.body, color = InkPrimary)
                        Text(
                            text = L.setLanguageDesc,
                            style = TimartType.caption,
                            color = InkSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(text = L.enter, style = TimartType.caption, color = TimeGold, modifier = Modifier.padding(start = 12.dp))
                }
            }

            // ---- 6. 口令管理（已设口令才显示；首次设置走创建流程） ----
            if (hasPassword) {
                SettingsCard(title = L.setPasswordCard, modifier = Modifier.padding(top = 12.dp)) {
                    var rowCenter by remember { mutableStateOf(IntOffset.Zero) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                feedbackAt(rowCenter)
                                showModifyPassword = true
                            }
                            .onGloballyPositioned { coords ->
                                rowCenter = IntOffset(
                                    coords.localToRoot(Offset.Zero).x.toInt() + coords.size.width / 2,
                                    coords.localToRoot(Offset.Zero).y.toInt() + coords.size.height / 2,
                                )
                            }
                            // 文字与热区边缘留出水平空隙（波纹不贴字）
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = L.setModifyPw, style = TimartType.body, color = InkPrimary)
                            Text(
                                text = L.setModifyPwDesc,
                                style = TimartType.caption,
                                color = InkSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Text(text = L.go, style = TimartType.caption, color = TimeGold, modifier = Modifier.padding(start = 12.dp))
                    }
                    // 启动隐私锁（N18，默认关）：生物识别只挡入口，内容仍由口令加密
                    var privacyCenter by remember { mutableStateOf(IntOffset.Zero) }
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            privacyCenter = IntOffset(
                                coords.localToRoot(Offset.Zero).x.toInt() + coords.size.width / 2,
                                coords.localToRoot(Offset.Zero).y.toInt() + coords.size.height / 2,
                            )
                        },
                    ) {
                        SwitchRow(
                            label = L.setPrivacyLock,
                            description = L.setPrivacyLockDesc,
                            checked = biometricLock,
                            onChanged = { value ->
                                vm.setBiometricLock(value)
                                // N15 新预设启用位：开 → 星环余晖 / 关 → 尘埃落定
                                // 锚点换算到粒子画布局部坐标（privacyCenter 是根坐标，与同页 feedbackAt 同理）
                                val local = privacyCenter - rootOrigin
                                engine.fire(
                                    preset = if (value) {
                                        ParticlePreset.HALO
                                    } else {
                                        ParticlePreset.SETTLE
                                    },
                                    anchorX = local.x.toFloat(),
                                    anchorY = local.y.toFloat(),
                                    anchorRadius = with(density) { 36.dp.toPx() },
                                    colorArgb = ParticleEngine.TIME_GOLD,
                                )
                            },
                        )
                    }
                    // 口令会话免输时长（储备池 v7）：下一次口令解锁后的免输窗口（密钥依旧不落盘）
                    Text(
                        text = L.setSessionTtl,
                        style = TimartType.body,
                        color = InkPrimary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = L.setSessionTtlDesc,
                        style = TimartType.caption,
                        color = InkSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        val ttlChoices = listOf(
                            24 to L.sessionTtlHoursFmt.format(24),
                            72 to L.sessionTtlDaysFmt.format(3),
                            168 to L.sessionTtlDaysFmt.format(7),
                        )
                        ttlChoices.forEachIndexed { index, (hours, label) ->
                            val selected = sessionTtl == hours
                            SegmentedButton(
                                selected = selected,
                                onClick = { vm.setSessionTtlHours(hours) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ttlChoices.size,
                                ),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = TimeGold,
                                    activeContentColor = DeepCharcoal,
                                    inactiveContainerColor = DeepCharcoal,
                                    inactiveContentColor = InkSecondary,
                                ),
                                border = BorderStroke(1.dp, if (selected) TimeGold else TrackHairline),
                                label = { Text(text = label, style = TimartType.caption) },
                            )
                        }
                    }
                }
            }

            // ---- 7. 备份（导出/导入弹窗见 BackupDialogs，T15；自动备份复用同一导出链路） ----
            SettingsCard(title = L.setBackupCard, modifier = Modifier.padding(top = 12.dp)) {
                SimpleRow(
                    label = L.setExport,
                    description = L.setExportDesc,
                    onClick = { showExportDialog = true },
                )
                SimpleRow(
                    label = L.setImport,
                    description = L.setImportDesc,
                    onClick = { showImportDialog = true },
                )
                SimpleRow(
                    label = L.setGiftImport,
                    description = L.setGiftImportDesc,
                    onClick = { showGiftImportDialog = true },
                )

                // 备份口令（v3 备份口令分离）：导出/自动备份共用的独立口令，与主口令互不影响
                SimpleRow(
                    label = L.setBackupPw,
                    description = if (backupPwSet) L.setBackupPwSetDesc else L.setBackupPwDesc,
                    onClick = { showBackupPwDialog = true },
                )

                // 自动定期本地备份：打开应用达到周期即写入所选目录（密文包，零网络）
                SwitchRow(
                    label = L.setAutoBackup,
                    description = L.setAutoBackupDesc,
                    checked = autoBackup.enabled,
                    onChanged = vm::setAutoBackupEnabled,
                )
                if (autoBackup.enabled) {
                    SimpleRow(
                        label = L.setAutoBackupPeriod,
                        description = L.setAutoBackupPeriodFmt.format(autoBackup.periodDays),
                        onClick = { showAutoBackupPeriod = true },
                    )
                    SimpleRow(
                        label = L.setAutoBackupDir,
                        description = autoBackupDirName(autoBackup.treeUri),
                        onClick = { treePicker.launch(null) },
                    )
                    Text(
                        text = autoBackup.lastAt?.let {
                            L.setAutoBackupLastFmt.format(TimeFormatter.dateTime(it))
                        } ?: L.setAutoBackupNever,
                        style = TimartType.caption,
                        color = InkDisabled,
                        modifier = Modifier.padding(start = 10.dp, top = 2.dp),
                    )
                    // 随备份体检摘要（N22）：仅在体检已执行过时显示，不打扰、不可点
                    autoBackup.healthLastAt?.let { healthAt ->
                        Text(
                            text = (autoBackup.healthFindings ?: 0).let { findings ->
                                if (findings == 0) {
                                    L.setAutoBackupHealthClean
                                        .format(TimeFormatter.dateTime(healthAt))
                                } else {
                                    L.setAutoBackupHealthFmt
                                        .format(TimeFormatter.dateTime(healthAt), findings)
                                }
                            },
                            style = TimartType.caption,
                            color = InkDisabled,
                            modifier = Modifier.padding(start = 10.dp, top = 2.dp),
                        )
                    }
                    SimpleRow(
                        label = L.setAutoBackupNow,
                        description = when (autoBackup.lastStatus) {
                            AutoBackupManager.Status.RAN -> L.setAutoBackupDone
                            AutoBackupManager.Status.FAILED -> L.setAutoBackupFailed
                            AutoBackupManager.Status.SKIPPED_LOCKED -> L.setAutoBackupNeedSession
                            AutoBackupManager.Status.SKIPPED_NO_PW -> L.setAutoBackupNeedPw
                            else -> L.setAutoBackupDesc
                        },
                        onClick = vm::runAutoBackupNow,
                    )
                    // 历史备份（N4）：轮转目录现存份数，可从指定份恢复
                    if (autoBackup.treeUri != null) {
                        SimpleRow(
                            label = L.setAutoBackupHistory,
                            description = L.setAutoBackupHistoryDesc,
                            onClick = { showAutoBackupHistory = true },
                        )
                    }
                }
            }

            // ---- 权限管理（状态实时展示；就近申请或跳转系统设置） ----
            PermissionManageCard(modifier = Modifier.padding(top = 12.dp))

            // ---- 信息类：体检 / 迁移 / 权限说明 / 天气数据 / 关于 ----
            SettingsCard(title = L.setInfoCard, modifier = Modifier.padding(top = 12.dp)) {
                SimpleRow(
                    label = L.setHealthCheck,
                    description = L.setHealthCheckDesc,
                    onClick = { showHealthCheck = true },
                )
                // 星库志（N6）：全库统计常驻入口（纯本地聚合，密文不参与）
                SimpleRow(
                    label = L.ledgerTitle,
                    description = L.ledgerEntryDesc,
                    onClick = { showStarLedger = true },
                )
                SimpleRow(
                    label = L.setMigration,
                    description = L.setMigrationDesc,
                    onClick = { showMigration = true },
                )
                SimpleRow(
                    label = L.setAnnualReport,
                    description = L.setAnnualReportDesc,
                    onClick = { showAnnualReport = true },
                )
                SimpleRow(
                    label = L.setPerm,
                    description = L.setPermDesc,
                    onClick = { showPermissionDialog = true },
                )
                SimpleRow(
                    label = L.setWeatherInfo,
                    description = L.setWeatherInfoDesc,
                    onClick = { showWeatherDialog = true },
                )
                SimpleRow(
                    label = L.setCheckUpdate,
                    description = if (checkingUpdate) L.updChecking else L.setCheckUpdateDesc,
                    onClick = {
                        if (!checkingUpdate) {
                            checkingUpdate = true
                            scope.launch(Dispatchers.IO) {
                                val result = UpdateChecker.check(BuildConfig.VERSION_NAME)
                                withContext(Dispatchers.Main) {
                                    updateResult = result
                                    checkingUpdate = false
                                }
                            }
                        }
                    },
                )
                SimpleRow(
                    label = L.setAbout,
                    description = L.setAboutDesc,
                    onClick = { showAboutDialog = true },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ---- 检查更新结果弹窗（仓库占位期：未发布版本/无网络均软提示） ----
    updateResult?.let { result ->
        AlertDialog(
            onDismissRequest = { updateResult = null },
            containerColor = SurfaceRaise,
            title = { Text(text = L.updTitle, style = TimartType.titleSerif) },
            text = {
                Text(
                    text = when (result) {
                        is UpdateChecker.Result.Available ->
                            L.updNewFmt.format(result.latestTag, BuildConfig.VERSION_NAME)
                        UpdateChecker.Result.UpToDate -> L.updUpToDate
                        UpdateChecker.Result.NotFound -> L.updNotFound
                        UpdateChecker.Result.NetworkError -> L.updNetworkError
                    },
                    style = TimartType.caption,
                    color = InkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateResult = null
                        when (result) {
                            is UpdateChecker.Result.Available ->
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, result.releaseUrl.toUri()),
                                    )
                                }
                            UpdateChecker.Result.NotFound ->
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, UpdateChecker.REPO_URL.toUri()),
                                    )
                                }
                            else -> Unit
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(
                        text = when (result) {
                            is UpdateChecker.Result.Available -> L.updGoDownload
                            UpdateChecker.Result.NotFound -> L.updViewRepo
                            else -> L.ok
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { updateResult = null }) {
                    Text(text = L.batchCancel, color = InkSecondary)
                }
            },
        )
    }

    // ---- 信息类对话框 ----
    if (showPermissionDialog) {
        InfoDialog(title = L.setPerm, onDismiss = { showPermissionDialog = false }) {
            InfoParagraph(text = L.permLocationDesc)
            InfoParagraph(text = L.permActivityDesc)
            InfoParagraph(text = L.permNotifDesc)
        }
    }
    if (showWeatherDialog) {
        InfoDialog(title = L.setWeatherInfo, onDismiss = { showWeatherDialog = false }) {
            InfoParagraph(text = L.weatherSrcLine)
            InfoParagraph(text = L.weatherPrivacyLine)
        }
    }
    if (showAboutDialog) {
        InfoDialog(title = L.setAbout, onDismiss = { showAboutDialog = false }) {
            InfoParagraph(text = L.aboutVersionFmt.format(BuildConfig.VERSION_NAME))
            InfoParagraph(text = L.aboutLine1)
            InfoParagraph(text = L.aboutLine2)
            InfoParagraph(text = L.aboutLicense)
            InfoParagraph(text = L.aboutRepoLabel)
            val uriHandler = LocalUriHandler.current
            Text(
                text = REPO_URL,
                style = TimartType.caption,
                color = TimeGold,
                modifier = Modifier
                    .padding(top = 2.dp)
                    // 链接文字本体过小：上下各扩 10dp 使触达高度接近 48dp 无障碍标准
                    .padding(vertical = 10.dp)
                    .clickable { runCatching { uriHandler.openUri(REPO_URL) } },
            )
            InfoParagraph(text = L.aboutFeedbackLabel)
            Text(
                text = FEEDBACK_EMAIL,
                style = TimartType.caption,
                color = TimeGold,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .padding(vertical = 10.dp)
                    .clickable { runCatching { uriHandler.openUri("mailto:$FEEDBACK_EMAIL") } },
            )
        }
    }

    // ---- 修改口令 ----
    if (showModifyPassword) {
        ModifyPasswordDialog(
            state = passwordChange,
            onConfirm = { old, new -> vm.changePassword(old, new) },
            onDismiss = {
                val current = passwordChange
                if (current == null || current.finished) {
                    vm.consumePasswordChangeState()
                    showModifyPassword = false
                }
            },
        )
    }

    // ---- 备份（T15 实现；v3 备份口令独立于主口令） ----
    if (showExportDialog) {
        BackupExportDialog(
            manager = container.backupManager,
            backupPwSet = backupPwSet,
            onSetBackupPassword = { pw, onResult -> vm.setBackupPassword(pw, onResult) },
            onDismiss = { showExportDialog = false },
        )
    }
    if (showBackupPwDialog) {
        BackupPasswordDialog(
            manager = container.backupManager,
            onSaved = { vm.refreshBackupPassword() },
            onDismiss = { showBackupPwDialog = false },
        )
    }
    if (showImportDialog) {
        BackupImportDialog(manager = container.backupManager, onDismiss = { showImportDialog = false })
    }
    if (showGiftImportDialog) {
        GiftImportDialog(manager = container.backupManager, onDismiss = { showGiftImportDialog = false })
    }
    if (showAnnualReport) {
        AnnualReportDialog(container = container, onDismiss = { showAnnualReport = false })
    }
    if (showHealthCheck) {
        HealthCheckDialog(container = container, onDismiss = { showHealthCheck = false })
    }
    if (showStarLedger) {
        StarLedgerDialog(container = container, onDismiss = { showStarLedger = false })
    }
    if (showMigration) {
        MigrationWizardDialog(container = container, onDismiss = { showMigration = false })
    }

    // ---- 自动备份周期选择（7 / 14 / 30 天） ----
    if (showAutoBackupPeriod) {
        AlertDialog(
            onDismissRequest = { showAutoBackupPeriod = false },
            containerColor = SurfaceRaise,
            title = { Text(text = L.setAutoBackupPeriod, style = TimartType.titleSerif) },
            text = {
                Column {
                    listOf(7, 14, 30).forEach { days ->
                        val selected = days == autoBackup.periodDays
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setAutoBackupPeriod(days)
                                    showAutoBackupPeriod = false
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = TimeGold,
                                    unselectedColor = InkSecondary,
                                ),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = L.setAutoBackupPeriodFmt.format(days),
                                style = TimartType.body,
                                color = InkPrimary,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAutoBackupPeriod = false }) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            },
        )

    // ---- 历史备份浏览（N4）：轮转目录现存份数 + 指定份恢复（复用 BackupImportDialog 预选件） ----
    if (showAutoBackupHistory) {
        LaunchedEffect(Unit) { vm.loadAutoBackupHistory() }
        val history by vm.autoBackupHistory.collectAsStateWithLifecycle()
        var restoreUri by remember { mutableStateOf<Uri?>(null) }
        AlertDialog(
            onDismissRequest = { showAutoBackupHistory = false },
            containerColor = SurfaceRaise,
            title = { Text(text = L.setAutoBackupHistory, style = TimartType.titleSerif) },
            text = {
                Column {
                    if (history.isEmpty()) {
                        Text(
                            text = L.setAutoBackupHistoryEmpty,
                            style = TimartType.body,
                            color = InkSecondary,
                        )
                    } else {
                        history.forEach { copy ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = copy.timestamp?.let { TimeFormatter.dateTime(it) }
                                            ?: copy.name,
                                        style = TimartType.body,
                                        color = InkPrimary,
                                    )
                                    copy.sizeBytes?.let { bytes ->
                                        Text(
                                            text = android.text.format.Formatter
                                                .formatShortFileSize(context, bytes),
                                            style = TimartType.caption,
                                            color = InkDisabled,
                                        )
                                    }
                                }
                                TextButton(onClick = { restoreUri = copy.uri }) {
                                    Text(text = L.setAutoBackupHistoryRestore, color = TimeGold)
                                }
                            }
                        }
                    }
                    Text(
                        text = L.setAutoBackupHistoryWarn,
                        style = TimartType.caption,
                        color = InkDisabled,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAutoBackupHistory = false }) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            },
        )
        // 指定份恢复：历史列表之上再弹口令校验（v2 导入链路含 Verifier + 二次确认文案）
        restoreUri?.let { uri ->
            BackupImportDialog(
                manager = container.backupManager,
                onDismiss = { restoreUri = null },
                presetUri = uri,
            )
        }
    }
    }

    // ---- 语言选择底部弹层（单选列表，选中即时生效并收起） ----
    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = SurfaceRaise,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = L.setLanguageCard,
                style = TimartType.titleSerif,
                color = InkPrimary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            AppLanguage.entries.forEach { option ->
                val selected = option == language
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.setLanguage(option)
                            showLanguageSheet = false
                            feedbackAt(langRowCenter)
                        }
                        .padding(horizontal = 24.dp),
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = TimeGold,
                            unselectedColor = InkSecondary,
                        ),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.padding(vertical = 14.dp)) {
                        Text(text = languageName(option, L), style = TimartType.body, color = InkPrimary)
                        if (option == AppLanguage.SYSTEM) {
                            Text(
                                text = L.langSystemDesc,
                                style = TimartType.caption,
                                color = InkSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** 更新与源码仓库（关于弹窗展示；仓库尚在构建中，占位口径） */
private const val REPO_URL = "https://github.com/muxiaowf/timart"

/** 反馈邮箱（关于弹窗展示；mailto: 唤起系统邮件客户端） */
private const val FEEDBACK_EMAIL = "timart@muxiaowf.top"

/**
 * 权限管理卡：应用全部可管理的系统授权入口（状态实时展示，ON_RESUME 回来后自动刷新）：
 * - 通知（API 33+ 运行时；更低版本系统免授权）；
 * - 精确定位（GPS 条件 / Wi-Fi 名称 / 附近城市；永久拒绝后引导应用详情页）；
 * - 身体活动（API 29+ 计步条件；更低版本免授权）；
 * - 定位服务（系统服务开关，非运行时权限）；
 * - 忽略电池优化（后台判定与步数累计的保活，用户可拒绝）。
 */
@Composable
private fun PermissionManageCard(modifier: Modifier = Modifier) {
    val L = LocalStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notifGranted by remember { mutableStateOf(false) }
    var locGranted by remember { mutableStateOf(false) }
    var activityGranted by remember { mutableStateOf(false) }
    var locServiceOn by remember { mutableStateOf(false) }
    var batteryIgnored by remember { mutableStateOf(false) }
    // 永久拒绝（申请过且系统不再弹窗）→ 只能引导应用详情页；首装未申请时仍走系统弹窗
    var locNeverAsk by remember { mutableStateOf(false) }
    var notifNeverAsk by remember { mutableStateOf(false) }
    var activityNeverAsk by remember { mutableStateOf(false) }
    var lastRequested by remember { mutableStateOf<String?>(null) }

    fun hasPerm(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun refresh() {
        val sdk = Build.VERSION.SDK_INT
        notifGranted = sdk < 33 || hasPerm(PERM_POST_NOTIFICATIONS)
        locGranted = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)
        activityGranted = sdk < 29 || hasPerm(PERM_ACTIVITY_RECOGNITION)
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        locServiceOn = lm?.let {
            it.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                it.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } ?: false
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        batteryIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // 拒绝且系统不再弹窗 → 标记永久拒绝，后续点击引导应用详情页
        val perm = lastRequested
        if (!granted && perm != null) {
            val activity = context as? Activity
            val neverAsk = activity != null &&
                !activity.shouldShowRequestPermissionRationale(perm)
            when (perm) {
                Manifest.permission.ACCESS_FINE_LOCATION -> locNeverAsk = neverAsk
                PERM_POST_NOTIFICATIONS -> notifNeverAsk = neverAsk
                PERM_ACTIVITY_RECOGNITION -> activityNeverAsk = neverAsk
            }
        }
        refresh()
    }

    fun openAppDetails() {
        runCatching {
            context.startActivity(
                Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null)),
            )
        }
    }

    fun requestOrOpen(permission: String, neverAsk: Boolean) {
        if (neverAsk) {
            openAppDetails()
        } else {
            lastRequested = permission
            launcher.launch(permission)
        }
    }

    SettingsCard(title = L.setPermCard, modifier = modifier) {
        val notifStatus = when {
            Build.VERSION.SDK_INT < 33 -> L.permNotRequired
            notifGranted -> L.permGranted
            else -> L.goEnable
        }
        PermissionStatusRow(
            label = L.permNotifLabel,
            description = L.permNotifDesc,
            status = notifStatus,
            action = Build.VERSION.SDK_INT >= 33 && !notifGranted,
            onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !notifGranted) {
                    requestOrOpen(PERM_POST_NOTIFICATIONS, notifNeverAsk)
                }
            },
        )
        PermissionStatusRow(
            label = L.permFineLocLabel,
            description = L.permLocationDesc,
            status = if (locGranted) L.permGranted else L.goEnable,
            action = !locGranted,
            onClick = { if (!locGranted) requestOrOpen(Manifest.permission.ACCESS_FINE_LOCATION, locNeverAsk) },
        )
        PermissionStatusRow(
            label = L.permActivityLabel,
            description = L.permActivityDesc,
            status = when {
                Build.VERSION.SDK_INT < 29 -> L.permNotRequired
                activityGranted -> L.permGranted
                else -> L.goEnable
            },
            action = Build.VERSION.SDK_INT >= 29 && !activityGranted,
            onClick = {
                if (Build.VERSION.SDK_INT >= 29 && !activityGranted) {
                    requestOrOpen(PERM_ACTIVITY_RECOGNITION, activityNeverAsk)
                }
            },
        )
        PermissionStatusRow(
            label = L.permLocServiceLabel,
            description = L.permLocServiceDesc,
            status = if (locServiceOn) L.permEnabled else L.permDisabled,
            action = !locServiceOn,
            onClick = {
                runCatching {
                    context.startActivity(Intent(SystemSettings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            },
        )
        PermissionStatusRow(
            label = L.permBatteryLabel,
            description = L.permBatteryDesc,
            status = if (batteryIgnored) L.permEnabled else L.goEnable,
            action = !batteryIgnored,
            onClick = {
                if (!batteryIgnored) {
                    // Play 政策合规：跳系统电池优化列表页由用户手动设置本应用为「不允许优化」；
                    // 此 action 无需 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限（Manifest 已移除该声明）
                    runCatching {
                        context.startActivity(
                            Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                        )
                    }
                }
            },
        )
    }
}

/** 权限状态行：标签 + 用途说明 + 行尾状态（金色 = 可操作，灰色 = 已授权/无需授权） */
@Composable
private fun PermissionStatusRow(
    label: String,
    description: String,
    status: String,
    action: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 文字与热区边缘留出水平空隙（波纹不贴字）
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = TimartType.body, color = InkPrimary)
            Text(
                text = description,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // 状态字与描述之间固定 12dp 间距（长描述换行时不贴字）
        Text(
            text = status,
            style = TimartType.caption,
            color = if (action) TimeGold else InkSecondary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** 设置分组卡：小注标题 + 内容（功能优先，不做玻璃拟态） */
@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceRaise)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = TimartType.sectionNote, color = InkSecondary)
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

/** 开关行：标签 + 描述 + M3 Switch（TimeGold 轨道）；整行可点切换（Switch 本身 onCheckedChange 置 null 避免双触发） */
@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onChanged,
            )
            // 文字与热区边缘留出水平空隙（波纹不贴字）
            .padding(start = 10.dp, end = 10.dp, top = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = TimartType.body, color = InkPrimary)
            Text(
                text = description,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // 尾部开关与描述列固定 12dp 间距（长描述换行时不贴字）
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.padding(start = 12.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepCharcoal,
                checkedTrackColor = TimeGold,
                checkedBorderColor = TimeGold,
                uncheckedThumbColor = InkDisabled,
                uncheckedTrackColor = SurfaceRaise,
                uncheckedBorderColor = InkDisabled,
            ),
        )
    }
}

/** 简单入口行：标签 + 描述 + 「进入」提示 */
@Composable
private fun SimpleRow(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    val L = LocalStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 文字与热区边缘留出水平空隙（波纹不贴字）
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = TimartType.body, color = InkPrimary)
            Text(
                text = description,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(text = L.enter, style = TimartType.caption, color = TimeGold, modifier = Modifier.padding(start = 12.dp))
    }
}

/** 信息说明弹窗（权限 / 天气 / 关于共用） */
@Composable
private fun InfoDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val L = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        title = { Text(text = title, style = TimartType.titleSerif) },
        text = {
            Column(content = content)
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
            ) {
                Text(text = L.ok)
            }
        },
    )
}

@Composable
private fun InfoParagraph(text: String) {
    Text(
        text = text,
        style = TimartType.caption,
        color = InkSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** 语言选项显示名（各语言用原文；「跟随系统」跟随当前文案表翻译） */
private fun languageName(option: AppLanguage, strings: Strings): String = when (option) {
    AppLanguage.SYSTEM -> strings.langSystem
    AppLanguage.ZH_HANS -> strings.langZhHans
    AppLanguage.ZH_HANT -> strings.langZhHant
    AppLanguage.EN -> strings.langEn
}

/** 自动备份目录的简短显示名（SAF tree id 的 `:` 后段；解析失败回退「已选择」） */
@Composable
private fun autoBackupDirName(treeUri: String?): String {
    val L = LocalStrings.current
    if (treeUri == null) return L.setAutoBackupDirNone
    return runCatching {
        android.provider.DocumentsContract.getTreeDocumentId(treeUri.toUri())
            .substringAfter(':')
            .ifBlank { null }
    }.getOrNull() ?: L.setAutoBackupDirChosen
}

/**
 * 年度星图报告弹窗（体验储备池 §5）：统计预览（今年/去年切换）→ 生成海报并分享。
 * 统计口径见 [AnnualReport]；海报复用 PosterComposer（FileProvider 分享，与详情页海报同路）。
 */
@Composable
private fun AnnualReportDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val thisYear = java.time.LocalDate.now().year
    var year by remember { mutableIntStateOf(thisYear) }
    var stats by remember { mutableStateOf<AnnualReport.AnnualStats?>(null) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(year) {
        stats = withContext(Dispatchers.IO) {
            val capsules = runCatching { container.capsuleRepository.allSync() }.getOrDefault(emptyList())
            val destroyed = runCatching { container.destroyedRepository.observeAll().first() }.getOrDefault(emptyList())
            AnnualReport.compute(capsules, destroyed, year, RuntimeSettings.resolvedLang)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!sharing) onDismiss() },
        containerColor = SurfaceRaise,
        title = { Text(text = L.setAnnualReport, style = TimartType.titleSerif) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(thisYear, thisYear - 1).forEach { option ->
                        val selected = option == year
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) TimeGold else SurfaceRaise)
                                .clickable(enabled = !sharing) { year = option }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = option.toString(),
                                style = TimartType.caption,
                                color = if (selected) DeepCharcoal else InkSecondary,
                            )
                        }
                    }
                }
                val current = stats
                if (current == null) {
                    Text(
                        text = L.reportComputing,
                        style = TimartType.caption,
                        color = InkSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = L.reportTitleFmt.format(current.year),
                            style = TimartType.titleSerif.copy(fontSize = 18.sp),
                            color = InkPrimary,
                        )
                        Text(
                            text = L.reportSealedFmt.format(current.sealedCount),
                            style = TimartType.body,
                            color = InkPrimary,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Text(
                            text = L.reportOpenedFmt.format(current.openedCount),
                            style = TimartType.body,
                            color = InkPrimary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = L.reportDustFmt.format(current.dustCount),
                            style = TimartType.body,
                            color = InkPrimary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        current.longestWaitTitle?.let { title ->
                            Text(
                                text = L.reportLongestWaitFmt.format(title, current.longestWaitDays),
                                style = TimartType.body,
                                color = InkPrimary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        current.topConditionKindName?.let { name ->
                            Text(
                                text = L.reportTopConditionFmt.format(name, current.topConditionCount),
                                style = TimartType.body,
                                color = InkPrimary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (stats != null) {
                TextButton(
                    onClick = {
                        val current = stats ?: return@TextButton
                        sharing = true
                        scope.launch(Dispatchers.IO) {
                            val poster = PosterComposer(context).compose(
                                AnnualReport.posterContent(current, RuntimeSettings.resolvedLang),
                            )
                            withContext(Dispatchers.Main) {
                                sharing = false
                                if (poster == null) {
                                    android.widget.Toast.makeText(context, L.posterFail, android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        poster,
                                    )
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = android.content.ClipData.newRawUri("poster", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(Intent.createChooser(send, L.posterShare)) }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.reportShare)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sharing) {
                Text(text = L.cancel, color = InkSecondary)
            }
        },
    )
}
