package com.muxiao.timart.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多章节信件（N10）纯逻辑：边界编解码、按章切片、揭示间隔窗口、fail-open/-closed 口径。
 */
class ChapterLetterTest {

    private val dayMs = 24L * 60 * 60 * 1000

    // ---- 边界编解码 ----

    @Test
    fun `parse valid boundaries`() {
        assertEquals(listOf(3, 2), ChapterLetter.parseBoundaries("3,2"))
        assertEquals(listOf(1, 1, 1), ChapterLetter.parseBoundaries("1,1,1"))
    }

    @Test
    fun `parse rejects blank or malformed`() {
        assertNull(ChapterLetter.parseBoundaries(null))
        assertNull(ChapterLetter.parseBoundaries(""))
        assertNull(ChapterLetter.parseBoundaries("3"))
        assertNull(ChapterLetter.parseBoundaries("3,a"))
        assertNull(ChapterLetter.parseBoundaries("0,2"))
        assertNull(ChapterLetter.parseBoundaries("-1,2"))
    }

    @Test
    fun `encode requires at least two non-empty chapters`() {
        assertNull(ChapterLetter.encodeBoundaries(listOf(3)))
        assertNull(ChapterLetter.encodeBoundaries(listOf(3, 0)))
        assertEquals("3,2", ChapterLetter.encodeBoundaries(listOf(3, 2)))
    }

    // ---- 按章切片 ----

    @Test
    fun `visible paragraphs slice by revealed count`() {
        val paras = listOf("a", "b", "c", "d", "e")
        val bounds = listOf(3, 2)
        assertEquals(listOf("a", "b", "c"), ChapterLetter.visibleParagraphs(paras, bounds, 1))
        assertEquals(paras, ChapterLetter.visibleParagraphs(paras, bounds, 2))
    }

    @Test
    fun `visible clamps over-reveal to full text`() {
        val paras = listOf("a", "b")
        assertEquals(paras, ChapterLetter.visibleParagraphs(paras, listOf(1, 1), 5))
    }

    @Test
    fun `boundary mismatch fails open to full text`() {
        val paras = listOf("a", "b", "c")
        assertEquals(paras, ChapterLetter.visibleParagraphs(paras, listOf(3, 2), 1))
    }

    // ---- 揭示窗口 ----

    @Test
    fun `can reveal after gap elapsed`() {
        val last = 100L * dayMs
        assertTrue(ChapterLetter.canRevealNext(1, 3, last, last + 3 * dayMs))
        assertFalse(ChapterLetter.canRevealNext(1, 3, last, last + 3 * dayMs - 1))
    }

    @Test
    fun `missing last reveal timestamp counts as ready`() {
        assertTrue(ChapterLetter.canRevealNext(1, 3, null, 0L))
    }

    @Test
    fun `no next chapter when all revealed or none revealed`() {
        assertFalse(ChapterLetter.canRevealNext(3, 3, 0L, 10L * dayMs))
        assertFalse(ChapterLetter.canRevealNext(0, 3, null, 10L * dayMs))
    }

    @Test
    fun `eta ms null when ready or finished`() {
        val last = 100L * dayMs
        assertNull(ChapterLetter.nextChapterEtaMs(1, 3, last, last + 3 * dayMs))
        assertNull(ChapterLetter.nextChapterEtaMs(3, 3, last, last))
        assertNull(ChapterLetter.nextChapterEtaMs(1, 3, null, last))
        assertEquals(
            2 * dayMs,
            ChapterLetter.nextChapterEtaMs(1, 3, last, last + dayMs),
        )
    }
}
