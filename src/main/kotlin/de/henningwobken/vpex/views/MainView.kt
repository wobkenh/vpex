package de.henningwobken.vpex.views

import de.henningwobken.vpex.Styles
import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.control.Alert.AlertType.INFORMATION
import javafx.scene.control.TextInputDialog
import javafx.stage.FileChooser
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.LineNumberFactory
import org.xml.sax.InputSource
import tornadofx.*
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.SAXParserFactory
import kotlin.math.max
import kotlin.math.min


class MainView : View("VPEX: View, parse and edit large XML Files") {
    private var codeArea: CodeArea by singleAssign()
    private var isDirty: BooleanProperty = SimpleBooleanProperty(false)
    private var file: File? = null
    override val root = borderpane {
        addClass(Styles.welcomeScreen)
        top {
            menubar {
                menu("File") {
                    item("Open").action {
                        openFile()
                    }
                    item("Save", "Shortcut+S").action {
                        saveFile()
                    }
                    item("Save as", "Shortcut+Shift+S").action {
                        saveFileAs()
                    }
                }
                menu("View") {
                    item("Move to", "Shortcut+G").action {
                        moveTo()
                    }
                }
                menu("Validate") {
                    item("Syntax", "Shortcut+H").action {
                        validateSyntax()
                    }
                    item("Schema", "Shortcut+J").action {
                        validateSchema()
                    }
                }
                menu("Settings") {
                    item("Settings")
                }
            }
        }
        center = getVirtualScrollPane(getRichTextArea())
        bottom {
            hbox {
                paddingAll = 10.0
                label {
                    bind(isDirty.stringBinding {
                        if (it!!) {
                            "Dirty"
                        } else {
                            "Unchanged"
                        }
                    })
                }
            }
        }
    }

    private fun moveTo() {
        val dialog = TextInputDialog("Line:Column")
        dialog.title = "Move to..."
        val stringOptional = dialog.showAndWait()
        stringOptional.ifPresent {
            val userLine: Int
            val userColumn: Int
            if (it.contains(":")) {
                val split = it.split(":")
                userLine = split[0].toInt()
                userColumn = split[1].toInt()
            } else {
                userLine = it.toInt()
                userColumn = 0
            }
            val line = max(min(userLine, codeArea.paragraphs.size), 1) - 1
            val column = min(userColumn, codeArea.getParagraph(line).length())
            Platform.runLater {
                codeArea.moveTo(codeArea.position(line, column).toOffset())
                codeArea.requestFollowCaret()
                println("Moved to $userLine")
            }
        }
    }

    private fun validateSyntax() {
        println("Validating Syntax...")
        val text = codeArea.text
        val saxParserFactory = SAXParserFactory.newInstance()
        saxParserFactory.isNamespaceAware = true
        val saxParser = saxParserFactory.newSAXParser()
        val xmlReader = saxParser.xmlReader
        xmlReader.parse(InputSource(text.byteInputStream()))
        alert(INFORMATION, "The council has decided", "Die Syntax dieser XML-Datei ist valide!")
    }

    private fun validateSchema() {
        println("Validating against schema")
        // TODO: Config Base Path
    }

    private fun saveFile() {
        println("Saving")
        val file = this.file
        if (file != null) {
            val text = codeArea.text
            Files.write(file.toPath(), text.toByteArray())
            println("Saved")
        } else {
            saveFileAs()
        }
    }

    private fun saveFileAs() {
        println("Saving as")
        val fileChooser = FileChooser();
        fileChooser.title = "Save as"
        val file = fileChooser.showSaveDialog(FX.primaryStage)
        if (file != null) {
            this.file = file
            val text = codeArea.text
            Files.write(file.toPath(), text.toByteArray())
            println("Saved as")
        }
    }

    private fun openFile() {
        println("Opening new file")
        val fileChooser = FileChooser();
        fileChooser.title = "Open new File"
        val file = fileChooser.showOpenDialog(FX.primaryStage)
        if (file != null && file.exists()) {
            this.file = file
            this.codeArea.replaceText(file.readText())
            this.isDirty.set(false)
        }
    }

    private fun getVirtualScrollPane(codeArea: CodeArea): VirtualizedScrollPane<CodeArea> {
        return VirtualizedScrollPane(codeArea)
    }

    private fun getRichTextArea(): CodeArea {
        println("Creating text area")
        val codeArea = CodeArea()
        codeArea.wrapTextProperty().set(true)
        codeArea.plainTextChanges().subscribe {
            this.isDirty.set(true)
        }
        this.codeArea = codeArea
        codeArea.paragraphGraphicFactory = LineNumberFactory.get(codeArea)
        return codeArea
    }
}