package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.controllers.*
import de.henningwobken.vpex.main.model.Settings
import de.henningwobken.vpex.main.xml.XmlFormattingService
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

    private val settingsController = Mockito.mock(SettingsController::class.java)

    private val scope = Scope(
            StringUtils(),
            SearchAndReplaceController(),
            InternalResourceController(),
            settingsController,
            XmlFormattingService(),
            VpexExecutor(),
            FileCalculationController(),
            FileWatcher(),
            HighlightingController()
    )

    @BeforeAll
    fun init() {
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            JFXPanel() // initializes JavaFX environment
            latch.countDown()
        }
        latch.await()
    }

    @Test
    fun moveTo() {
    }

    @Test
    fun moveToPage() {
    }

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

    private fun writeFile(lines: List<String>): File {
        val file = File.createTempFile("vpex", ".test")
        Files.write(file.toPath(), lines)
        return file
    }

    private fun initTabView(settings: Settings): TabView {
        Mockito.`when`(settingsController.getSettings()).thenReturn(settings)
        Mockito.`when`(settingsController.settingsProperty).thenReturn(SimpleObjectProperty())
        return FX.find(TabView::class.java, scope)
    }
}
