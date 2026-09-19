package com.muxiao.timart.utils.export

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.DestroyRecord
import com.muxiao.timart.domain.model.Lang
import com.muxiao.timart.domain.model.unlock.ConditionKind
import com.muxiao.timart.domain.model.unlock.conditionKind
import com.muxiao.timart.domain.model.unlock.conditionKindName
import com.muxiao.timart.l10n.stringsFor
import java.time.Instant
import java.time.ZoneId

/**
 * 年度星图报告统计（体验储备池 §5）：一年封存 / 开启 / 归尘 / 等待最久 / 最常用条件大类。
 * 输入为纯数据（胶囊行 + 尘迹档案），输出纯数据 + 海报文案；无 Android 依赖、可 JVM 单测。
 * 统计口径：
 * - 封存数/开启数按胶囊行时间戳（物理删除的胶囊不计——「删除」不留档案的语义延伸）；
 * - 「等待最久」优先取本年内解锁、等待跨度最大的一颗；无开启记录时回退等待中的最老一颗；
 * - 「最常用条件」按条件大类（ConditionKind）计数，范围为年内封存的胶囊（空则回退全部现存胶囊）。
 */
object AnnualReport {

    data class AnnualStats(
        val year: Int,
        val sealedCount: Int,
        val openedCount: Int,
        val dustCount: Int,
        /** 等待最久的胶囊标题（无胶囊记录时为 null） */
        val longestWaitTitle: String?,
        /** 等待天数（开启跨度或已等待天数） */
        val longestWaitDays: Int,
        /** 最常用条件大类显示名（无从统计时为 null） */
        val topConditionKindName: String?,
        val topConditionCount: Int,
    )

    fun compute(
        capsules: List<Capsule>,
        destroyed: List<DestroyRecord>,
        year: Int,
        lang: Lang = Lang.ZH_HANS,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): AnnualStats {
        fun yearOf(millis: Long): Int =
            Instant.ofEpochMilli(millis).atZone(zone).year

        val sealed = capsules.filter { yearOf(it.createTimestamp) == year }
        val opened = capsules.filter { it.unlockTimestamp != null && yearOf(it.unlockTimestamp) == year }
        val dust = destroyed.count { yearOf(it.destroyedAt) == year }

        // 等待最久：本年内开启者取「解锁 − 封存」最大跨度；无则取等待中的最老一颗（截至 now）
        val longestOpened = opened.maxByOrNull { it.unlockTimestamp!! - it.createTimestamp }
        val oldestWaiting = capsules
            .filter { it.state == CapsuleState.LOCKED }
            .minByOrNull { it.createTimestamp }
        val longest: Capsule?
        val longestDays: Int
        if (longestOpened != null) {
            longest = longestOpened
            longestDays = ((longestOpened.unlockTimestamp!! - longestOpened.createTimestamp) / 86_400_000L)
                .coerceAtLeast(0).toInt()
        } else {
            longest = oldestWaiting
            longestDays = oldestWaiting?.let { ((nowMillis - it.createTimestamp) / 86_400_000L).coerceAtLeast(0).toInt() } ?: 0
        }

        // 最常用条件大类：年内封存优先，空则回退全部现存胶囊
        val pool = sealed.ifEmpty { capsules }
        val kindCounts = HashMap<ConditionKind, Int>()
        pool.forEach { capsule ->
            capsule.unlockRule.conditionList.forEach { condition ->
                val kind = conditionKind(condition)
                kindCounts[kind] = (kindCounts[kind] ?: 0) + 1
            }
        }
        val topKind = kindCounts.entries.maxByOrNull { it.value }?.key

        return AnnualStats(
            year = year,
            sealedCount = sealed.size,
            openedCount = opened.size,
            dustCount = dust,
            longestWaitTitle = longest?.title,
            longestWaitDays = longestDays,
            topConditionKindName = topKind?.let { conditionKindName(it, lang) },
            topConditionCount = topKind?.let { kindCounts[it] ?: 0 } ?: 0,
        )
    }

    /** 海报内容组装（段落 = 统计行；[lang] 决定文案语言） */
    fun posterContent(
        stats: AnnualStats,
        lang: Lang,
    ): PosterComposer.PosterContent {
        val L = stringsFor(lang)
        val lines = buildList {
            add(L.reportSealedFmt.format(stats.sealedCount))
            add(L.reportOpenedFmt.format(stats.openedCount))
            add(L.reportDustFmt.format(stats.dustCount))
            stats.longestWaitTitle?.let { title ->
                add(L.reportLongestWaitFmt.format(title, stats.longestWaitDays))
            }
            stats.topConditionKindName?.let { name ->
                add(L.reportTopConditionFmt.format(name, stats.topConditionCount))
            }
        }
        return PosterComposer.PosterContent(
            title = L.reportTitleFmt.format(stats.year),
            paragraphs = lines,
            createdLine = L.posterBrand,
            note = L.reportNote,
        )
    }
}
