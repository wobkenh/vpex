package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.TestUtils
import de.henningwobken.vpex.main.controllers.ExpectedClass.*
import de.henningwobken.vpex.main.model.DisplayMode
import de.henningwobken.vpex.main.model.Find
import de.henningwobken.vpex.main.model.Settings
import javafx.beans.property.SimpleObjectProperty
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.model.PlainTextChange
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import tornadofx.*

internal class HighlightingControllerTest {

    @Test
    fun `highlighting after text change`() {
        val highlightingController = initController(SettingsController.DEFAULT_SETTINGS.copy(
                syntaxHighlighting = true
        ))
        val codeArea = CodeArea()
        codeArea.replaceText("<test>test</test>")
        highlightingController.highlightEverything(codeArea, listOf(), Find(0L, 0L), DisplayMode.PLAIN, 0, false)
        codeArea.replaceText(13, 13, " ") // invalidates closing tag
        val plainTextChange = PlainTextChange(13, "", " ")
        highlightingController.textChanged(codeArea, plainTextChange)
        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK), // <
                ExpectedStyles(4, ANYTAG),  // test
                ExpectedStyles(1, TAGMARK), // >
                ExpectedStyles(4),          // test
                ExpectedStyles(2, TAGMARK), // </
                ExpectedStyles(1, ANYTAG),  // t
                ExpectedStyles(4),          // " est"
                ExpectedStyles(1, TAGMARK)  // >
        ), codeArea)
    }

    @Test
    fun `highlight everything with find`() {
        val highlightingController = initController(SettingsController.DEFAULT_SETTINGS.copy(
                syntaxHighlighting = true
        ))
        val codeArea = CodeArea()
        codeArea.replaceText("<test>test</test>")
        val allFinds = listOf(Find(1L, 5L), Find(6L, 10L), Find(12L, 16L))
        val currentFind = Find(12L, 16L)
        highlightingController.highlightEverything(codeArea, allFinds, currentFind, DisplayMode.PLAIN, 0, true)
        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, SEARCH_ALL_HIGHLIGHT),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK)
        ), codeArea)
    }

    @Test
    fun `highlight everything with find without syntax`() {
        val highlightingController = initController(SettingsController.DEFAULT_SETTINGS.copy(
                syntaxHighlighting = false
        ))
        val codeArea = CodeArea()
        codeArea.replaceText("<test>test</test>")
        val allFinds = listOf(Find(1L, 5L), Find(6L, 10L), Find(12L, 16L))
        val currentFind = Find(12L, 16L)
        highlightingController.highlightEverything(codeArea, allFinds, currentFind, DisplayMode.PLAIN, 0, true)
        TestUtils.checkStyle(listOf(
                ExpectedStyles(1),
                ExpectedStyles(4, SEARCH_ALL_HIGHLIGHT),
                ExpectedStyles(1),
                ExpectedStyles(4, SEARCH_ALL_HIGHLIGHT),
                ExpectedStyles(2),
                ExpectedStyles(4, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(1)
        ), codeArea)
    }


    @Test
    fun `highlight everything with find but find turned off`() {
        val highlightingController = initController(SettingsController.DEFAULT_SETTINGS.copy(
                syntaxHighlighting = true
        ))
        val codeArea = CodeArea()
        codeArea.replaceText("<test>test</test>")
        val allFinds = listOf(Find(1L, 5L), Find(6L, 10L), Find(12L, 16L))
        val currentFind = Find(12L, 16L)
        highlightingController.highlightEverything(codeArea, allFinds, currentFind, DisplayMode.PLAIN, 0, false)
        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK)
        ), codeArea)
    }

    @Test
    fun `highlight everything with find paginated`() {
        val highlightingController = initController(SettingsController.DEFAULT_SETTINGS.copy(
                syntaxHighlighting = true,
                pageSize = 17
        ))
        val codeArea = CodeArea()
        codeArea.replaceText("<test>test</test>")
        val allFinds = listOf(Find(18L, 22L), Find(23L, 27L), Find(29L, 33L))
        val currentFind = Find(29L, 33L)
        highlightingController.highlightEverything(codeArea, allFinds, currentFind, DisplayMode.DISK_PAGINATION, 1, true)
        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, SEARCH_ALL_HIGHLIGHT),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK)
        ), codeArea)
    }

    @Test
    fun `highlight find next simple`() {
        val highlightingController = initController(SettingsController.DEFAULT_SETTINGS.copy(
                syntaxHighlighting = true
        ))
        val codeArea = CodeArea()
        codeArea.replaceText("test1testtest")
        highlightingController.nextFind(codeArea, 0, 0, 0, 4)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(4, SEARCH_HIGHLIGHT),
                ExpectedStyles(9, NONE)
        ), codeArea)

        highlightingController.nextFind(codeArea, 0, 4, 5, 9)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(5, NONE),
                ExpectedStyles(4, SEARCH_HIGHLIGHT),
                ExpectedStyles(4, NONE)
        ), codeArea)

        highlightingController.nextFind(codeArea, 5, 9, 9, 13)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(9, NONE),
                ExpectedStyles(4, SEARCH_HIGHLIGHT)
        ), codeArea)

    }

    @Test
    fun `highlight find next with xml highlighting`() {
        val highlightingController = initController(SettingsController.DEFAULT_SETTINGS.copy(
                syntaxHighlighting = true
        ))
        val codeArea = CodeArea()
        codeArea.replaceText("<test>test</test>")
        highlightingController.highlightEverything(codeArea, listOf(), Find(0L, 0L), DisplayMode.PLAIN, 0, true)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, NONE),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK)
        ), codeArea)

        highlightingController.nextFind(codeArea, 0, 0, 1, 5)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, NONE),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK)
        ), codeArea)

        highlightingController.nextFind(codeArea, 1, 5, 6, 10)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, SEARCH_HIGHLIGHT),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK)
        ), codeArea)

        highlightingController.nextFind(codeArea, 6, 10, 12, 16)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, NONE),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK)
        ), codeArea)
    }

    private fun initController(settings: Settings = SettingsController.DEFAULT_SETTINGS): HighlightingController {
        val settingsController = Mockito.mock(SettingsController::class.java)

        Mockito.`when`(settingsController.getSettings()).thenReturn(settings)
        Mockito.`when`(settingsController.settingsProperty).thenReturn(SimpleObjectProperty())
        val scope = Scope(StringUtils(), SearchAndReplaceController(), settingsController)
//        return FX.getComponents(scope)[HighlightingController::class] as HighlightingController
        return FX.find(HighlightingController::class.java, scope)
    }

}
