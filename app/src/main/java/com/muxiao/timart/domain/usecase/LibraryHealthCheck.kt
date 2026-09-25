package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule

/**
 * 整库体检（纯逻辑，可 JVM 单测；数据/文件侧扫描由 UI 层编排注入）：
 *
 * - 孤儿 meta 键：胶囊已物理删除但 `capsule.*.<id>` 记录残留（删除路径未覆盖的 key）；
 * - 依赖死链：`dependCapsuleId` 指向不存在的胶囊；
 * - 依赖锁死风险：依赖目标已 DESTROYED（依赖方将永远无法解锁，同 DependencyGraphUseCase 口径）；
 * - 依赖环：沿 dependCapsuleId 链回到自身；
 * - 分片材料不完整：`shardSalt/params/verifier/threshold/total` 五者应同非 null
 *   （见 `docs/spec-storage.md §3.1`），部分缺失 = 数据不一致；
 *
 * 前缀清单由数据层 [com.muxiao.timart.data.local.db.CapsuleMetaKeys] 传入
 * （key 契约单一事实源在数据层，本类只做纯分类，不持有前缀字面量）。
 */
object LibraryHealthCheck {

    /** 问题类别（UI 据此分组展示与决定可否清理） */
    enum class Kind { ORPHAN_META, DEAD_DEPENDENCY, DESTROYED_DEPENDENCY, CYCLE, SHARD_MATERIAL }

    /** 一条体检发现（detail = 面向展示的 id / key 串，文案由 UI 层按 kind 包装） */
    data class Finding(val kind: Kind, val detail: String)

    /**
     * 孤儿 meta 键：`capsule.*.<id>` 前缀下、id 不在现存胶囊集合中的 key。
     * condMet 的 id 段 = 去掉前缀后**最后一个 `.` 之前**的部分（`<id>.<index>` 两段式）；
     * 其余单段式 key 的 id = 去掉前缀后首个 `.` 之前（UUID 无 `.`，取全串亦等价）。
     */
    fun orphanMetaKeys(metaKeys: List<String>, capsuleIds: Set<String>, condMetPrefix: String, prefixes: List<String>): List<String> =
        metaKeys.mapNotNull { key ->
            val prefix = prefixes.firstOrNull { key.startsWith(it) } ?: return@mapNotNull null
            val remainder = key.removePrefix(prefix)
            val id = if (prefix == condMetPrefix) {
                remainder.substringBeforeLast('.')
            } else {
                remainder.substringBefore('.')
            }
            if (id.isEmpty() || id in capsuleIds) null else key
        }

    /** 死链 / 销毁依赖：(胶囊 id, 目标 id, 目标是否存在)。目标存在 = 销毁锁死风险，不存在 = 死链 */
    data class LinkProblem(val capsuleId: String, val targetId: String, val targetExists: Boolean)

    fun dependencyProblems(capsules: List<Capsule>): List<LinkProblem> {
        return capsules.mapNotNull { capsule ->
            val target = capsule.dependCapsuleId ?: return@mapNotNull null
            if (target == capsule.id) return@mapNotNull null // 自依赖在创建侧已禁止，防御性跳过
            when (capsules.find { it.id == target }) {
                null -> LinkProblem(capsule.id, target, targetExists = false)
                else -> null
            }
        } + capsules
            .filter { capsule ->
                val target = capsule.dependCapsuleId ?: return@filter false
                target != capsule.id &&
                    capsules.find { it.id == target }?.state == com.muxiao.timart.domain.model.CapsuleState.DESTROYED
            }
            .map { LinkProblem(it.id, it.dependCapsuleId!!, targetExists = true) }
    }

    /** 依赖环：返回陷入环中的胶囊 id（沿链回到自身的节点） */
    fun cycleCapsuleIds(capsules: List<Capsule>): List<String> {
        val dependencyOf = capsules.associate { it.id to it.dependCapsuleId }
        return capsules.mapNotNull { capsule ->
            var current: String? = capsule.dependCapsuleId
            val visited = mutableSetOf<String>()
            while (current != null) {
                if (current == capsule.id) return@mapNotNull capsule.id
                if (!visited.add(current)) return@mapNotNull if (capsule.id in visited) capsule.id else null
                current = dependencyOf[current]
            }
            null
        }.distinct()
    }

    /** 分片材料不完整的胶囊 id：五字段应同非 null 且 1 ≤ threshold ≤ total */
    fun incompleteShardMaterials(capsules: List<Capsule>): List<String> =
        capsules.filter { c ->
            val fields = listOf(c.shardSalt, c.shardParams, c.shardVerifier, c.shardThreshold, c.shardTotal)
            val any = fields.any { it != null }
            val all = fields.all { it != null }
            when {
                !any -> false // 普通胶囊
                !all -> true // 部分缺失 = 不一致
                else -> {
                    val threshold = c.shardThreshold!!
                    val total = c.shardTotal!!
                    threshold < 1 || total < threshold
                }
            }
        }.map { it.id }

    /** 一次性全量纯扫描（UI 编排一次调用） */
    fun scan(
        capsules: List<Capsule>,
        metaKeys: List<String>,
        condMetPrefix: String,
        prefixes: List<String>,
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        val orphans = orphanMetaKeys(metaKeys, capsules.map { it.id }.toSet(), condMetPrefix, prefixes)
        if (orphans.isNotEmpty()) findings.add(Finding(Kind.ORPHAN_META, orphans.joinToString("\n")))
        for (problem in dependencyProblems(capsules)) {
            findings.add(
                Finding(
                    if (problem.targetExists) Kind.DESTROYED_DEPENDENCY else Kind.DEAD_DEPENDENCY,
                    "${problem.capsuleId} → ${problem.targetId}",
                ),
            )
        }
        val cycles = cycleCapsuleIds(capsules)
        if (cycles.isNotEmpty()) findings.add(Finding(Kind.CYCLE, cycles.joinToString(" · ")))
        val shards = incompleteShardMaterials(capsules)
        if (shards.isNotEmpty()) findings.add(Finding(Kind.SHARD_MATERIAL, shards.joinToString(" · ")))
        return findings
    }
}
