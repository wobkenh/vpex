package de.henningwobken.vpex.views

import de.henningwobken.vpex.Styles
import de.henningwobken.vpex.controllers.SettingsController
import de.henningwobken.vpex.xml.ResourceResolver
import de.henningwobken.vpex.xml.XmlErrorHandler
import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.geometry.Pos
import javafx.scene.control.Alert.AlertType.INFORMATION
import javafx.scene.control.TextInputDialog
import javafx.scene.layout.Priority
import javafx.stage.FileChooser
import javafx.util.Duration
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.LineNumberFactory
import org.xml.sax.InputSource
import tornadofx.*
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.text.NumberFormat
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import kotlin.math.max
import kotlin.math.min


class MainView : View("VPEX: View, parse and edit large XML Files") {
    private val settingsController: SettingsController by inject()
    private var codeArea: CodeArea by singleAssign()
    private val isDirty: BooleanProperty = SimpleBooleanProperty(false)
    private val charCountProperty = SimpleIntegerProperty(0)
    private var numberFormat = NumberFormat.getInstance(settingsController.getSettings().locale)
    private var file: File? = null
    override val root = borderpane {
        top {
            menubar {
                menu("File") {
                    item("Open", "Shortcut+O").action {
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
                menu("Edit") {
                    item("Pretty print", "Shortcut+Shift+F").action {
                        prettyPrint()
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
                    item("Settings", "Shortcut+Alt+S").action {
                        replaceWith<SettingsView>(ViewTransition.Metro(Duration.millis(500.0)))
                    }
                    item("About").action {
                        replaceWith<AboutView>(ViewTransition.Metro(Duration.millis(500.0)))
                    }
                }
            }
        }
        center = getVirtualScrollPane(getRichTextArea())
        bottom {
            hbox {
                hgrow = Priority.ALWAYS
                alignment = Pos.CENTER
                label {
                    paddingAll = 10.0
                    prefWidth = 110.0
                    toggleClass(Styles.changed, isDirty)
                    toggleClass(Styles.unchanged, isDirty.not())
                    bind(isDirty.stringBinding {
                        if (it!!) {
                            "Dirty"
                        } else {
                            "Unchanged"
                        }
                    })
                }
                label("") {
                    hgrow = Priority.ALWAYS
                    maxWidth = Int.MAX_VALUE.toDouble()
                }
                button("Close").action {
                    file = null
                    codeArea.replaceText("")
                    isDirty.set(false)
                }
                hbox(10) {
                    paddingAll = 10.0
                    label("Lines:")
                    label(codeArea.paragraphs.sizeProperty.stringBinding {
                        numberFormat.format(it)
                    })
                    label("Chars:")
                    label(charCountProperty.stringBinding {
                        numberFormat.format(it)
                    })
                }

            }
        }
    }

    override fun onDock() {
        super.onDock()
        codeArea.wrapTextProperty().set(settingsController.getSettings().wrapText)
        numberFormat = NumberFormat.getInstance(settingsController.getSettings().locale)
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
        alert(INFORMATION, "The council has decided", "The syntax of this xml file is valid.")
    }

    private fun validateSchema() {
        println("Validating against schema")
        val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        schemaFactory.resourceResolver = ResourceResolver(settingsController.getSettings().schemaBasePath)
        schemaFactory.errorHandler = XmlErrorHandler()
        val schema = schemaFactory.newSchema()
        val validator = schema.newValidator()
        validator.resourceResolver = ResourceResolver(settingsController.getSettings().schemaBasePath)
        validator.validate(SAXSource(InputSource(codeArea.text.byteInputStream())))
        alert(INFORMATION, "The council has decided", "This xml file is schematically compliant.")
    }

    private fun prettyPrint() {
        val transformerFactory = TransformerFactory.newInstance()
        transformerFactory.setAttribute("indent-number", settingsController.getSettings().prettyPrintIndent)
        val transformer = transformerFactory.newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        // Otherwise the root tag will be written into the first line
        // see https://stackoverflow.com/questions/18249490/since-moving-to-java-1-7-xml-document-element-does-not-indent
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes")
        val stringWriter = StringWriter()
        val xmlOutput = StreamResult(stringWriter)
        val xmlInput = StreamSource(StringReader(this.codeArea.text))
        transformer.transform(xmlInput, xmlOutput)
        this.codeArea.replaceText(xmlOutput.writer.toString())
    }

    private fun saveFile() {
        println("Saving")
        val file = this.file
        if (file != null) {
            val text = codeArea.text
            Files.write(file.toPath(), text.toByteArray())
            isDirty.set(false)
            println("Saved")
        } else {
            saveFileAs()
        }
    }

    private fun saveFileAs() {
        println("Saving as")
        val fileChooser = FileChooser();
        fileChooser.title = "Save as"
        fileChooser.initialDirectory = File(settingsController.getSettings().openerBasePath)
        val file = fileChooser.showSaveDialog(FX.primaryStage)
        if (file != null) {
            this.file = file
            val text = codeArea.text
            Files.write(file.toPath(), text.toByteArray())
            setFileTitle(file)
            isDirty.set(false)
            println("Saved as")
        }
    }

    private fun openFile() {
        println("Opening new file")
        val fileChooser = FileChooser();
        fileChooser.title = "Open new File"
        fileChooser.initialDirectory = File(settingsController.getSettings().openerBasePath)
        val file = fileChooser.showOpenDialog(FX.primaryStage)
        if (file != null && file.exists()) {
            this.file = file
            setFileTitle(file)
            this.codeArea.replaceText(file.readText())
            this.isDirty.set(false)
        }
    }

    private fun setFileTitle(file: File) {
        val filePathTooLong = file.absolutePath.length > 100
        val filePath = if (filePathTooLong) {
            "..." + file.absolutePath.substring(file.absolutePath.length - 100)
        } else {
            "" + file.absolutePath
        }
        title = "VPEX - $filePath"
    }

    private fun getVirtualScrollPane(codeArea: CodeArea): VirtualizedScrollPane<CodeArea> {
        return VirtualizedScrollPane(codeArea)
    }

    private fun getRichTextArea(): CodeArea {
        println("Creating text area")
        val codeArea = CodeArea()
        println("Setting wrap text to " + settingsController.getSettings().wrapText)
        codeArea.wrapTextProperty().set(settingsController.getSettings().wrapText)
        codeArea.plainTextChanges().subscribe {
            this.isDirty.set(true)
            this.charCountProperty.set(this.codeArea.text.length)
        }
        this.codeArea = codeArea
        codeArea.paragraphGraphicFactory = LineNumberFactory.get(codeArea)
        return codeArea
    }
}