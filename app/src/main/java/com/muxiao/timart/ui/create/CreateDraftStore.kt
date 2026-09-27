package com.muxiao.timart.ui.create

import com.muxiao.timart.domain.model.unlock.LogicType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 创建草稿自动保存（落 meta `settings.createDraft`，单槽覆盖写）：
 * 三步表单的文本与规则快照，进程被杀 / 误退出后可从创建页首步恢复。
 *
 * 隐私口径：草稿是**封存前的明文**，随 meta 明文落库（口令加密只保护已封存胶囊）——
 * 恢复入口 UI 必须明示「草稿未加密」。图片与语音字节不参与草稿（体积大且未加密更敏感），
 * 恢复后需重新添加；城市天气快照 / 依赖 / 拼图 / 嵌套种子 / 分片等封存期特化项同样不进草稿。
 * 封存成功、用户显式丢弃、清空全部内容时清槽。键非 `capsule.*` 前缀，不参与体检孤儿扫描。
 */
@Serializable
data class CreateDraftData(
    val savedAt: Long = 0L,
    val step: Int = 0,
    val title: String = "",
    val content: String = "",
    val note: String = "",
    val question: String = "",
    val sealStyle: Int = 0,
    val paperStyle: Int = 0,
    val chaptersEnabled: Boolean = false,
    val chapterTexts: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val autoDestroy: Boolean = false,
    val blindBox: Boolean = false,
    val logic: String = LogicType.AND.name,
    val logicThreshold: Int = 2,
    val ruleJson: String? = null,
)

object CreateDraftStore {

    /** 草稿 meta key（唯一写入点 = CreateViewModel 自动保存；唯一读取点 = CreateViewModel 装载与清槽） */
    const val DRAFT_KEY = "settings.createDraft"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(data: CreateDraftData): String =
        json.encodeToString(CreateDraftData.serializer(), data)

    fun decode(text: String): CreateDraftData? =
        runCatching { json.decodeFromString(CreateDraftData.serializer(), text) }.getOrNull()

    /** 全空草稿（无任何可恢复内容）：调用方据此清槽而不是落库 */
    fun isEmpty(data: CreateDraftData): Boolean =
        data.title.isBlank() &&
            data.content.isBlank() &&
            data.note.isBlank() &&
            data.question.isBlank() &&
            data.tags.isEmpty() &&
            data.ruleJson == null
}
