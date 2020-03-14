package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.controllers.ExpectedClass.*
import de.henningwobken.vpex.main.model.DisplayMode
import de.henningwobken.vpex.main.model.Find
import org.fxmisc.richtext.CodeArea
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tornadofx.*

internal class HighlightingControllerTest {
    private val scope = Scope(StringUtils(), SearchAndReplaceController(), HighlightingController())
    private val highlightingExecutor = FX.getComponents(scope)[HighlightingController::class] as HighlightingController

    @Test
    fun queueTextChangedTask() {

    }

    @Test
    fun `highlight find next simple`() {
        val codeArea = CodeArea()
        codeArea.replaceText("test1testtest")
        highlightingExecutor.nextFind(codeArea, 0, 0, 0, 4)

        checkStyle(listOf(
                ExpectedStyles(4, SEARCH_HIGHLIGHT),
                ExpectedStyles(9, NONE)
        ), codeArea)

        highlightingExecutor.nextFind(codeArea, 0, 4, 5, 9)

        checkStyle(listOf(
                ExpectedStyles(5, NONE),
                ExpectedStyles(4, SEARCH_HIGHLIGHT),
                ExpectedStyles(4, NONE)
        ), codeArea)

        highlightingExecutor.nextFind(codeArea, 5, 9, 9, 13)

        checkStyle(listOf(
                ExpectedStyles(9, NONE),
                ExpectedStyles(4, SEARCH_HIGHLIGHT)
        ), codeArea)

    }

    @Test
    fun `highlight find next with xml highlighting`() {
        val codeArea = CodeArea()
        codeArea.replaceText("<test>test</test>")
        highlightingExecutor.highlightEverything(codeArea, listOf(), Find(0L, 0L), DisplayMode.PLAIN, 0, true)

        checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, NONE),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK)
        ), codeArea)

        highlightingExecutor.nextFind(codeArea, 0, 0, 1, 5)

        checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, NONE),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK)
        ), codeArea)

        highlightingExecutor.nextFind(codeArea, 1, 5, 6, 10)

        checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, SEARCH_HIGHLIGHT),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK)
        ), codeArea)

        highlightingExecutor.nextFind(codeArea, 6, 10, 12, 16)

        checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, NONE),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK)
        ), codeArea)
    }

    private fun toCamelCase(string: String): String? {
        val camelCaseString = string.split("_").map { toProperCase(it) }.joinToString("")
        return camelCaseString[0].toLowerCase() + camelCaseString.substring(1)
    }

    private fun toProperCase(string: String): String? {
        return string.substring(0, 1).toUpperCase() +
                string.substring(1).toLowerCase()
    }

    private fun checkStyle(expectedStyles: List<ExpectedStyles>, codeArea: CodeArea) {
        val styleSpans = codeArea.getStyleSpans(0, codeArea.text.length)

        for (index in expectedStyles.indices) {
            val expectedStyle = expectedStyles[index]
            val actualStyle = styleSpans.getStyleSpan(index)
            assertEquals(expectedStyle.length, actualStyle.length, "Length of Style differs. Expected $expectedStyle but found $actualStyle")

            val styleDifferenceMessage = "Failed at style no $index: expected ${expectedStyle.styles} but found ${actualStyle.style}"
            if (expectedStyle.styles == listOf(NONE)) {
                assertEquals(0, actualStyle.style.size, styleDifferenceMessage)
            } else {
                assertEquals(expectedStyle.styles.size, actualStyle.style.size, styleDifferenceMessage)
                val actualStyleClasses = actualStyle.style.toMutableList()
                for (styleClassIndex in expectedStyle.styles.indices) {
                    val expectedStyleClass = expectedStyle.styles[styleClassIndex]
                    val actualStyleClass = actualStyleClasses[styleClassIndex]
                    assertEquals(toCamelCase(expectedStyleClass.name), actualStyleClass, styleDifferenceMessage)
                }
            }
        }

        assertEquals(expectedStyles.size, styleSpans.spanCount)
    }

}
