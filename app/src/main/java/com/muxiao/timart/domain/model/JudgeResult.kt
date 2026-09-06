package com.muxiao.timart.domain.model

import com.muxiao.timart.domain.context.DependencyStatus
import com.muxiao.timart.domain.model.unlock.UnlockCondition

/**
 * 单颗胶囊的判定结果（前台页面与 Worker 共用同一结构）。
 *
 * @param overallOk        是否满足解锁总条件（依赖 + 条件合并）
 * @param dependencyOk     依赖胶囊是否解锁（无依赖时恒为 true）
 * @param dependencyStatus 依赖胶囊状态（无依赖时为 UNLOCKED）
 * @param items            每条条件的独立判定结果，供条件时间线展示
 */
data class JudgeResult(
    val overallOk: Boolean,
    val dependencyOk: Boolean,
    val dependencyStatus: DependencyStatus = DependencyStatus.UNLOCKED,
    val items: List<ConditionStatus> = emptyList(),
)

/** 单条条件的判定状态 */
data class ConditionStatus(
    val condition: UnlockCondition,
    val satisfied: Boolean,
    /** 后台场景跳过判定（如 Worker 不检测 GPS），按不满足参与合并 */
    val skipped: Boolean = false,
    /** 不满足时的原因文案（null = 满足或无需解释） */
    val reason: String? = null,
)
