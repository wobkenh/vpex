package de.henningwobken.vpex.main.controllers;

import de.henningwobken.vpex.main.model.Find
import org.fxmisc.richtext.CodeArea
import tornadofx.*

public class HighlightingExecutor : Controller() {

    private val xmlSyntaxHighlightingController by inject<XmlSyntaxHighlightingController>()
    private val settingsController by inject<SettingsController>()

    fun queueHighlightingTask(codeArea: CodeArea, allFinds: List<Find>, startFrom: Int) {

        if (settingsController.getSettings().syntaxHighlighting) {
            codeArea.setStyleSpans(startFrom, xmlSyntaxHighlightingController.computeHighlighting(codeArea.text.substring(startFrom)))
        } else {
            codeArea.clearStyle(startFrom, codeArea.length - 1)
        }
        

    }


    private fun highlightFinds(finds: List<Find>) {
        // TODO : Highlight finds after syntax highlighting
//        if (finds.isEmpty()) {
//            return
//        }
//        val localFinds = finds.toList()
//        this.hasFindProperty.set(true)
//        val pageSize = this.settingsController.getSettings().pageSize
//        if (displayMode.get() == DisplayMode.PLAIN) {
//            for (find in localFinds) {
//                codeArea.setStyle(find.start.toInt(), find.end.toInt(), listOf("searchAllHighlight"))
//            }
//        } else {
//            val pageOffset = this.getPageIndex() * pageSize.toLong()
//            for (find in localFinds) {
//                if (isInPage(find)) {
//                    val start = max(find.start - pageOffset, 0).toInt()
//                    val end = min(find.end - pageOffset, pageSize.toLong() - 1).toInt()
//                    codeArea.setStyle(start, end, listOf("searchAllHighlight"))
//                }
//            }
//        }

    }

}
