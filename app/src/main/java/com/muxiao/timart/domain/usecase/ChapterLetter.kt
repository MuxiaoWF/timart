package com.muxiao.timart.domain.usecase

/**
 * 多章节信件（N10，纯逻辑可 JVM 单测）：
 *
 * 章节在创建时一次性全部加密入库（不做延迟加密，避免分片式复杂度），存储侧只有
 * meta 段落边界（每章段落数，逗号分隔）；阅读侧按「已揭示章数」过滤正文段落——
 * 揭示节奏是**阅读侧行为**，与解锁判定正交（判定引擎零感知）。
 *
 * 揭示规则：首次揭封展开第 1 章；此后每次重读且距上次揭示 ≥ [REVEAL_GAP_DAYS] 天，
 * 提供「下一章可以读了」入口。边界与段落计数不一致时 fail-open 全量展示
 * （宁可多读，不可丢文——正文正确性优先于节奏）。
 */
object ChapterLetter {

    /** 章数上限（含首章） */
    const val MAX_CHAPTERS = 3

    /** 相邻两章揭示的最小间隔（天） */
    const val REVEAL_GAP_DAYS = 3L

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * 段落边界解析：`"3,2"` → [3, 2]。非法（null / 段数 <2 章 / 非正数 / 解析失败）返回 null
     * （fail-closed 按单章信处理——与拼图分组值解析同哲学）。
     */
    fun parseBoundaries(metaValue: String?): List<Int>? {
        if (metaValue.isNullOrBlank()) return null
        val counts = metaValue.split(',').map { it.trim().toIntOrNull() ?: return null }
        if (counts.size < 2 || counts.any { it <= 0 }) return null
        return counts
    }

    /** 段落边界编码（创建侧）；章数 <2 或含空章返回 null（不写键 = 单章信） */
    fun encodeBoundaries(paragraphCounts: List<Int>): String? {
        if (paragraphCounts.size < 2 || paragraphCounts.any { it <= 0 }) return null
        return paragraphCounts.joinToString(",")
    }

    /**
     * 按已揭示章数取可见段落：revealed = 前 [counts.take(revealed)].sum() 段。
     * 边界总量与实际段落数不一致（版本迁移/历史数据）→ fail-open 返回全量段落。
     */
    fun visibleParagraphs(paragraphs: List<String>, boundaries: List<Int>, revealed: Int): List<String> {
        val totalByBoundaries = boundaries.sum()
        if (totalByBoundaries != paragraphs.size) return paragraphs
        val shown = boundaries.take(revealed.coerceIn(1, boundaries.size)).sum()
        return paragraphs.take(shown)
    }

    /**
     * 是否已可揭示下一章：还有未揭示章（revealed ∈ [1, total)）且距上次揭示 ≥3 天。
     * lastRevealAt 缺失（历史数据）按「已可揭示」处理（宁早勿卡）。
     */
    fun canRevealNext(revealed: Int, total: Int, lastRevealAt: Long?, now: Long): Boolean {
        if (revealed < 1 || revealed >= total) return false
        val gap = REVEAL_GAP_DAYS * DAY_MS
        return lastRevealAt == null || now - lastRevealAt >= gap
    }

    /**
     * 距下一章可读的剩余毫秒（用于「N 天后可读」提示）；已可读 / 无后续章返回 null。
     */
    fun nextChapterEtaMs(revealed: Int, total: Int, lastRevealAt: Long?, now: Long): Long? {
        if (revealed < 1 || revealed >= total) return null
        if (lastRevealAt == null) return null
        val gap = REVEAL_GAP_DAYS * DAY_MS
        return (lastRevealAt + gap - now).takeIf { it > 0 }
    }
}
