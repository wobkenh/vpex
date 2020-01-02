package de.henningwobken.vpex.main.view

import de.henningwobken.vpex.main.Vpex
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
import org.testfx.api.FxRobot
import org.testfx.api.FxToolkit
import org.testfx.assertions.api.Assertions
import org.testfx.framework.junit5.ApplicationExtension
import org.testfx.framework.junit5.Start


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ApplicationExtension::class)
class MainViewTest {

    private lateinit var robot: FxRobot
    private lateinit var stage: Stage
    private lateinit var codeArea: CodeArea
    private lateinit var findTextField: TextField
    private lateinit var replaceTextField: TextField
    private lateinit var replaceAllButton: Button


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
        FxToolkit.registerPrimaryStage()
        FxToolkit.setupApplication(Vpex::class.java)
        robot = FxRobot()
        codeArea = robot.lookup("#codeArea").queryAs(CodeArea::class.java)
        findTextField = robot.lookup("#findField").queryAs(TextField::class.java)
        replaceTextField = robot.lookup("#replaceField").queryAs(TextField::class.java)
        replaceAllButton = robot.lookup("#replaceAll").queryAs(Button::class.java)
    }

    @AfterAll
    fun after() {
        FxToolkit.hideStage()
    }

    @Test
    fun replaceAllPlainSimple() {
        testReplaceAll(
                uiText = "asdf",
                searchText = "asdf",
                replacementText = "qwer",
                uiResultText = "qwer"
        )
    }

    @Test
    fun replaceAllPlainDuplicate() {
        testReplaceAll(
                uiText = "abababab",
                searchText = "abab",
                replacementText = "cdcd",
                uiResultText = "cdcdcdcd"
        )
    }

    private fun testReplaceAll(uiText: String, searchText: String, replacementText: String, uiResultText: String) {
        robot.interact {
            codeArea.replaceText(uiText)
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
}
