package de.henningwobken.vpex.main.controllers;

import de.henningwobken.vpex.main.model.DisplayMode
import de.henningwobken.vpex.main.model.Find
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.model.PlainTextChange
import tornadofx.*
import kotlin.math.max
import kotlin.math.min

class HighlightingExecutor : Controller() {

    companion object {
        const val CURRENT_FIND = "searchHighlight"
        const val ALL_FIND = "searchAllHighlight"
    }


    private val xmlSyntaxHighlightingController by inject<XmlSyntaxHighlightingController>()
    private val settingsController by inject<SettingsController>()
    private val searchAndReplaceController by inject<SearchAndReplaceController>()

    fun removeFinds(codeArea: CodeArea) {
        unhighlight(codeArea, 0, codeArea.text.length, CURRENT_FIND)
        unhighlight(codeArea, 0, codeArea.text.length, ALL_FIND)
    }

    fun nextFind(codeArea: CodeArea, oldFindStart: Int, oldFindEnd: Int, newFindStart: Int, newFindEnd: Int) {
        if (oldFindEnd > 0) {
            unhighlight(codeArea, oldFindStart, oldFindEnd, CURRENT_FIND)
        }
        // We do not call apllyFindHighlight/highlightFind because the place where this is called already has
        // the indices calculated to be in page, not in file
        highlight(codeArea, newFindStart, newFindEnd, CURRENT_FIND)
    }

    fun allFinds(codeArea: CodeArea, displayMode: DisplayMode, pageIndex: Int, allFinds: List<Find>) {
        highlightFinds(codeArea, displayMode, pageIndex, allFinds)
    }

    fun textChanged(codeArea: CodeArea, textChange: PlainTextChange) {
        // codearea text already has changes applied to it
        val startFrom = textChange.position
        val endAt = startFrom + textChange.inserted.length
        val maxDiversion = 1000 // How far i am willing to go back/forward to find the next/last xml element

        // We build a prefix/postfix to correct syntax highlighting around the text change
        // that might have been corrected or corrupted due to the text change

        // Prefix
        val lowestStartPrefix = max(startFrom - maxDiversion, 0)
        var startPrefixFrom = lowestStartPrefix
        val xmlEndCodePoint = '>'.toInt()
        for (i in startFrom - 1 downTo lowestStartPrefix) {
            if (codeArea.text.codePointAt(i) == xmlEndCodePoint) {
                startPrefixFrom = i + 1
                break
            }
        }

        // Postfix
        val highestEndPrefix = min(endAt + maxDiversion, codeArea.text.length)
        var endPostfixAt = highestEndPrefix
        val xmlStartCodePoint = '<'.toInt()
        for (i in endAt until highestEndPrefix) {
            if (codeArea.text.codePointAt(i) == xmlStartCodePoint) {
                endPostfixAt = i
                break
            }
        }

        val prefixText = codeArea.text.substring(startPrefixFrom, startFrom)
        val postfixText = codeArea.text.substring(endAt, endPostfixAt)
        val newStyles = xmlSyntaxHighlightingController.computeHighlighting(prefixText + textChange.inserted + postfixText)
        codeArea.setStyleSpans(startPrefixFrom, newStyles)
    }


    fun highlightEverything(codeArea: CodeArea, allFinds: List<Find>, currentFind: Find, displayMode: DisplayMode, pageIndex: Int, showFind: Boolean) {
        codeArea.clearStyle(0, codeArea.length)
        if (settingsController.getSettings().syntaxHighlighting) {
            codeArea.setStyleSpans(0, xmlSyntaxHighlightingController.computeHighlighting(codeArea.text))
        }
        if (showFind) {
            highlightFinds(codeArea, displayMode, pageIndex, allFinds)
            highlightFind(codeArea, displayMode, pageIndex, currentFind)
        }
    }

    // highlighting wrapper functions for current or all finds

    private fun highlightFind(codeArea: CodeArea, displayMode: DisplayMode, pageIndex: Int, currentFind: Find) {
        if (currentFind.end == 0L) {
            return
        }
        applyFindHighlight(codeArea, displayMode, pageIndex, currentFind)
    }

    private fun highlightFinds(codeArea: CodeArea, displayMode: DisplayMode, pageIndex: Int, allFinds: List<Find>) {
        if (allFinds.isEmpty()) {
            return
        }
        applyFindHighlight(codeArea, displayMode, pageIndex, *allFinds.toTypedArray())
    }

    // Function to handle display mode when highlighting

    private fun applyFindHighlight(codeArea: CodeArea, displayMode: DisplayMode, pageIndex: Int, vararg finds: Find) {
        val pageSize = this.settingsController.getSettings().pageSize
        if (displayMode == DisplayMode.PLAIN) {
            for (find in finds) {
                highlight(codeArea, find.start.toInt(), find.end.toInt(), ALL_FIND)
            }
        } else {
            val pageOffset = pageIndex * pageSize.toLong()
            for (find in finds) {
                if (searchAndReplaceController.isInPage(find, pageIndex, pageSize)) {
                    val start = max(find.start - pageOffset, 0).toInt()
                    val end = min(find.end - pageOffset, pageSize.toLong() - 1).toInt()
                    highlight(codeArea, start, end, ALL_FIND)
                }
            }
        }
    }


    // Functions to interact with codearea style

    private fun unhighlight(codeArea: CodeArea, start: Int, end: Int, className: String) {
        codeArea.setStyleSpans(start,
                codeArea.getStyleSpans(start, end)
                        .mapStyles { styles -> styles.filter { it != className } }
        )
    }

    private fun highlight(codeArea: CodeArea, start: Int, end: Int, className: String) {
        codeArea.setStyleSpans(start,
                codeArea.getStyleSpans(start, end)
                        .mapStyles { styles -> styles.union(listOf(className)) }
        )
    }

}
