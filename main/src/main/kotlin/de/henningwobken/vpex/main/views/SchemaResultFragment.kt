package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.Styles
import de.henningwobken.vpex.main.controllers.InternalResourceController
import de.henningwobken.vpex.main.controllers.SettingsController
import de.henningwobken.vpex.main.model.InternalResource
import de.henningwobken.vpex.main.xml.ProgressInputStream
import de.henningwobken.vpex.main.xml.ResourceResolver
import de.henningwobken.vpex.main.xml.XmlErrorHandler
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.image.ImageView
import javafx.scene.layout.Region
import mu.KotlinLogging
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import tornadofx.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.transform.sax.SAXSource
import javax.xml.validation.SchemaFactory


class SchemaResultFragment : Fragment("Schema Validation Result") {

    private val logger = KotlinLogging.logger {}
    private val settingsController by inject<SettingsController>()
    private val internalResourceController by inject<InternalResourceController>()
    lateinit var gotoLineColumn: (line: Long, column: Long) -> Unit

    private enum class ValidationSeverity {
        WARNING, ERROR, FATAL
    }

    private data class SAXExceptionWrapper(val exception: SAXParseException, val severity: ValidationSeverity)

    private val progressProperty = SimpleDoubleProperty(0.0)
    private val workingProperty = SimpleBooleanProperty(true)
    private val hasErrorsProperty = SimpleBooleanProperty(false)
    private val exceptions = observableListOf<SAXExceptionWrapper>()

    override val root = borderpane {
        prefWidth = 1000.0
        prefHeight = 500.0
        top = hbox {
            removeWhen(hasErrorsProperty.not())
            addClass(Styles.primaryHeader)
            label(exceptions.sizeProperty.stringBinding { "Found $it warnings or errors" }) {
                style = "-fx-text-fill: white"
            }
            label {
                ViewHelper.fillHorizontal(this)
            }
            button("Copy All") {
                addClass(Styles.button)
                action {
                    val stringSelection = StringSelection(exceptions.joinToString("\n") { exceptionToString(it) })
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(stringSelection, null)
                    alert(Alert.AlertType.INFORMATION, "Copied", "Error Descriptions were copied to clipboard.")
                }
            }
        }
        center = scrollpane {
            isFitToWidth = true
            vbox {
                val vbox = this
                stackpane {
                    isFitToWidth = true
                    prefHeight = 450.0
                    alignment = Pos.CENTER
                    hbox(50) {
                        addClass(Styles.card)
                        paddingAll = 50.0
                        removeWhen(hasErrorsProperty.or(workingProperty))
                        maxWidth = Region.USE_PREF_SIZE
                        maxHeight = Region.USE_PREF_SIZE
                        alignment = Pos.CENTER
                        hbox {
                            alignment = Pos.CENTER
                            prefWidth = 96.0
                            maxWidth = 96.0
                            label {
                                graphic = internalResourceController.getAsSvg(InternalResource.SUCCESS_ICON)
                                graphic.scaleX = 4.0
                                graphic.scaleY = 4.0
                            }
                        }
                        vbox(40) {
                            minWidth = Region.USE_PREF_SIZE
                            minHeight = Region.USE_PREF_SIZE
                            alignment = Pos.CENTER_LEFT
                            label("The council has decided.")
                            label("This xml file is schematically compliant.")
                        }
                    }
                }
                exceptions.onChange { exceptionChange ->
                    if (exceptionChange.next()) {
                        vbox.children.addAll(exceptionChange.addedSubList.map { getExceptionNode(it) })
                    }
                }
            }
        }
        bottom = progressbar(progressProperty) {
            val progressbar = this
            hasErrorsProperty.onChange {
                if (it) {
                    progressbar.style = "-fx-accent: red;"
                }
            }
            workingProperty.not().and(hasErrorsProperty.not()).onChange {
                if (it) {
                    progressbar.style = "-fx-accent: green;"
                }
            }
            maxWidth = Double.MAX_VALUE
        }
    }

