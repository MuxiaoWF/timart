package com.muxiao.timart.ui.starlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxiao.timart.AppContainer
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
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
 * 已解锁星库 VM（架构 §2.17）：UNLOCKED 集合 + 最近解锁排序 + 标签筛选。
 * 重读不在此判定（MainActivity 全量判定负责持久化），点击直接进详情（450ms 简化过渡在详情页）。
 */
class StarLibraryViewModel(container: AppContainer) : ViewModel() {

    private val repo = container.capsuleRepository
    private val crud = container.capsuleCrudUseCase

    /** 全部 UNLOCKED 胶囊（最近解锁在前） */
    val unlocked: StateFlow<List<Capsule>> = repo.observeAll()
        .map { list -> list.filter { it.state == CapsuleState.UNLOCKED } }
        .map { list -> list.sortedByDescending { it.unlockTimestamp ?: it.createTimestamp } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTag = MutableStateFlow<String?>(null)

    /** 当前筛选标签（null = 全部） */
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    /** 筛选后的展示列表 */
    val filtered: StateFlow<List<Capsule>> = combine(unlocked, _selectedTag) { list, tag ->
        if (tag == null) list else list.filter { tag in it.tags }
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
