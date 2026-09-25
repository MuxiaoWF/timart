package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.ConditionKind
import com.muxiao.timart.domain.model.unlock.conditionKind

/**
 * 星库志统计（N6）：全库元数据聚合，纯 Kotlin 零 android 依赖，可 JVM 单测。
 *
 * 口径：
 * - **密文不参与**——只统计状态、时间戳与规则结构（ConditionKind 大类），不解密任何内容；
 * - 「累计封存」= 现存胶囊 + 尘迹档案数（物理删除但留档的记录仍计入生平）；
 * - 开启率只对现存胶囊计（尘迹无「开启态」语义）；
 * - 「最长等待」：LOCKED 用 now − create，UNLOCKED 用 unlock − create，取全库最大值；
 * - 「平均封存时长」：仅对有 unlockTimestamp 的 UNLOCKED 胶囊求均值（无样本返回 null）。
 */
object StarLedgerStatsCalculator {

    data class Stats(
        val totalSealed: Int,
        val currentlyLocked: Int,
        val currentlyUnlocked: Int,
        val destroyedArchives: Int,
        /** 开启率（0–100 整数百分比；现存胶囊为 0 时返回 0） */
        val openRatePercent: Int,
        /** 最长等待毫秒数（无任何可计样本时 null） */
        val longestWaitMs: Long?,
        /** 已开启胶囊的封存→开启平均时长毫秒（无样本 null） */
        val avgSealToOpenMs: Long?,
        /** 条件大类分布（现存胶囊规则展平计数；不含尘迹——规则已随销毁不可考） */
        val kindCounts: Map<ConditionKind, Int>,
        /** 标签 Top（出现次数降序，最多 `topTagsLimit` 个；无标签空表） */
        val topTags: List<String>,
    )

    /**
     * @param capsules 现存胶囊全量（任意状态）
     * @param destroyedCount 尘迹档案数
     * @param now 当前时刻毫秒（注入以便单测）
     */
    fun compute(
        capsules: List<Capsule>,
        destroyedCount: Int,
        now: Long,
        topTagsLimit: Int = 5,
    ): Stats {
        val locked = capsules.count { it.state == CapsuleState.LOCKED }
        val unlockedList = capsules.filter { it.state == CapsuleState.UNLOCKED }
        val openRate = (locked + unlockedList.size).takeIf { it > 0 }
            ?.let { unlockedList.size * 100 / it }
            ?: 0

        val longestWait = capsules.maxOfOrNull { capsule ->
            when (capsule.state) {
                CapsuleState.UNLOCKED -> capsule.unlockTimestamp?.let { it - capsule.createTimestamp }
                    ?: Long.MIN_VALUE
                CapsuleState.LOCKED -> now - capsule.createTimestamp
                CapsuleState.DESTROYED -> Long.MIN_VALUE
            }
        }?.takeIf { it > 0 }

        val sealToOpen = unlockedList.mapNotNull { capsule ->
            capsule.unlockTimestamp?.let { unlock -> unlock - capsule.createTimestamp }
        }
        val avgSealToOpen = sealToOpen.takeIf { it.isNotEmpty() }
            ?.let { list -> list.sumOf { it } / list.size }

        val kindCounts = countKinds(capsules)
        val topTags = capsules.flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(topTagsLimit)
            .map { it.key }

        return Stats(
            totalSealed = capsules.size + destroyedCount,
            currentlyLocked = locked,
            currentlyUnlocked = unlockedList.size,
            destroyedArchives = destroyedCount,
            openRatePercent = openRate,
            longestWaitMs = longestWait,
            avgSealToOpenMs = avgSealToOpen,
            kindCounts = kindCounts,
            topTags = topTags,
        )
    }

    /** 条件大类展平计数：每颗胶囊规则里的每条条件各计一次 */
    private fun countKinds(capsules: List<Capsule>): Map<ConditionKind, Int> {
        val counts = mutableMapOf<ConditionKind, Int>()
        for (capsule in capsules) {
            for (condition in capsule.unlockRule.conditionList) {
                val kind = conditionKind(condition)
                counts[kind] = (counts[kind] ?: 0) + 1
            }
        }
        return counts
    }
}
