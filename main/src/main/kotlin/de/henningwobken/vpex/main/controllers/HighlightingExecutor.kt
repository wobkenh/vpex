package de.henningwobken.vpex.main.controllers;

import de.henningwobken.vpex.main.model.DisplayMode
import de.henningwobken.vpex.main.model.Find
import org.fxmisc.richtext.CodeArea
import tornadofx.*
import kotlin.math.max
import kotlin.math.min

public class HighlightingExecutor : Controller() {

    private val xmlSyntaxHighlightingController by inject<XmlSyntaxHighlightingController>()
    private val settingsController by inject<SettingsController>()
    private val searchAndReplaceController by inject<SearchAndReplaceController>()

    fun queueHighlightingTask(codeArea: CodeArea, allFinds: List<Find>, startFrom: Int, displayMode: DisplayMode, pageIndex: Int, showFind: Boolean) {
        // TODO: Different tasks?
        // - Remove finds
        // - part of text changed
        // - all of text changed
        if (settingsController.getSettings().syntaxHighlighting) {
            codeArea.setStyleSpans(startFrom, xmlSyntaxHighlightingController.computeHighlighting(codeArea.text.substring(startFrom)))
        } else {
            codeArea.clearStyle(startFrom, codeArea.length - 1)
        }
        if (showFind) {
            highlightFinds(codeArea, allFinds, startFrom, displayMode, pageIndex)
        }
    }


    private fun highlightFinds(codeArea: CodeArea, allFinds: List<Find>, startFrom: Int, displayMode: DisplayMode, pageIndex: Int) {
        if (allFinds.isEmpty()) {
            return
        }
        val localFinds = allFinds.toList()
        val pageSize = this.settingsController.getSettings().pageSize
        if (displayMode == DisplayMode.PLAIN) {
            for (find in localFinds) {
                if (find.start >= startFrom) {
                    codeArea.setStyle(find.start.toInt(), find.end.toInt(), listOf("searchAllHighlight"))
                }
            }
        } else {
            val pageOffset = pageIndex * pageSize.toLong()
            for (find in localFinds) {
                if (searchAndReplaceController.isInPage(find, pageIndex, pageSize)) {
                    val start = max(find.start - pageOffset, 0).toInt()
                    val end = min(find.end - pageOffset, pageSize.toLong() - 1).toInt()
                    codeArea.setStyle(start, end, listOf("searchAllHighlight"))
                }
            }
        }

    }

}
