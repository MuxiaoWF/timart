package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 整库体检纯逻辑单测（LibraryHealthCheck）：
 * 孤儿 meta 键（含 condMet 两段式 id）/ 依赖死链 / 销毁锁死 / 依赖环 / 分片材料 / scan 汇总。
 */
class LibraryHealthCheckTest {

    private val check = LibraryHealthCheck

    private fun capsule(
        id: String,
        dependCapsuleId: String? = null,
        state: CapsuleState = CapsuleState.LOCKED,
        shardSalt: String? = null,
        shardParams: String? = null,
        shardVerifier: String? = null,
        shardThreshold: Int? = null,
        shardTotal: Int? = null,
    ) = Capsule(
        id = id,
        title = "胶囊$id",
        contentCipher = null,
        createTimestamp = 0L,
        unlockRule = UnlockRule(LogicType.AND, listOf(UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1)))),
        state = state,
        dependCapsuleId = dependCapsuleId,
        shardSalt = shardSalt,
        shardParams = shardParams,
        shardVerifier = shardVerifier,
        shardThreshold = shardThreshold,
        shardTotal = shardTotal,
    )

    // ---- 1. 孤儿 meta 键 ----

    @Test
    fun orphanKeysDetectedForMissingCapsule() {
        val keys = listOf(
            "capsule.read.aaa",
            "capsule.read.bbb",
            "capsule.haptic.ccc",
        )
        val orphans = check.orphanMetaKeys(
            metaKeys = keys,
            capsuleIds = setOf("aaa"),
            condMetPrefix = "capsule.condMet.",
            prefixes = listOf("capsule.read.", "capsule.haptic.", "capsule.condMet."),
        )
        assertEquals(listOf("capsule.read.bbb", "capsule.haptic.ccc"), orphans)
    }

    @Test
    fun condMetTwoSegmentIdParsedBeforeLastDot() {
        // condMet 的 id 段 = 最后一个 `.` 之前（`<id>.<index>` 两段式）
        val keys = listOf(
            "capsule.condMet.aaa.0",
            "capsule.condMet.aaa.3",
            "capsule.condMet.bbb.1",
        )
        val orphans = check.orphanMetaKeys(
            metaKeys = keys,
            capsuleIds = setOf("aaa"),
            condMetPrefix = "capsule.condMet.",
            prefixes = listOf("capsule.condMet."),
        )
        assertEquals(listOf("capsule.condMet.bbb.1"), orphans)
    }

    @Test
    fun unknownPrefixKeysIgnored() {
        // 不在扫描前缀清单内的 key（如 settings.*）不参与孤儿判定
        val orphans = check.orphanMetaKeys(
            metaKeys = listOf("settings.foo", "app.lastOpen", "capsule.read.ghost"),
            capsuleIds = emptySet(),
            condMetPrefix = "capsule.condMet.",
            prefixes = listOf("capsule.read."),
        )
        assertEquals(listOf("capsule.read.ghost"), orphans)
    }

    @Test
    fun emptyIdFragmentNeverCountsAsOrphan() {
        // 防御：前缀后空 id 段（畸形 key）不产出误报
        val orphans = check.orphanMetaKeys(
            metaKeys = listOf("capsule.read."),
            capsuleIds = emptySet(),
            condMetPrefix = "capsule.condMet.",
            prefixes = listOf("capsule.read."),
        )
        assertTrue(orphans.isEmpty())
    }

    // ---- 2. 依赖死链 / 销毁锁死 ----

    @Test
    fun deadLinkReportedWhenTargetMissing() {
        val capsules = listOf(capsule("A"), capsule("B", dependCapsuleId = "ghost"))
        val problems = check.dependencyProblems(capsules)
        assertEquals(1, problems.size)
        assertEquals("B", problems[0].capsuleId)
        assertEquals("ghost", problems[0].targetId)
        assertEquals(false, problems[0].targetExists)
    }

    @Test
    fun destroyedTargetReportedAsLockIn() {
        val capsules = listOf(
            capsule("A", state = CapsuleState.DESTROYED),
            capsule("B", dependCapsuleId = "A"),
        )
        val problems = check.dependencyProblems(capsules)
        assertEquals(1, problems.size)
        assertTrue(problems[0].targetExists)
        assertEquals("B", problems[0].capsuleId)
    }

    @Test
    fun healthyDependencyAndSelfDependencyNotReported() {
        // 正常依赖 + 自依赖（创建侧已禁止，体检防御性跳过）都不产出
        val capsules = listOf(
            capsule("A"),
            capsule("B", dependCapsuleId = "A"),
            capsule("C").copy(dependCapsuleId = "C"),
        )
        assertTrue(check.dependencyProblems(capsules).isEmpty())
    }

    // ---- 3. 依赖环 ----

    @Test
    fun twoNodeCycleBothIdsReported() {
        val capsules = listOf(
            capsule("A", dependCapsuleId = "B"),
            capsule("B", dependCapsuleId = "A"),
        )
        assertEquals(setOf("A", "B"), check.cycleCapsuleIds(capsules).toSet())
    }

    @Test
    fun threeNodeCycleAllIdsReported() {
        val capsules = listOf(
            capsule("A", dependCapsuleId = "C"),
            capsule("B", dependCapsuleId = "A"),
            capsule("C", dependCapsuleId = "B"),
            capsule("D"), // 圈外
        )
        assertEquals(setOf("A", "B", "C"), check.cycleCapsuleIds(capsules).toSet())
    }

    @Test
    fun linearChainNoCycle() {
        val capsules = listOf(
            capsule("A"),
            capsule("B", dependCapsuleId = "A"),
            capsule("C", dependCapsuleId = "B"),
        )
        assertTrue(check.cycleCapsuleIds(capsules).isEmpty())
    }

    // ---- 4. 分片材料 ----

    @Test
    fun normalCapsuleWithoutShardFieldsPasses() {
        assertTrue(check.incompleteShardMaterials(listOf(capsule("A"))).isEmpty())
    }

    @Test
    fun completeShardMaterialPasses() {
        val full = capsule(
            "A",
            shardSalt = "salt",
            shardParams = "params",
            shardVerifier = "verifier",
            shardThreshold = 2,
            shardTotal = 3,
        )
        assertTrue(check.incompleteShardMaterials(listOf(full)).isEmpty())
    }

    @Test
    fun partiallyMissingShardFieldsFlagged() {
        val broken = capsule(
            "A",
            shardSalt = "salt",
            shardParams = null,
            shardVerifier = "verifier",
            shardThreshold = 2,
            shardTotal = 3,
        )
        assertEquals(listOf("A"), check.incompleteShardMaterials(listOf(broken)))
    }

    @Test
    fun invalidThresholdRangeFlagged() {
        // 五字段齐全但 threshold 越界 / total < threshold 仍判不完整
        val badThreshold = capsule("A", shardSalt = "s", shardParams = "p", shardVerifier = "v", shardThreshold = 0, shardTotal = 3)
        val badTotal = capsule("B", shardSalt = "s", shardParams = "p", shardVerifier = "v", shardThreshold = 4, shardTotal = 3)
        assertEquals(listOf("A", "B"), check.incompleteShardMaterials(listOf(badThreshold, badTotal)))
    }

    // ---- 5. scan 汇总 ----

    @Test
    fun scanAggregatesAllKinds() {
        val capsules = listOf(
            capsule("A", state = CapsuleState.DESTROYED),
            capsule("B", dependCapsuleId = "A"),
            capsule("C", dependCapsuleId = "ghost"),
        )
        val findings = check.scan(
            capsules = capsules,
            metaKeys = listOf("capsule.read.ghost", "capsule.read.A"),
            condMetPrefix = "capsule.condMet.",
            prefixes = listOf("capsule.read.", "capsule.condMet."),
        )
        val kinds = findings.map { it.kind }
        assertTrue(LibraryHealthCheck.Kind.ORPHAN_META in kinds)
        assertTrue(LibraryHealthCheck.Kind.DEAD_DEPENDENCY in kinds)
        assertTrue(LibraryHealthCheck.Kind.DESTROYED_DEPENDENCY in kinds)
        // 死链与销毁锁死各一条，detail 携带方向
        assertTrue(findings.any { it.kind == LibraryHealthCheck.Kind.DEAD_DEPENDENCY && it.detail == "C → ghost" })
        assertTrue(findings.any { it.kind == LibraryHealthCheck.Kind.DESTROYED_DEPENDENCY && it.detail == "B → A" })
    }

    @Test
    fun scanCleanLibraryYieldsNoFindings() {
        val capsules = listOf(capsule("A"), capsule("B", dependCapsuleId = "A"))
        val findings = check.scan(
            capsules = capsules,
            metaKeys = listOf("capsule.read.A"),
            condMetPrefix = "capsule.condMet.",
            prefixes = listOf("capsule.read.", "capsule.condMet."),
        )
        assertTrue(findings.isEmpty())
    }
}
