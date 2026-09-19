package com.muxiao.timart.ui.starlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxiao.timart.data.local.db.CapsuleMetaKeys
import com.muxiao.timart.data.local.db.MetaDao
import com.muxiao.timart.domain.model.DestroyRecord
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.repository.CapsuleRepository
import com.muxiao.timart.domain.repository.DestroyedRepository
import com.muxiao.timart.utils.RuntimeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 尘迹档案页 VM：
 * - [records] 由 Room Flow 驱动（按销毁时间倒序），删除后自动回流；
 * - [deleteRecord] 仅删除元记录——内容早在销毁时已物理删除，档案只是最后一行字；
 * - [loadBiography]「胶囊的一生」（体验储备池 §5）：销毁只删密文与图片，DESTROYED 胶囊行
 *   保留元信息（笔记/标签/规则/时间戳），配合 meta 刻度还原封存→达成→开启→归尘全程。
 */
class DustRecordsViewModel(
    private val destroyedRepository: DestroyedRepository,
    private val capsuleRepository: CapsuleRepository,
    private val metaDao: MetaDao,
) : ViewModel() {

    /** 全部销毁记录 */
    val records: StateFlow<List<DestroyRecord>> = destroyedRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 生平数据（按需加载，一次性回调） */
    data class Biography(
        val record: DestroyRecord,
        /** 胶囊行是否仍在（星库「删除」为物理删除，行不在时仅剩时间跨度可展示） */
        val capsuleExists: Boolean,
        /** 封存笔记（胶囊行已被「删除」物理移除时为 null） */
        val note: String?,
        val tags: List<String>,
        /** 条件达成时刻线（index = 规则条件序；metAt 为 null = 无刻度，显示「—」） */
        val conditions: List<ConditionMoment>,
        /** 开启时刻（从未开启 / 胶囊行被删为 null） */
        val unlockedAt: Long?,
        /** 凝视次数与累计时长（秒；无记录为 null） */
        val viewCount: Int?,
        val watchSeconds: Int?,
        /** 回信（体验储备池 §5；销毁不清理该 meta，对话留在档案里） */
        val reply: String?,
    ) {
        data class ConditionMoment(
            val index: Int,
            val sentence: String?,
            val metAt: Long?,
        )
    }

    /**
     * 装配一颗胶囊的生平：DESTROYED 胶囊行的元信息 + meta 刻度
     * （capsule.condMet.* 达成时刻 / capsule.views.* 凝视 / capsule.watch.* 时长）。
     * 胶囊行若被星库「删除」（物理删除，不留本体），只剩档案行的时间跨度。
     */
    fun loadBiography(record: DestroyRecord, onLoaded: (Biography) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val capsule = runCatching { capsuleRepository.byIdSync(record.id) }.getOrNull()
            val conditions = capsule?.unlockRule?.conditionList?.mapIndexed { index, condition ->
                Biography.ConditionMoment(
                    index = index,
                    sentence = ConditionText.conditionSentence(condition, RuntimeSettings.resolvedLang),
                    metAt = runCatching {
                        metaDao.get(CapsuleMetaKeys.condMet(record.id, index))?.toLongOrNull()
                    }.getOrNull(),
                )
            }.orEmpty()
            val biography = Biography(
                record = record,
                capsuleExists = capsule != null,
                note = capsule?.createNote?.takeIf { it.isNotBlank() },
                tags = capsule?.tags ?: emptyList(),
                conditions = conditions,
                unlockedAt = capsule?.unlockTimestamp,
                viewCount = runCatching {
                    metaDao.get("capsule.views.${record.id}")?.toIntOrNull()
                }.getOrNull(),
                watchSeconds = runCatching {
                    metaDao.get("capsule.watch.${record.id}")?.toIntOrNull()
                }.getOrNull(),
                reply = runCatching {
                    metaDao.get(CapsuleMetaKeys.reply(record.id))
                }.getOrNull(),
            )
            withContext(Dispatchers.Main) { onLoaded(biography) }
        }
    }

    /** 删除一条档案记录（确认弹窗之后调用；UI 先在该行位置放 DISSOLVE 粒子） */
    fun deleteRecord(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            destroyedRepository.delete(id)
        }
    }

    /** 批量删除档案记录（长按多选 + 二次确认后调用） */
    fun deleteRecords(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { destroyedRepository.delete(it) }
        }
    }
}
