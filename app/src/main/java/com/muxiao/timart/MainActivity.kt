package com.muxiao.timart

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.data.local.db.entity.MetaEntity
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.l10n.rememberStrings
import com.muxiao.timart.ui.components.PermissionGuideDialog
import com.muxiao.timart.ui.navigation.NavGraph
import com.muxiao.timart.ui.theme.TimartTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 唯一 Activity：setContent { TimartTheme { NavGraph(container) } }。
 * ON_RESUME 生命周期钩子触发全量前台判定（协程，Default 派发）——
 * 判定结果经 Room Flow 自然刷新 UI，Activity 不持有判定状态。
 * T14：通知权限引导（33+，说明先于系统弹窗，仅引导一次，拒绝不影响判定）。
 */
// BiometricPrompt（androidx.biometric）要求宿主为 FragmentActivity；
// FragmentActivity 继承 ComponentActivity，Compose 承载方式不变
class MainActivity : FragmentActivity() {

    /** 前台判定作用域（独立于 UI 重组，ON_DESTROY 取消） */
    private val judgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 通知权限引导是否显示（33+ 且从未引导过时置 true） */
    private var showNotifGuide by mutableStateOf(false)

    /**
     * 本进程内已确认落库的「条件达成时刻」键（`capsuleId:index`）：
     * ON_RESUME 全量判定每轮都会对已满足条件重复回调，内存去重避免反复读 meta。
     * 契约见 CapsuleMetaKeys（写入点本类 + DetailViewModel.judgeOnce，读取点 DustRecordsViewModel）。
     */
    private val condMetSeen = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** 通知权限申请：PermissionGuideDialog 确认后触发系统弹窗 */
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TimartApplication).container
        handleNfcLinkIntent(container, intent)

        // ON_RESUME → 全量前台判定（含 GPS）
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // 判定前记录一次会话（10 分钟去重）：打开次数/连击/上次打开条件的输入
                    container.usageStatsTracker.recordOpen()
                    judgeScope.launch {
                        container.unlockJudgeUseCase.judgeAllLocked(
                            ctx = container.defaultContext(foregroundOnly = false),
                            repo = container.capsuleRepository,
                            onUnlocked = { capsule ->
                                container.notifier.notifyUnlock(capsule.id, capsule.title)
                                container.playCapsuleHaptic(capsule.id, destroy = false)
                            },
                            lang = RuntimeSettings.resolvedLang,
                            onConditionSatisfied = { capsuleId, index, _ ->
                                val seenKey = "$capsuleId:$index"
                                if (condMetSeen.putIfAbsent(seenKey, true) == null) {
                                    recordCondMet(container, capsuleId, index)
                                }
                            },
                            shouldJudge = { capsule -> !container.isSeedDormant(capsule.id) },
                        )
                    }
                    maybeGuideNotificationPermission(container)
                }
            },
        )

        setContent {
            // 语言词表根部提供：组合期读取 RuntimeSettings.appLanguage 快照状态，
            // 设置页切换语言后全树换文案即时重组
            CompositionLocalProvider(LocalStrings provides rememberStrings()) {
                TimartTheme {
                    val L = LocalStrings.current
                    NavGraph(container)
                    if (showNotifGuide) {
                        PermissionGuideDialog(
                            title = L.notifGuideTitle,
                            body = L.permNotifDesc,
                            onConfirm = {
                                // 标记在用户明确选择后才写入：确认/暂不都算「已问过」（保持仅一次），
                                // 但弹窗展示前不再预写——避免进程中断导致永不再引导
                                markNotifGuideAsked(container)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                showNotifGuide = false
                            },
                            onDismiss = {
                                markNotifGuideAsked(container)
                                showNotifGuide = false
                            },
                        )
                    }
                }
            }
        }
    }

    /**
     * 通知权限引导（33+，仅一次）：meta 标记 `app.notifGuideAsked`（用户交互后写入，
     * 见 setContent 内 PermissionGuideDialog 回调）；说明先于系统弹窗（PermissionGuideDialog），
     * 拒绝仅收不到提醒、不影响判定。
     */    private fun maybeGuideNotificationPermission(container: AppContainer) {
        if (!container.permissionHelper.shouldRequestPostNotifications()) return
        judgeScope.launch(Dispatchers.IO) {
            val metaDao = container.database.metaDao()
            val asked = runCatching { metaDao.get(FLAG_NOTIF_GUIDE_ASKED) }.getOrNull() == "true"
            if (asked) return@launch
            withContext(Dispatchers.Main) { showNotifGuide = true }
        }
    }

    /** 用户对引导弹窗做出明确选择后落 meta 标记（幂等） */
    private fun markNotifGuideAsked(container: AppContainer) {
        judgeScope.launch(Dispatchers.IO) {
            runCatching {
                container.database.metaDao().put(MetaEntity(FLAG_NOTIF_GUIDE_ASKED, "true"))
            }
        }
    }

    /**
     * 「条件达成时刻」埋点：meta `capsule.condMet.<id>.<index>` 只写不覆写（先到先记），
     * 供尘迹生平页达成时刻线使用。写失败静默（缺刻度的条件在生平页显示为「—」）。
     */
    private fun recordCondMet(container: AppContainer, capsuleId: String, index: Int) {
        judgeScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = container.database.metaDao()
                val key = com.muxiao.timart.data.local.db.CapsuleMetaKeys.condMet(capsuleId, index)
                if (dao.get(key) == null) {
                    dao.put(MetaEntity(key, container.timeProvider.nowMillis().toString()))
                }
            }
        }
    }

    /** NFC 实体锚点分发（体验储备池 §4）：`timart.com:link` 记录 → 待直达胶囊 id（NavGraph 消费后清空） */
    private fun handleNfcLinkIntent(container: AppContainer, intent: android.content.Intent?) {
        val capsuleId = intent?.let { com.muxiao.timart.utils.device.NfcCardWriter.parseLinkCapsuleId(it) }
        if (!capsuleId.isNullOrEmpty()) {
            container.pendingNfcCapsuleId = capsuleId
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // singleTask：应用在前时碰卡走 onNewIntent；先 setIntent 再解析
        setIntent(intent)
        handleNfcLinkIntent((application as TimartApplication).container, intent)
    }

    override fun onDestroy() {
        judgeScope.cancel()
        super.onDestroy()
    }

    // 音量键转发（VolumeKeyCombo 挑战判定输入；不拦截系统行为）
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == com.muxiao.timart.utils.app.VolumeKeyEventBus.KEY_UP ||
            keyCode == com.muxiao.timart.utils.app.VolumeKeyEventBus.KEY_DOWN
        ) {
            com.muxiao.timart.utils.app.VolumeKeyEventBus.dispatch(keyCode, true)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == com.muxiao.timart.utils.app.VolumeKeyEventBus.KEY_UP ||
            keyCode == com.muxiao.timart.utils.app.VolumeKeyEventBus.KEY_DOWN
        ) {
            com.muxiao.timart.utils.app.VolumeKeyEventBus.dispatch(keyCode, false)
        }
        return super.onKeyUp(keyCode, event)
    }

    private companion object {
        const val FLAG_NOTIF_GUIDE_ASKED = "app.notifGuideAsked"
    }
}
