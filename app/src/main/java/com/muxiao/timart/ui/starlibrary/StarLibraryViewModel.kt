package com.muxiao.timart.ui.starlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxiao.timart.AppContainer
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.ConditionKind
import com.muxiao.timart.domain.model.unlock.conditionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 已解锁星库 VM（架构 §2.17）：UNLOCKED 集合 + 最近解锁排序 + 标签筛选
 * + 检索与条件大类筛选（星库检索：标题包含匹配（忽略大小写）+ ConditionKind 五大类；
 * 储备池 v6 起，口令会话已解锁时正文也参与检索——按需解密建**纯内存索引**，
 * 不落盘、VM 销毁即焚；会话未解锁时退回仅标题检索。分片胶囊内层仍锁，正文不参与）。
 * 重读不在此判定（MainActivity 全量判定负责持久化），点击直接进详情（450ms 简化过渡在详情页）。
 */
class StarLibraryViewModel(container: AppContainer) : ViewModel() {

    private val repo = container.capsuleRepository
    private val crud = container.capsuleCrudUseCase
    private val reader = container.readCapsuleUseCase
    private val crypto = container.contentCryptoManager

    /** 全部 UNLOCKED 胶囊（最近解锁在前） */
    val unlocked: StateFlow<List<Capsule>> = repo.observeAll()
        .map { list -> list.filter { it.state == CapsuleState.UNLOCKED } }
        .map { list -> list.sortedByDescending { it.unlockTimestamp ?: it.createTimestamp } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTag = MutableStateFlow<String?>(null)

    /** 当前筛选标签（null = 全部） */
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    /** 标题检索词（空 = 不过滤；口令会话已解锁时正文一并匹配） */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 条件大类筛选（null = 全部；按解锁规则内任一条件归属判定） */
    private val _category = MutableStateFlow<ConditionKind?>(null)
    val category: StateFlow<ConditionKind?> = _category.asStateFlow()

    // ---- 会话内正文检索索引（储备池 v6；纯内存，退出页面/会话即焚） ----

    /** capsuleId → 正文聚合文本；只缓存本会话解密过的胶囊 */
    private val contentIndex = HashMap<String, String>()

    /** 索引版本号：后台解密填充完成后自增，驱动 filtered 重算 */
    private val indexVersion = MutableStateFlow(0)

    /** 口令会话是否已解锁（解锁才可能做正文检索；读失败按未解锁处理） */
    private val sessionUnlocked: Boolean
        get() = runCatching { crypto.ensureUnlocked() }.getOrDefault(false)

    /** 筛选后的展示列表（标签 × 检索 × 条件大类三路过滤） */
    val filtered: StateFlow<List<Capsule>> = combine(
        unlocked,
        _selectedTag,
        _query,
        _category,
        indexVersion,
    ) { list, tag, query, category, _ ->
        val q = query.trim()
        list.filter { capsule ->
            (tag == null || tag in capsule.tags) &&
                (
                    q.isEmpty() ||
                        capsule.title.contains(q, ignoreCase = true) ||
                        contentIndex[capsule.id]?.contains(q, ignoreCase = true) == true
                    ) &&
                (category == null || capsule.unlockRule.conditionList.any { conditionKind(it) == category })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 全部标签（按出现频次降序，去重） */
    val tags: StateFlow<List<String>> = unlocked.map { list ->
        list.flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 切换标签筛选（再次点击同标签 = 取消筛选） */
    fun selectTag(tag: String?) {
        _selectedTag.value = if (_selectedTag.value == tag) null else tag
    }

    fun setQuery(value: String) {
        _query.value = value
        val q = value.trim()
        // 口令会话已解锁且检索词非空 → 后台按需解密缺失胶囊正文，入索引后驱动重算。
        // 解密失败（会话中途失效/分片内层未解）逐颗跳过：该胶囊退回仅标题匹配
        if (q.isNotEmpty() && sessionUnlocked) {
            viewModelScope.launch(Dispatchers.IO) {
                val targets = unlocked.value.filter { it.id !in contentIndex && it.shardVerifier == null }
                var changed = false
                targets.forEach { capsule ->
                    runCatching {
                        contentIndex[capsule.id] = reader.read(capsule).paragraphs.joinToString("\n")
                        changed = true
                    }
                }
                if (changed) indexVersion.value += 1
            }
        } else if (q.isEmpty() && contentIndex.isNotEmpty()) {
            // 检索清空即焚索引：明文常驻内存的窗口收窄到「正在检索」期间
            contentIndex.clear()
            indexVersion.value += 1
        }
    }

    /** 切换条件大类筛选（再次点击同大类 = 取消筛选） */
    fun selectCategory(kind: ConditionKind?) {
        _category.value = if (_category.value == kind) null else kind
    }

    /**
     * 批量删除已解锁胶囊（长按多选 + 二次确认后调用）：
     * 走 CRUD 的物理删除（内容 + 图片目录 + 元记录），不写销毁档案（内容还在，非"销毁"语义）。
     */
    fun deleteSelected(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { crud.delete(it) }
        }
    }

    /**
     * 批量归为销毁（长按多选 + 二次确认后调用）：
     * 走 CRUD 的 markDestroyed（内容物理删除 + 尘迹档案 + DESTROYED 状态），
     * 与「删除」的语义差异 = 留不留一行尘迹档案。
     */
    fun destroySelected(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { runCatching { crud.markDestroyed(it) } }
        }
    }
}
