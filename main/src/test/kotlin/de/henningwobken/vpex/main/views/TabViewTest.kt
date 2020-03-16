package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.TestUtils
import de.henningwobken.vpex.main.controllers.ExpectedClass.*
import de.henningwobken.vpex.main.controllers.ExpectedStyles
import de.henningwobken.vpex.main.controllers.SettingsController
import de.henningwobken.vpex.main.model.Settings
import javafx.beans.property.SimpleObjectProperty
import javafx.embed.swing.JFXPanel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import tornadofx.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import javax.swing.SwingUtilities

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TabViewTest {

    // region preparations

    @BeforeAll
    fun init() {
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            JFXPanel() // initializes JavaFX environment
            latch.countDown()
        }
        latch.await()
    }

    // endregion preparations

    // region open file

    @Test
    fun openFilePlain() {
        val tabView = initTabView(SettingsController.DEFAULT_SETTINGS.copy(
                pagination = false,
                diskPagination = false
        ))
        val file = writeFile(listOf("test"))
        tabView.openFile(file)
        // the \n is inserted as EOF indicator
        // this must be ok since notepad++ and IntelliJ do the same
        assertEquals("test\n", tabView.codeArea.text)
    }

    @Test
    fun openFilePagination() {
        val tabView = initTabView(SettingsController.DEFAULT_SETTINGS.copy(
                pagination = true,
                paginationThreshold = 1,
                pageSize = 1,
                diskPagination = false
        ))
        val file = writeFile(listOf("test"))
        tabView.openFile(file)
        assertEquals(1, tabView.page.get())
        assertEquals("t", tabView.codeArea.text)
        tabView.moveToPage(2)
        assertEquals(2, tabView.page.get())
        assertEquals("e", tabView.codeArea.text)
        tabView.moveToPage(3)
        assertEquals(3, tabView.page.get())
        assertEquals("s", tabView.codeArea.text)
        tabView.moveToPage(4)
        assertEquals(4, tabView.page.get())
        assertEquals("t", tabView.codeArea.text)
    }

    @Test
    fun openFileDiskPagination() {
        val tabView = initTabView(SettingsController.DEFAULT_SETTINGS.copy(
                pagination = false,
                diskPagination = true,
                diskPaginationThreshold = 0,
                pageSize = 1
        ))
        val file = writeFile(listOf("test"))
        tabView.openFile(file)
        Thread.sleep(100) // need to sleep a bit as changes are executed async
        assertEquals(1, tabView.page.get())
        assertEquals("t", tabView.codeArea.text)
        tabView.moveToPage(2)
        Thread.sleep(100)
        assertEquals(2, tabView.page.get())
        assertEquals("e", tabView.codeArea.text)
        tabView.moveToPage(3)
        Thread.sleep(100)
        assertEquals(3, tabView.page.get())
        assertEquals("s", tabView.codeArea.text)
        tabView.moveToPage(4)
        Thread.sleep(100)
        assertEquals(4, tabView.page.get())
        assertEquals("t", tabView.codeArea.text)
    }

    // endregion open file

    // region syntax highlighting

    @Test
    fun syntaxHighlightingPlain() {
        val tabView = initTabView(SettingsController.DEFAULT_SETTINGS.copy(
                pagination = false,
                diskPagination = false,
                syntaxHighlighting = true
        ))
        val file = writeFile(listOf("<test></test>"))
        tabView.openFile(file)
        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(3, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                // Would be 1, but Code Area expands last style to end of file line break
                ExpectedStyles(2, TAGMARK)
        ), tabView.codeArea)
    }

    @Test
    fun syntaxHighlightingPagination() {
        val tabView = initTabView(SettingsController.DEFAULT_SETTINGS.copy(
                pagination = true,
                paginationThreshold = 1,
                pageSize = 7,
                diskPagination = false,
                syntaxHighlighting = true
        ))
        val file = writeFile(listOf("<test>a</test>"))
        tabView.openFile(file)
        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(1, ANYDATA)
        ), tabView.codeArea)
        tabView.moveToPage(2)
        TestUtils.checkStyle(listOf(
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK)
        ), tabView.codeArea)
    }

    @Test
    fun syntaxHighlightingDiskPagiantion() {
        val tabView = initTabView(SettingsController.DEFAULT_SETTINGS.copy(
                diskPagination = true,
                diskPaginationThreshold = 0,
                pageSize = 7,
                syntaxHighlighting = true
        ))
        val file = writeFile(listOf("<test>a</test>"))
        tabView.openFile(file)
        Thread.sleep(100) // need to sleep a bit as changes are executed async
        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(1, ANYDATA)
        ), tabView.codeArea)
        tabView.moveToPage(2)
        Thread.sleep(100)
        TestUtils.checkStyle(listOf(
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG),
                ExpectedStyles(1, TAGMARK)
        ), tabView.codeArea)
    }

    // endregion syntax highlighting

    // region navigate through all finds

    /*
        Scenario:

        - User opens file "<test>a</test>
        - User searches all occurrences of "test" => first "test" should be the selected all find
        - User moves to next find
        - User moves back to first find
     */

    @Test
    fun navigateThroughAllFindsPlain() {
        val tabView = initTabView(SettingsController.DEFAULT_SETTINGS.copy(
                pagination = false,
                diskPagination = false,
                syntaxHighlighting = true
        ))
        val file = writeFile(listOf("<test>a</test>"))
        tabView.openFile(file)
        tabView.openSearch()
        tabView.findTextField.text = "test"
        tabView.onFindAllClicked()

        Thread.sleep(100)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(1, ANYDATA),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT),
                ExpectedStyles(2, TAGMARK)
        ), tabView.codeArea)

        tabView.onNextAllFindClicked()

        Thread.sleep(100)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(1, ANYDATA),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(2, TAGMARK)
        ), tabView.codeArea)

        tabView.onLastAllFindClicked()

        Thread.sleep(100)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(1, ANYDATA),
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT),
                ExpectedStyles(2, TAGMARK)
        ), tabView.codeArea)

    }

    @Test
    fun navigateThroughAllFindsPagination() {
        val tabView = initTabView(SettingsController.DEFAULT_SETTINGS.copy(
                pagination = true,
                paginationThreshold = 1,
                pageSize = 7,
                diskPagination = false,
                syntaxHighlighting = true
        ))
        val file = writeFile(listOf("<test>a</test>"))
        tabView.openFile(file)
        tabView.openSearch()
        tabView.findTextField.text = "test"
        tabView.onFindAllClicked()

        Thread.sleep(100)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(1, ANYDATA)
        ), tabView.codeArea)

        tabView.onNextAllFindClicked()

        Thread.sleep(100)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK)
        ), tabView.codeArea)

        tabView.onLastAllFindClicked()

        Thread.sleep(100)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(1, ANYDATA)
        ), tabView.codeArea)
    }

    @Test
    fun navigateThroughAllFindsDiskPagination() {
        val tabView = initTabView(SettingsController.DEFAULT_SETTINGS.copy(
                diskPagination = true,
                diskPaginationThreshold = 0,
                pageSize = 7,
                syntaxHighlighting = true
        ))
        val file = writeFile(listOf("<test>a</test>"))
        tabView.openFile(file)

        Thread.sleep(100)

        tabView.openSearch()
        tabView.findTextField.text = "test"
        tabView.onFindAllClicked()

        Thread.sleep(100)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(1, ANYDATA)
        ), tabView.codeArea)

        tabView.onNextAllFindClicked()

        Thread.sleep(100)

        TestUtils.checkStyle(listOf(
                ExpectedStyles(2, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK)
        ), tabView.codeArea)

        tabView.onLastAllFindClicked()

        Thread.sleep(100)

        assertEquals(1, tabView.page.get())
        TestUtils.checkStyle(listOf(
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(4, ANYTAG, SEARCH_ALL_HIGHLIGHT, SEARCH_HIGHLIGHT),
                ExpectedStyles(1, TAGMARK),
                ExpectedStyles(1, ANYDATA)
        ), tabView.codeArea)
    }

    // endregion navigate through all finds

    // region helper methods

    private fun writeFile(lines: List<String>): File {
        val file = File.createTempFile("vpex", ".test")
        Files.write(file.toPath(), lines)
        return file
    }

    private fun initTabView(settings: Settings): TabView {
        val settingsController = Mockito.mock(SettingsController::class.java)
        val scope = Scope(
                settingsController
        )
        Mockito.`when`(settingsController.getSettings()).thenReturn(settings)
        Mockito.`when`(settingsController.settingsProperty).thenReturn(SimpleObjectProperty())
        return FX.find(TabView::class.java, scope)
    }

    // endregion helper methods
}
