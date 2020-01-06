package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.Styles
import de.henningwobken.vpex.main.controllers.InternalResourceController
import de.henningwobken.vpex.main.controllers.SettingsController
import de.henningwobken.vpex.main.controllers.StringUtils
import de.henningwobken.vpex.main.controllers.VpexExecutor
import de.henningwobken.vpex.main.model.InternalResource
import de.henningwobken.vpex.main.xml.DiffProgressInputStream
import de.henningwobken.vpex.main.xml.ResourceResolver
import de.henningwobken.vpex.main.xml.TotalProgressInputStream
import de.henningwobken.vpex.main.xml.XmlErrorHandler
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import mu.KotlinLogging
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import tornadofx.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.InputStream
import java.lang.Integer.min
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.xml.XMLConstants
import javax.xml.transform.sax.SAXSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Validator


class SchemaResultFragment : Fragment("Schema Validation Result") {

    private val logger = KotlinLogging.logger {}
    private val settingsController by inject<SettingsController>()
    private val internalResourceController by inject<InternalResourceController>()
    private val vpexExecutor by inject<VpexExecutor>()
    private val stringUtils by inject<StringUtils>()
    lateinit var gotoLineColumn: (line: Long, column: Long, file: File?) -> Unit

    private enum class ValidationSeverity {
        WARNING, ERROR, FATAL
    }

    private class SAXExceptionWrapper(val exception: SAXParseException, val severity: ValidationSeverity, val file: File?) {
        val message = SimpleStringProperty(exception.message)
    }

    private val schema = getSchema()
    private val schemaResolver = ResourceResolver(settingsController.getSettings().schemaBasePathList)

    private val progressProperty = SimpleDoubleProperty(0.0)
    private val workingProperty = SimpleBooleanProperty(true)
    private val hasErrorsProperty = SimpleBooleanProperty(false)
    // Compound binding needs to be created here. Otherwise it will fall out of scope and be garbage collected
    private val doneWithoutErrorsProperty = workingProperty.not().and(hasErrorsProperty.not())
    private val exceptions = observableListOf<SAXExceptionWrapper>()
    private val displayedExceptions = observableListOf<SAXExceptionWrapper>()
    private val fileCountProperty = SimpleIntegerProperty(0)
    private val pageSize = 10
    private val page = SimpleIntegerProperty(1)
    private val maxPage = SimpleIntegerProperty(1)
    private val pageDisplayProperty = SimpleIntegerProperty(1)

