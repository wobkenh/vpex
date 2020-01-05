package de.henningwobken.vpex.main.controllers

import tornadofx.*
import java.io.File

class FileCalculationController : Controller() {

    private val stringUtils by inject<StringUtils>()

    data class CalculationResult(val pageLineCounts: List<Int>, val pageStartingByteIndexes: List<Long>)

    fun calcStartingByteIndexesAndLineCounts(file: File, pageSize: Int, progressCallback: (percentDone: Double) -> Unit): CalculationResult {
        val totalSize = file.length().toDouble()
        val reader = file.reader()
        val buffer = CharArray(pageSize)
        var pageIndex = 0
        var totalBytesRead = 0L
        val pageStartingByteIndexes = mutableListOf<Long>()
        val pageLineCounts = mutableListOf<Int>()
        while (true) {
            val read = reader.read(buffer)
            if (read == -1) {
                break
            }
            pageStartingByteIndexes.add(totalBytesRead)
            val string = String(buffer, 0, read)
            totalBytesRead += string.toByteArray().size
            pageLineCounts.add(stringUtils.countLinesInString(string))
            pageIndex++
            progressCallback(totalBytesRead / totalSize)
        }
        reader.close()
        return CalculationResult(pageLineCounts, pageStartingByteIndexes)
    }

    fun calculateStartingLineCounts(pageLineCounts: List<Int>): List<Int> {
        val pageStartingLineCounts = mutableListOf<Int>()
        val maxPage = pageLineCounts.size
        pageStartingLineCounts.add(0)
        for (page in 2..maxPage) {
            val pageIndex = page - 1
            // minus 1 since page break introduces a "fake" line break
            pageStartingLineCounts.add(pageLineCounts[pageIndex - 1] + pageStartingLineCounts[pageIndex - 1] - 1)
        }
        return pageStartingLineCounts
    }
}
