package de.henningwobken.vpex.main.view

import de.henningwobken.vpex.main.TestUtils
import de.henningwobken.vpex.main.Vpex
import de.henningwobken.vpex.main.controllers.SettingsController
import de.henningwobken.vpex.main.model.Settings
import de.henningwobken.vpex.main.views.MainView
import javafx.event.ActionEvent
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.stage.Stage
import org.fxmisc.richtext.CodeArea
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.testfx.api.FxRobot
import org.testfx.api.FxToolkit
import org.testfx.assertions.api.Assertions
import org.testfx.framework.junit5.ApplicationExtension
import org.testfx.framework.junit5.Start
import tornadofx.*
import java.io.File
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ApplicationExtension::class)
class MainViewTest {

    private val mockSettingsController = Mockito.mock(SettingsController::class.java)

    private lateinit var robot: FxRobot
    private lateinit var stage: Stage
    private lateinit var codeArea: CodeArea
    private lateinit var findTextField: TextField
    private lateinit var replaceTextField: TextField
    private lateinit var replaceAllButton: Button
    private lateinit var findAllButton: Button
    private lateinit var mainView: MainView


    /**
     * Will be called with {@code @Before} semantics, i. e. before each test method.
     *
     * @param stage - Will be injected by the test runner.
     */
    @Start
    private fun start(stage: Stage) {
        this.stage = stage
        stage.show()
    }

    @BeforeAll
    fun before() {
        setTestSettings()
        FX.defaultScope.set(mockSettingsController)
        FxToolkit.registerPrimaryStage()
        FxToolkit.setupApplication(Vpex::class.java)
        robot = FxRobot()
        codeArea = robot.lookup("#codeArea").queryAs(CodeArea::class.java)
        findTextField = robot.lookup("#findField").queryAs(TextField::class.java)
        replaceTextField = robot.lookup("#replaceField").queryAs(TextField::class.java)
        replaceAllButton = robot.lookup("#replaceAll").queryAs(Button::class.java)
        findAllButton = robot.lookup("#findAll").queryAs(Button::class.java)
        mainView = FX.getComponents()[MainView::class] as MainView
    }

    @AfterAll
    fun after() {
        FxToolkit.hideStage()
    }

    @Test
    fun replaceAllPlainSimple() {
        setTestSettings()
        testReplaceAll(
                file = TestUtils.loadResource("/ui_tests/simple1.txt"),
                searchText = "asdf",
                replacementText = "qwer",
                uiResultText = "qwer\n"
        )
    }

    @Test
    fun replaceAllPlainDuplicate() {
        setTestSettings()
        testReplaceAll(
                file = TestUtils.loadResource("/ui_tests/simple2.txt"),
                searchText = "abab",
                replacementText = "cdcd",
                uiResultText = "cdcdcdcd\n"
        )
    }

    @Test
    fun findAllDiskPagination() {
        setTestSettings(pageSize = 10, paginationThreshold = 0, diskPaginationThreshold = 0)
        Assertions.assertThat(mainView).isNotNull
        robot.interact {
            mainView.openFile(TestUtils.loadResource("/ui_tests/paginated.txt"))
        }
        Thread.sleep(20)
        robot.interact {
            Assertions.assertThat(codeArea.text).isEqualTo("Page1eins\n")
        }
        // TODO: Test find all
    }


    private fun setTestSettings(pageSize: Int = 1000000, paginationThreshold: Int = 30000000, diskPaginationThreshold: Int = 500) {
        Mockito.`when`(mockSettingsController.getSettings()).thenReturn(Settings(
                schemaBasePathList = listOf("./"),
                wrapText = true,
                prettyPrintIndent = 4,
                locale = Locale.ENGLISH,
                pagination = true,
                pageSize = pageSize,
                paginationThreshold = paginationThreshold,
                autoUpdate = true,
                proxyHost = "",
                proxyPort = null,
                memoryIndicator = true,
                saveLock = false,
                diskPagination = true,
                diskPaginationThreshold = diskPaginationThreshold,
                trustStore = "",
                trustStorePassword = "",
                insecure = false,
                contextMenu = false
        ))
    }

    private fun testReplaceAll(file: File, searchText: String, replacementText: String, uiResultText: String) {
        robot.interact {
            mainView.openFile(file)
            findTextField.text = searchText
            replaceTextField.text = replacementText
            replaceAllButton.onAction.handle(ActionEvent())
        }
        Thread.sleep(50)
        robot.interact {
            println(codeArea.text)
            Assertions.assertThat(uiResultText).isEqualTo(codeArea.text)
        }
    }

    private fun testFindAll(file: File, searchText: String) {
        robot.interact {
            mainView.openFile(file)
            findTextField.text = searchText
            findAllButton.onAction.handle(ActionEvent())
        }
        Thread.sleep(50)
        robot.interact {
            println(codeArea.text)
//            Assertions.assertThat(uiResultText).isEqualTo(codeArea.text)
        }
    }
}
