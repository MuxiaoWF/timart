package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Lang
import com.muxiao.timart.domain.model.Capsule

/**
 * 依赖图校验（创建/修改依赖时调用）：
 * 环检测（DFS 沿 dependCapsuleId 链）、死链（不存在或 DESTROYED）、自依赖、
 * 自动销毁依赖强警告。返回 error/severity 结果对象，不抛异常。
 */
class DependencyGraphUseCase {

    /**
     * 环检测：若"from → candidate"这条新依赖边将形成环则返回 true。
     * 沿 candidate 的依赖链向下走，再次回到 fromId 即成环。
     */
    fun detectCycle(fromId: String, candidateDepId: String, all: List<Capsule>): Boolean {
        val dependencyOf = all.associate { it.id to it.dependCapsuleId }
        var current: String? = candidateDepId
        val visited = mutableSetOf<String>()
        while (current != null) {
            if (current == fromId) return true
            if (!visited.add(current)) return true // 已存在环（防御性）
            current = dependencyOf[current]
        }
        return false
    }

    /**
     * 校验候选依赖设置（candidate.dependCapsuleId 为待校验目标）。
     * 返回 null = 通过；返回 [DependencyError] 时 severity=ERROR 禁止保存，
     * severity=WARNING 需用户二次确认。
     */
    fun validateCandidate(candidate: Capsule, all: List<Capsule>, lang: Lang = Lang.ZH_HANS): DependencyError? {
        val m = depMsgs(lang)
        val depId = candidate.dependCapsuleId ?: return null

        // 自依赖
        if (depId == candidate.id) {
            return DependencyError(
                issue = DependencyIssue.SELF_DEPENDENCY,
                severity = DependencySeverity.ERROR,
                message = m.self,
            )
        }

        val target = all.find { it.id == depId }
            // 死链：目标不存在
            ?: return DependencyError(
                issue = DependencyIssue.DEAD_LINK,
                severity = DependencySeverity.ERROR,
                message = m.notFound,
            )

        // 死链防护：目标已销毁
        if (target.state == com.muxiao.timart.domain.model.CapsuleState.DESTROYED) {
            return DependencyError(
                issue = DependencyIssue.DEAD_LINK,
                severity = DependencySeverity.ERROR,
                message = m.destroyed,
            )
        }

        // 环检测
        if (detectCycle(candidate.id, depId, all)) {
            return DependencyError(
                issue = DependencyIssue.CYCLE,
                severity = DependencySeverity.ERROR,
                message = m.cycle,
            )
        }

        // 自动销毁依赖强警告（确认后可保存）
        if (target.autoDestroyAfterRead) {
            return DependencyError(
                issue = DependencyIssue.AUTO_DESTROY_RISK,
                severity = DependencySeverity.WARNING,
                message = m.readDestroy,
            )
        }

        return null
    }
}

/** 依赖问题类型 */
enum class DependencyIssue {
    SELF_DEPENDENCY,
    CYCLE,
    DEAD_LINK,
    AUTO_DESTROY_RISK,
}

/** 严重级别：ERROR 禁止保存；WARNING 需二次确认 */
enum class DependencySeverity { ERROR, WARNING }

/** 校验结果对象 */
data class DependencyError(
    val issue: DependencyIssue,
    val severity: DependencySeverity,
    val message: String,
)


/** 依赖校验错误文案（单语言词表） */
private data class DepMsgs(
    val self: String,
    val notFound: String,
    val destroyed: String,
    val cycle: String,
    val readDestroy: String,
)

private fun depMsgs(lang: Lang): DepMsgs = when (lang) {
    Lang.ZH_HANS -> DepMsgs(
        self = "不能选择自己作为依赖",
        notFound = "依赖的胶囊不存在",
        destroyed = "依赖胶囊已销毁，无法作为前置依赖",
        cycle = "不能形成循环依赖",
        readDestroy = "该胶囊阅读销毁后，本胶囊将永久无法解锁",
    )
    Lang.ZH_HANT -> DepMsgs(
        self = "不能選擇自己作為依賴",
        notFound = "依賴的膠囊不存在",
        destroyed = "依賴膠囊已銷毀，無法作為前置依賴",
        cycle = "不能形成循環依賴",
        readDestroy = "該膠囊閱讀銷毀後，本膠囊將永久無法解鎖",
    )
    Lang.EN -> DepMsgs(
        self = "A capsule can't depend on itself",
        notFound = "The source capsule doesn't exist",
        destroyed = "The source capsule was destroyed and can't be a dependency",
        cycle = "This would create a circular dependency",
        readDestroy = "That capsule destroys itself after reading; this capsule would never unlock",
    )
}