    override val root = borderpane {
        setOnKeyReleased { keyEvent ->
            if (keyEvent.code == KeyCode.ESCAPE) {
                close()
            }
        }
        prefWidth = 1000.0
        prefHeight = 500.0
        top = hbox {
            removeWhen(hasErrorsProperty.not().and(workingProperty.not()))
            addClass(Styles.primaryHeader)
            label(exceptions.sizeProperty.stringBinding {
                "Found $it warnings or errors"
            }.concat(fileCountProperty.stringBinding {
                val fileString = if (it == 1) "1 File" else "$it Files"
                " in $fileString"
            })) {
                style = "-fx-text-fill: white"
            }
            label {
                ViewHelper.fillHorizontal(this)
            }
            button("Copy All") {
                removeWhen(hasErrorsProperty.not())
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
                    removeWhen(hasErrorsProperty.or(workingProperty))
                    isFitToWidth = true
                    prefHeight = 450.0
                    paddingTop = 20
                    alignment = Pos.TOP_CENTER
                    addClass(Styles.councilBackground)
                    hbox(50) {
                        addClass(Styles.card)
                        paddingAll = 40.0
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
                        vbox(30) {
                            minWidth = Region.USE_PREF_SIZE
                            minHeight = Region.USE_PREF_SIZE
                            alignment = Pos.CENTER_LEFT
                            label("The council has decided.") {
                                style = "-fx-font-weight: bold;"
                            }
                            label("There are no syntactic or schematic errors.")
                        }
                    }
                }
                displayedExceptions.onChange { exceptionChange ->
                    if (exceptionChange.next()) {
                        if (exceptionChange.wasRemoved()) {
                            vbox.children.clear()
                        }
                        vbox.children.addAll(exceptionChange.addedSubList.map { getExceptionNode(it) })
                    }
                }
            }
        }
        bottom = hbox {
            progressbar(progressProperty) {
                hgrow = Priority.ALWAYS
                vgrow = Priority.ALWAYS
                maxHeight = Double.MAX_VALUE
                val progressbar = this
                hasErrorsProperty.onChange {
                    if (it) {
                        progressbar.style = "-fx-accent: red;"
                    }
                }
                doneWithoutErrorsProperty.onChange {
                    if (it) {
                        progressbar.style = "-fx-accent: green;"
                    }
                }
                maxWidth = Double.MAX_VALUE
            }
            /*
                Pagination
            */
            hbox(10) {
                alignment = Pos.CENTER
                removeWhen(exceptions.sizeProperty.lessThanOrEqualTo(pageSize))
                button("<<") {
                    disableWhen {
                        page.isEqualTo(1)
                    }
                }.action {
                    page.set(page.get() - 1)
                }
                hbox(5) {
                    alignment = Pos.CENTER
                    textfield(pageDisplayProperty) {
                        page.onChange {
                            displayPage(it)
                            pageDisplayProperty.set(it)
                        }
                        prefWidth = 50.0
                        maxWidth = 50.0
                    }.action {
                        val enteredPage = pageDisplayProperty.get()
                        if (enteredPage < 1 || enteredPage > maxPage.get()) {
                            pageDisplayProperty.set(page.get())
                        } else {
                            page.set(pageDisplayProperty.get())
                        }
                    }
                    label("/")
                    label(maxPage)
                }
                button(">>") {
                    disableWhen {
                        page.greaterThanOrEqualTo(maxPage)
                    }
                }.action {
                    page.set(page.get() + 1)
                }
            }
        }
    }

    override fun onUndock() {
        super.onUndock()
        if (this.workingProperty.get() && vpexExecutor.isRunning.get()) {
            logger.debug { "Schema Result Window was closed before finishing. Cancelling current Task." }
            vpexExecutor.cancel()
        }
    }

    private fun displayPage(pageNumber: Int) {
        displayedExceptions.clear()
        val fromIndex = pageSize * (pageNumber - 1)
        val tillIndex = min(exceptions.size, pageSize * pageNumber) - 1 // inclusive
        displayedExceptions.addAll(exceptions.slice(IntRange(fromIndex, tillIndex)))
    }

    fun validateSchema(inputStream: InputStream, inputSize: Long) {
        Platform.runLater {
            fileCountProperty.set(1)
        }
        vpexExecutor.execute {
            logger.info("Started validating against schema")
            val validator = getValidator { exception, severity ->
                addException(SAXExceptionWrapper(exception, severity, null))
            }
            try {
                validator.validate(SAXSource(InputSource(TotalProgressInputStream(inputStream) {
                    if (Thread.currentThread().isInterrupted) {
                        throw RuntimeException("Cancelled")
                    }
                    Platform.runLater {
                        progressProperty.set(it / inputSize.toDouble())
                    }
                })))
            } catch (exception: SAXParseException) {
                logger.warn { "Ignoring SAXParseException: ${exception.message}" }
            } catch (exception: RuntimeException) {
                logger.warn { "Validation was cancelled" }
            }
            logger.info("Finished validating against schema")
            setFinished()
        }
    }

    fun validateSchemaForFiles(files: List<File>) {
        Platform.runLater {
            fileCountProperty.set(files.size)
        }
        vpexExecutor.execute {
            // Need to be in a new Thread as to not block ui while waiting
            val bytesTotal = files.map { it.length() }.sum()
            val bytesRead = AtomicLong(0)
            val executor = Executors.newFixedThreadPool(10)
            for (file in files) {
                val worker = Runnable {
                    logger.info("Started validating against schema for file ${file.absolutePath}")
                    val validator = getValidator { exception, severity ->
                        addException(SAXExceptionWrapper(exception, severity, file))
                    }
                    try {
                        validator.validate(SAXSource(InputSource(DiffProgressInputStream(file.inputStream()) {
                            bytesRead.addAndGet(it.toLong())
                        })))
                    } catch (exception: SAXParseException) {
                        logger.warn { "Ignoring SAXParseException: ${exception.message}" }
                    }
                    logger.info("Finished validating against schema for file ${file.absolutePath}")
                }
                executor.execute(worker)
            }
            executor.shutdown()
            while (!executor.isTerminated) {
                if (Thread.currentThread().isInterrupted) {
                    logger.info { "Cancelling validations" }
                    executor.shutdownNow()
                    break
                }
                Platform.runLater {
                    progressProperty.set(bytesRead.get() / (bytesTotal.toDouble()))
                }
                try {
                    Thread.sleep(20)
                } catch (e: InterruptedException) {
                    logger.info { "Cancelling validations" }
                    executor.shutdownNow()
                    break
                }
            }
            logger.info { "Finished all validations" }
            setFinished()
        }
    }

    private fun setFinished() {
        Platform.runLater {
            progressProperty.set(1.0)
            workingProperty.set(false)
        }
    }

    private fun getSchema(): Schema {
        val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        schemaFactory.resourceResolver = schemaResolver
        schemaFactory.errorHandler = XmlErrorHandler()
        return schemaFactory.newSchema()
    }

    private fun getValidator(onException: (exception: SAXParseException, severity: ValidationSeverity) -> Unit): Validator {
        val validator = schema.newValidator()
        validator.resourceResolver = schemaResolver
        validator.errorHandler = object : ErrorHandler {
            @Throws(SAXException::class)
            override fun warning(exception: SAXParseException) {
                onException(exception, ValidationSeverity.WARNING)
            }

            @Throws(SAXException::class)
            override fun fatalError(exception: SAXParseException) {
                logger.error { "Fatal Exception (e.g. syntax error). Abort validation." }
                onException(exception, ValidationSeverity.FATAL)
            }

            @Throws(SAXException::class)
            override fun error(exception: SAXParseException) {
                onException(exception, ValidationSeverity.ERROR)
            }
        }
        return validator
    }

    private fun getExceptionNode(exceptionWrapper: SAXExceptionWrapper): Node {
        val exception = exceptionWrapper.exception
        return hbox {
            paddingTop = 15
            paddingHorizontal = 15
            // For multiline Text areas:
            stylesheets.add(internalResourceController.getAsResource(InternalResource.TEXTAREA_CSS))
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
                    if (exceptionWrapper.file != null) {
                        textfield {
                            addClass(Styles.selectable)
                            text = exceptionWrapper.file.absolutePath
                        }
                    }
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
                    textarea(exceptionWrapper.message) {
                        addClass(Styles.selectableMultiline)
                        val prefRows = stringUtils.countLinesInString(exceptionWrapper.message.get())
                        prefRowCount = prefRows
                        exceptionWrapper.message.onChange {
                            val updatedPrefRows = stringUtils.countLinesInString(exceptionWrapper.message.get())
                            prefRowCount = updatedPrefRows
                            requestLayout()
                        }
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
                    gotoLineColumn(exception.lineNumber.toLong(), 1, exceptionWrapper.file) // TODO: Column number incorrect
                }
            }
        }
    }

    private fun addException(exception: SAXExceptionWrapper) {
        logger.warn(exceptionToString(exception))
        Platform.runLater {
            synchronized(exceptions) {
                val existingException = exceptions.find { e ->
                    e.exception.lineNumber == exception.exception.lineNumber
                            && e.exception.columnNumber == exception.exception.columnNumber
                            && e.severity == exception.severity
                }
                if (existingException != null) {
                    existingException.message += System.lineSeparator() + exception.message.get()
                } else {
                    exceptions.add(exception)
                    if (displayedExceptions.size < pageSize) {
                        displayedExceptions.add(exception)
                    }
                    val pages = (exceptions.size + 1) / pageSize + 1
                    if (pages > maxPage.get()) {
                        maxPage.set(pages)
                    }
                    hasErrorsProperty.set(true)
                }
            }
        }
    }

    private fun exceptionToString(exceptionWrapper: SAXExceptionWrapper): String {
        val exception = exceptionWrapper.exception
        val prefix = "${exceptionWrapper.severity} at ${exception.lineNumber}:${exception.columnNumber} for schema ${exception.publicId} ${exception.systemId}:"
        return exceptionWrapper.message.get().split("\n").joinToString("\n") {
            "$prefix $it"
        }
    }


}
