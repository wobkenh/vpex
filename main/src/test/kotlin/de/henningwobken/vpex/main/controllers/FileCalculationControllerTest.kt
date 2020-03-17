package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.LineEnding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.io.StringReader

internal class FileCalculationControllerTest {

    private val fileCalculationController = FileCalculationController()
    private val testFile = loadResource("umlauts.xml")

    @Test
    fun calcStartingByteIndexesAndLineCounts() {
        val result = fileCalculationController.calcStartingByteIndexesAndLineCounts(testFile, 50) {}
        assertIterableEquals(listOf(0L, 52L, 110L, 170L), result.pageStartingByteIndexes)
        assertIterableEquals(listOf(3, 3, 3, 3), result.pageLineCounts)
    }

    @Test
    fun calcStartingLineCounts() {
        // Page Starting Line Counts overlap, e.g. consider the following file:
        // txtt\n
        // x
        // if page size was 3, then page 1 would have 1 line (txt) and page 2 would have 2 lines (t\nx)
        // but the whole file is just 2 lines

        // this test scenario relates to the following file (pages are seperated by |):
        // """
        // abcde|ab
        // cde|ab
        // cd
        // e|ab
        // c
        // d
        // e
        // """
        val lineCounts = listOf(1, 2, 3, 4)
        assertIterableEquals(listOf(0, 0, 1, 3), fileCalculationController.calculateStartingLineCounts(lineCounts))
    }

    @Test
    fun determineLineEndingUnix() {
        // TODO: Encoding
        val string = "testWithUnix\nLineBreaks"
        var lineEnding: LineEnding? = null
        fileCalculationController.determineLineEndingAndEncoding(StringReader(string)) {
            lineEnding = it
        }

        Thread.sleep(100) // TODO: Move Thread Logic elsewhere so we can test synchronously

        assertEquals(LineEnding.LF, lineEnding)
    }

    @Test
    fun determineLineEndingWindows() {
        // TODO: Encoding
        val string = "testWithUnix\r\nLineBreaks"
        var lineEnding: LineEnding? = null
        fileCalculationController.determineLineEndingAndEncoding(StringReader(string)) {
            lineEnding = it
        }

        Thread.sleep(100) // TODO: Move Thread Logic elsewhere so we can test synchronously

        assertEquals(LineEnding.CRLF, lineEnding)
    }

    private fun loadResource(resource: String): File {
        return File(SearchAndReplaceControllerTest::class.java.getResource("/file_calculation/$resource").toURI())
    }
}