    fun validateSchema(inputStream: InputStream, inputSize: Long) {
        Thread {
            logger.info("Validating against schema")
            val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            schemaFactory.resourceResolver = ResourceResolver(settingsController.getSettings().schemaBasePath)
            schemaFactory.errorHandler = XmlErrorHandler()
            val schema = schemaFactory.newSchema()
            val validator = schema.newValidator()
            validator.resourceResolver = ResourceResolver(settingsController.getSettings().schemaBasePath)
            validator.errorHandler = object : ErrorHandler {
                @Throws(SAXException::class)
                override fun warning(exception: SAXParseException) {
                    addException(SAXExceptionWrapper(exception, ValidationSeverity.WARNING))
                }

                @Throws(SAXException::class)
                override fun fatalError(exception: SAXParseException) {
                    logger.error { "Fatal Exception (e.g. syntax error). Abort validation." }
                    addException(SAXExceptionWrapper(exception, ValidationSeverity.FATAL))
                }

                @Throws(SAXException::class)
                override fun error(exception: SAXParseException) {
                    addException(SAXExceptionWrapper(exception, ValidationSeverity.ERROR))
                }
            }
            try {
                validator.validate(SAXSource(InputSource(ProgressInputStream(inputStream) {
                    Platform.runLater {
                        progressProperty.set(it / inputSize.toDouble())
                    }
                })))
            } catch (exception: SAXParseException) {
                logger.warn { "Ignoring SAXParseException: ${exception.message}" }
            }
            Platform.runLater {
                workingProperty.set(false)
                progressProperty.set(1.0)
            }
        }.start()
    }

    private fun getExceptionNode(exceptionWrapper: SAXExceptionWrapper): Node {
        val exception = exceptionWrapper.exception
        return hbox {
            paddingTop = 15
            paddingHorizontal = 15
            hbox(10) {
                ViewHelper.fillHorizontal(this)
                addClass(Styles.card)
                paddingAll = 10.0
                hbox {
                    alignment = Pos.CENTER
                    prefWidth = 48.0
                    maxWidth = 48.0
                    label {
                        graphic = when (exceptionWrapper.severity) {
                            ValidationSeverity.WARNING -> internalResourceController.getAsSvg(InternalResource.WARN_ICON)
                            ValidationSeverity.ERROR -> internalResourceController.getAsSvg(InternalResource.ERROR_ICON)
                            ValidationSeverity.FATAL -> {
                                val image = ImageView(internalResourceController.getAsImage(InternalResource.FATAL_ICON))
                                image.fitHeight = 48.0
                                image.fitWidth = 48.0
                                image.smoothProperty().set(true)
                                image
                            }
                        }
                        if (exceptionWrapper.severity != ValidationSeverity.FATAL) {
                            graphic.scaleX = 2.0
                            graphic.scaleY = 2.0
                        }
                    }
                }
                vbox {
                    ViewHelper.fillHorizontal(this)
                    textfield {
                        addClass(Styles.selectable)
                        if (exception.publicId != null || exception.systemId != null) {
                            if (exception.publicId != null) {
                                text += exception.publicId
                            }
                            if (exception.systemId != null) {
                                text += exception.systemId
                            }
                            text += ": "
                        }
                        text += "at ${exception.lineNumber}:${exception.columnNumber}"
                    }
                    textfield {
                        addClass(Styles.selectable)
                        text = exception.message
                    }
                }
            }
            label("") {
                prefWidth = 30.0
            }
            button("GOTO") {
                addClass(Styles.button)
                ViewHelper.fillVertical(this)
                action {
                    gotoLineColumn(exception.lineNumber.toLong(), 1) // TODO: Column number incorrect
                }
            }
        }
    }

    private fun addException(exception: SAXExceptionWrapper) {
        logger.warn(exceptionToString(exception))
        Platform.runLater {
            exceptions.add(exception)
            hasErrorsProperty.set(true)
        }
    }

    private fun exceptionToString(exceptionWrapper: SAXExceptionWrapper): String {
        val exception = exceptionWrapper.exception
        return "${exceptionWrapper.severity} at ${exception.lineNumber}:${exception.columnNumber} for schema ${exception.publicId} ${exception.systemId}: ${exception.message}"
    }

}
