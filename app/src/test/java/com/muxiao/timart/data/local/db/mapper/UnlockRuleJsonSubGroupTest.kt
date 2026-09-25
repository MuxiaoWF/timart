package com.muxiao.timart.data.local.db.mapper

import com.muxiao.timart.domain.model.unlock.ConditionGroup
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 子群组序列化单测（储备池暂缓项落地）：
 * 双写扁平 conditions（旧版 App 读取新 JSON 仍按扁平语义）、v2 组划分回读、非法划分整体丢弃、
 * 旧版扁平 JSON（v1 无 groups 字段）兼容回读。
 */
class UnlockRuleJsonSubGroupTest {

    private val passedDate = UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1))
    private val futureDate = UnlockCondition.FixedDate(LocalDate.of(2027, 1, 1))
    private val battery = UnlockCondition.BatteryLevel(min = 90, max = null)

    @Test
    fun roundTripKeepsGroups() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, futureDate, battery),
            groups = listOf(
                ConditionGroup(name = "日子", logicType = LogicType.OR, indexes = listOf(0, 1)),
            ),
        )
        val json = UnlockRuleJson.toJson(rule)
        assertTrue(json.contains("\"groups\""))
        assertTrue(json.contains("\"v\":2"))
        // 扁平 conditions 恒双写（旧版兼容）
        assertTrue(json.contains("FIXED_DATE"))
        val decoded = UnlockRuleJson.fromJson(json)
        assertEquals(rule.conditionList, decoded.conditionList)
        assertEquals(1, decoded.groups.size)
        assertEquals("日子", decoded.groups[0].name)
        assertEquals(LogicType.OR, decoded.groups[0].logicType)
        assertEquals(listOf(0, 1), decoded.groups[0].indexes)
    }

    @Test
    fun flatRuleSerializesAsLegacyShape() {
        val rule = UnlockRule(LogicType.OR, listOf(passedDate, battery))
        val json = UnlockRuleJson.toJson(rule)
        assertTrue(!json.contains("groups"))
        val decoded = UnlockRuleJson.fromJson(json)
        assertEquals(rule, decoded)
    }

    @Test
    fun legacyJsonWithoutGroupsParsesFlat() {
        // 旧版本产出的 JSON：无 groups 字段（encodeDefaults = false 从不写出）
        val legacy = """
            {"v":1,"logicType":"AND",
             "conditions":[{"type":"FIXED_DATE","isoDate":"2026-01-01"},{"type":"BATTERY","min":90}]}
        """.trimIndent()
        val decoded = UnlockRuleJson.fromJson(legacy)
        assertEquals(LogicType.AND, decoded.logicType)
        assertEquals(2, decoded.conditionList.size)
        assertTrue(decoded.groups.isEmpty())
    }

    @Test
    fun overlappingGroupsAreDropped() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, futureDate, battery),
            groups = listOf(
                ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(0, 1)),
                ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(1, 2)),
            ),
        )
        val json = UnlockRuleJson.toJson(rule)
        val decoded = UnlockRuleJson.fromJson(json)
        // 重叠划分 → 解码侧整体丢弃（fail-safe 回退扁平）
        assertTrue(decoded.groups.isEmpty())
        assertEquals(3, decoded.conditionList.size)
    }

    @Test
    fun outOfRangeIndexesAreDropped() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, battery),
            groups = listOf(ConditionGroup(name = null, logicType = LogicType.OR, indexes = listOf(0, 9))),
        )
        val decoded = UnlockRuleJson.fromJson(UnlockRuleJson.toJson(rule))
        assertTrue(decoded.groups.isEmpty())
    }

    @Test
    fun invalidLogicInGroupDropsAllGroups() {
        val raw = """
            {"v":2,"logicType":"AND",
             "conditions":[{"type":"FIXED_DATE","isoDate":"2026-01-01"},{"type":"BATTERY","min":90}],
             "groups":[{"logicType":"NOT_A_LOGIC","indexes":[0,1]}]}
        """.trimIndent()
        val decoded = UnlockRuleJson.fromJson(raw)
        assertTrue(decoded.groups.isEmpty())
        assertEquals(2, decoded.conditionList.size)
    }

    @Test
    fun groupThresholdCoercedToMemberRange() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, futureDate, battery),
            groups = listOf(
                ConditionGroup(name = null, logicType = LogicType.AT_LEAST, threshold = 9, indexes = listOf(0, 1, 2)),
            ),
        )
        val decoded = UnlockRuleJson.fromJson(UnlockRuleJson.toJson(rule))
        assertEquals(1, decoded.groups.size)
        assertEquals(3, decoded.groups[0].threshold)
    }

    @Test
    fun emptyGroupNameNormalizesToNull() {
        val rule = UnlockRule(
            LogicType.AND,
            listOf(passedDate, battery),
            groups = listOf(ConditionGroup(name = "  ", logicType = LogicType.OR, indexes = listOf(0, 1))),
        )
        val decoded = UnlockRuleJson.fromJson(UnlockRuleJson.toJson(rule))
        assertEquals(null, decoded.groups.single().name)
    }
}
