package com.muxiao.timart.ui.starlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxiao.timart.domain.model.DestroyRecord
import com.muxiao.timart.domain.repository.DestroyedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 尘迹档案页 VM：
 * - [records] 由 Room Flow 驱动（按销毁时间倒序），删除后自动回流；
 * - [deleteRecord] 仅删除元记录——内容早在销毁时已物理删除，档案只是最后一行字。
 */
class DustRecordsViewModel(private val destroyedRepository: DestroyedRepository) : ViewModel() {

    /** 全部销毁记录 */
    val records: StateFlow<List<DestroyRecord>> = destroyedRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
