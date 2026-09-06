package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 依赖图单测：环检测（A→B→A）、死链、自依赖、自动销毁强警告 */
class DependencyGraphUseCaseTest {

    private val useCase = DependencyGraphUseCase()

    private fun capsule(
        id: String,
        dependCapsuleId: String? = null,
        state: CapsuleState = CapsuleState.LOCKED,
        autoDestroy: Boolean = false,
    ) = Capsule(
        id = id,
        title = "胶囊$id",
        contentCipher = null,
        createTimestamp = 0L,
        unlockRule = UnlockRule(LogicType.AND, listOf(UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1)))),
        state = state,
        autoDestroyAfterRead = autoDestroy,
        dependCapsuleId = dependCapsuleId,
    )

    // ---- 1. 环检测 ----

    @Test
    fun detectCycleFindsTwoNodeLoop() {
        // 现有图：B 依赖 A。若再让 A 依赖 B（A→B→A）则成环
        val all = listOf(capsule("A"), capsule("B", dependCapsuleId = "A"))
        assertTrue(useCase.detectCycle(fromId = "A", candidateDepId = "B", all = all))
    }

    @Test
    fun detectCycleFindsThreeNodeLoop() {
        val all = listOf(
            capsule("A", dependCapsuleId = "C"),
            capsule("B", dependCapsuleId = "A"),
            capsule("C", dependCapsuleId = "B"),
        )
        assertTrue(useCase.detectCycle(fromId = "C", candidateDepId = "B", all = all))
    }

    @Test
    fun detectCyclePassesForLinearChain() {
        // 链：B→A；让 C 依赖 B 不成环
        val all = listOf(capsule("A"), capsule("B", dependCapsuleId = "A"))
        assertFalse(useCase.detectCycle(fromId = "C", candidateDepId = "B", all = all))
        // 让 B 依赖不存在的 D（链条断裂处不误报）
        val broken = listOf(capsule("A"), capsule("B", dependCapsuleId = "D"))
        assertFalse(useCase.detectCycle(fromId = "C", candidateDepId = "B", all = broken))
    }

    // ---- 2. 自依赖 ----

    @Test
    fun selfDependencyIsError() {
        val candidate = capsule("A").copy(dependCapsuleId = "A")
        val error = useCase.validateCandidate(candidate, listOf(candidate))
        assertNotNull(error)
        assertEquals(DependencyIssue.SELF_DEPENDENCY, error!!.issue)
        assertEquals(DependencySeverity.ERROR, error.severity)
    }

    // ---- 3. 死链 ----

    @Test
    fun missingTargetIsDeadLink() {
        val candidate = capsule("C").copy(dependCapsuleId = "ghost")
        val error = useCase.validateCandidate(candidate, listOf(capsule("A"), candidate))
        assertNotNull(error)
        assertEquals(DependencyIssue.DEAD_LINK, error!!.issue)
        assertEquals(DependencySeverity.ERROR, error.severity)
    }

    @Test
    fun destroyedTargetIsDeadLink() {
        val destroyed = capsule("B", state = CapsuleState.DESTROYED)
        val candidate = capsule("C").copy(dependCapsuleId = "B")
        val error = useCase.validateCandidate(candidate, listOf(capsule("A"), destroyed, candidate))
        assertEquals(DependencyIssue.DEAD_LINK, error!!.issue)
        assertEquals(DependencySeverity.ERROR, error.severity)
    }

    // ---- 4. 环 → ERROR ----

    @Test
    fun cycleIsError() {
        val all = listOf(capsule("A"), capsule("B", dependCapsuleId = "A"))
        val candidate = capsule("A").copy(dependCapsuleId = "B") // A→B→A
        val error = useCase.validateCandidate(candidate, all + candidate)
        assertEquals(DependencyIssue.CYCLE, error!!.issue)
        assertEquals(DependencySeverity.ERROR, error.severity)
        assertEquals("不能形成循环依赖", error.message)
    }

    // ---- 5. 自动销毁依赖 → WARNING ----

    @Test
    fun autoDestroyTargetIsWarning() {
        val target = capsule("B", autoDestroy = true)
        val candidate = capsule("C").copy(dependCapsuleId = "B")
        val error = useCase.validateCandidate(candidate, listOf(capsule("A"), target, candidate))
        assertEquals(DependencyIssue.AUTO_DESTROY_RISK, error!!.issue)
        assertEquals(DependencySeverity.WARNING, error.severity)
        assertTrue(error.message.contains("永久无法解锁"))
    }

    // ---- 6. 合法依赖 → null ----

    @Test
    fun validDependencyReturnsNull() {
        val all = listOf(capsule("A"), capsule("B", dependCapsuleId = "A"))
        val candidate = capsule("C").copy(dependCapsuleId = "A")
        assertNull(useCase.validateCandidate(candidate, all + candidate))
        // 无依赖直接通过
        assertNull(useCase.validateCandidate(capsule("D"), all))
    }
}
