package de.henningwobken.vpex.main.controllers;

import de.henningwobken.vpex.main.model.DisplayMode
import de.henningwobken.vpex.main.model.Find
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.model.PlainTextChange
import tornadofx.*
import kotlin.math.max
import kotlin.math.min

class HighlightingExecutor : Controller() {

    private val xmlSyntaxHighlightingController by inject<XmlSyntaxHighlightingController>()
    private val settingsController by inject<SettingsController>()
    private val searchAndReplaceController by inject<SearchAndReplaceController>()

    fun removeFinds(codeArea: CodeArea) {
        val toRemove = listOf("searchHighlight", "searchAllHighlight")
        // TODO: merge old styles if possible
        val styleSpans = codeArea.getStyleSpans(0, codeArea.text.length)
        for (styleSpan in styleSpans) {
            styleSpan.style.removeAll(toRemove)
        }
    }

    fun nextFind(codeArea: CodeArea, oldFindStart: Int, oldFindEnd: Int, newFindStart: Int, newFindEnd: Int) {
//        val pageSize = settingsController.getSettings().pageSize
//        val offset = when(displayMode) {
//            DisplayMode.PLAIN -> 0
//            else -> pageIndex * pageSize
//        }
//        if (oldFind != null && searchAndReplaceController.isInPage(oldFind, pageIndex, pageSize)) {
//            val oldStyleSpans = codeArea.getStyleSpans((oldFind.start - offset).toInt(), (oldFind.end - offset).toInt())
//
//        }
        if (oldFindEnd > 0) {
            codeArea.setStyleSpans(oldFindStart,
                    codeArea.getStyleSpans(oldFindStart, oldFindEnd)
                            .mapStyles { styles -> styles.filter { it != "searchHighlight" } }
            )
//            for (oldStyleSpan in oldStyleSpans) {
//                oldStyleSpan.style.remove("searchHighlight")
//            }
        }
        codeArea.setStyleSpans(newFindStart,
                codeArea.getStyleSpans(newFindStart, newFindEnd)
                        .mapStyles { styles -> styles.union(listOf("searchHighlight")) }
        )
//        newStyleSpans.
//        codeArea.setStyle(newFindStart, newFindEnd, listOf("searchHighlight"))
//        for (newStyleSpan in newStyleSpans) {
//            newStyleSpan.style = newStyleSpan.style.union(listOf("searchHighlight"))
//        }
    }

    fun allFinds() {
        // TODO ?
    }

    fun queueTextChangedTask(codeArea: CodeArea, textChange: PlainTextChange) {
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

    fun queueFindTask() {

    }

    fun queueHighlightingTask(codeArea: CodeArea, allFinds: List<Find>, startFrom: Int, displayMode: DisplayMode, pageIndex: Int, showFind: Boolean) {
        // TODO: Different tasks?
        // - Remove finds
        // - part of text changed
        // - all of text changed
        codeArea.clearStyle(startFrom, codeArea.length)
        if (settingsController.getSettings().syntaxHighlighting) {
            codeArea.setStyleSpans(startFrom, xmlSyntaxHighlightingController.computeHighlighting(codeArea.text.substring(startFrom)))
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
