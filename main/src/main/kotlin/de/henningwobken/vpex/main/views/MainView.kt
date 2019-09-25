package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.Styles
import de.henningwobken.vpex.main.controllers.InternalResourceController
import de.henningwobken.vpex.main.controllers.SettingsController
import de.henningwobken.vpex.main.controllers.StringUtils
import de.henningwobken.vpex.main.controllers.UpdateController
import de.henningwobken.vpex.main.model.InternalResource
import de.henningwobken.vpex.main.model.SearchDirection
import de.henningwobken.vpex.main.model.TextInterpreterMode
import de.henningwobken.vpex.main.model.XmlCharState
import de.henningwobken.vpex.main.xml.ResourceResolver
import de.henningwobken.vpex.main.xml.XmlErrorHandler
import javafx.application.Platform
import javafx.beans.property.*
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType.INFORMATION
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.TextInputDialog
import javafx.scene.input.KeyCode
import javafx.scene.input.TransferMode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.FileChooser
import javafx.util.Duration
import mu.KotlinLogging
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.CodeArea
import org.xml.sax.InputSource
import tornadofx.*
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.text.NumberFormat
import java.util.regex.Pattern
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round


class MainView : View("VPEX: View, parse and edit large XML Files") {
    private val logger = KotlinLogging.logger {}
    private val internalResourceController: InternalResourceController by inject()
    private val settingsController: SettingsController by inject()
    private val updateController: UpdateController by inject()
    private val stringUtils: StringUtils by inject()

    private var codeArea: CodeArea by singleAssign()
    private val isDirty: BooleanProperty = SimpleBooleanProperty(false)
    private val charCountProperty = SimpleIntegerProperty(0)
    private var numberFormat = NumberFormat.getInstance(settingsController.getSettings().locale)
    private var file: File? = null
    private var lineCount = SimpleIntegerProperty(0)
    private val statusTextProperty = SimpleStringProperty("")
    private val progressProperty = SimpleDoubleProperty(-1.0)

    // Memory monitor
    private val maxMemory = round(Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0).toLong()
    private val allocatedMemory = SimpleLongProperty(0)
    private val reservedMemory = SimpleLongProperty(0)
    private var memoryMonitorThread: Thread? = null
    private var stopMonitorThread = false
    private val showMonitorThread = SimpleBooleanProperty(false)

    // Search and Replace

    private data class Find(val start: Int, val end: Int)

    private val showReplaceProperty = SimpleBooleanProperty(false)
    private val showFindProperty = SimpleBooleanProperty(false)
    private val findProperty = SimpleStringProperty("")
    private val replaceProperty = SimpleStringProperty("")
    private var lastFindStart = 0
    private var lastFindEnd = 0
    private val allFinds = mutableListOf<Find>()
    private val allFindsSize = SimpleIntegerProperty(-1)
    private val hasFindProperty = SimpleBooleanProperty(false)
    private val searchDirection = SimpleObjectProperty<Any>()
    private val textInterpreterMode = SimpleObjectProperty<Any>()
    private val regexPatternMap = mutableMapOf<String, Pattern>()
    private val ignoreCaseProperty = SimpleBooleanProperty(false)

    // Pagination
    private var fullText: String = ""
    private val pagination = SimpleBooleanProperty()
    private val page = SimpleIntegerProperty(1)
    private var maxPage = SimpleIntegerProperty(0)
    private var pageLineCounts = IntArray(0) // Line count of each page
    private var pageStartingLineCounts = IntArray(0) // For each page the number of lines before this page
    private val pageTotalLineCount = SimpleIntegerProperty(0)
    private var textOperationLock = false // If set to true, ignore changes in code area for line counts / dirty detection
    private var dirtySinceLastSync = false

    init {
        internalResourceController.getAsStrings(InternalResource.BANNER).forEach(::println)
        if (settingsController.getSettings().autoUpdate) {
            statusTextProperty.set("Checking for updates")
            logger.info { "Checking for updates" }
            if (updateController.updateAvailable()) {
                logger.info { "Update available. Downloading." }
                statusTextProperty.set("Downloading updates")
                progressProperty.set(0.0)
                updateController.downloadUpdate(progressCallback = { progress, max ->
                    Platform.runLater {
                        progressProperty.set(progress / (max * 1.0))
                    }
                }, finishCallback = {
                    Platform.runLater {
                        logger.info { "Download finished." }
                        progressProperty.set(-1.0)
                        statusTextProperty.set("")
                        confirm("New Version", "A new version has been downloaded. Restart?", ButtonType.OK, ButtonType.CANCEL, actionFn = {
                            updateController.applyUpdate()
                        })
                    }
                })
            } else {
                logger.info { "Up to date." }
                statusTextProperty.set("")
            }
        }
        FX.primaryStage.setOnCloseRequest {
            if (memoryMonitorThread != null) {
                stopMonitorThread = true
                logger.info { "Set stop flag for memory monitor thread" }
            }
        }
    }

    // UI
    override val root: BorderPane = borderpane {
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
                    item("Search", "Shortcut+F").action {
                        showReplaceProperty.set(false)
                        showFindProperty.set(true)
                    }
                }
                menu("Edit") {
                    item("Pretty print", "Shortcut+Shift+F").action {
                        prettyPrint()
                    }
                    item("Ugly print", "Shortcut+Alt+Shift+F").action {
                        confirm("Ugly print is experimental", "Please check the result after using this.", ButtonType.OK, ButtonType.CANCEL, actionFn = {
                            uglyPrint()
                        })
                    }
                    item("Replace", "Shortcut+R").action {
                        showReplaceProperty.set(true)
                        showFindProperty.set(false)
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
        center = vbox {
            hbox(20) {
                this.removeWhen(showReplaceProperty.not().and(showFindProperty.not()))
                form {
                    hbox(20) {
                        fieldset {
                            field("Find") {
                                textfield(findProperty) {
                                    val textfield = this
                                    showReplaceProperty.onChange {
                                        if (it) {
                                            textfield.requestFocus()
                                        }
                                    }
                                    showFindProperty.onChange {
                                        if (it) {
                                            textfield.requestFocus()
                                        }
                                    }
                                    this.setOnKeyPressed {
                                        // Select the find if there was a find and the user did not change his position in the code area
                                        if (it.code == KeyCode.TAB && hasFindProperty.get() && lastFindStart == codeArea.anchor) {
                                            codeArea.requestFocus()
                                            codeArea.selectRange(codeArea.anchor, codeArea.anchor + findProperty.get().length)
                                            it.consume()
                                        } else if (it.code == KeyCode.ESCAPE) {
                                            closeSearchAndReplace()
                                        }
                                    }
                                    this.setOnAction {
                                        findNext()
                                    }
                                }

                            }
                            field("Replace") {
                                removeWhen(showReplaceProperty.not())
                                textfield(replaceProperty)
                            }
                        }
                        fieldset {
                            field {
                                togglegroup(searchDirection) {
                                    val toggleGroup = this
                                    vbox(10) {
                                        radiobutton("Up", toggleGroup, SearchDirection.UP)
                                        radiobutton("Down", toggleGroup, SearchDirection.DOWN) {
                                            this.selectedProperty().set(true)
                                        }
                                    }
                                }
                            }
                        }
                        fieldset {
                            field {
                                togglegroup(textInterpreterMode) {
                                    val toggleGroup = this
                                    vbox(10) {
                                        radiobutton("Normal", toggleGroup, TextInterpreterMode.NORMAL) {
                                            this.selectedProperty().set(true)
                                        }
                                        radiobutton("Extended", toggleGroup, TextInterpreterMode.EXTENDED)
                                        radiobutton("Regex", toggleGroup, TextInterpreterMode.REGEX)
                                    }
                                }
                            }
                        }
                        fieldset {
                            field {
                                checkbox("ignore case", ignoreCaseProperty)
                            }
                        }
                        fieldset {
                            hbox(5) {
                                vbox(5) {
                                    button("Find next") {
                                        fillHorizontal(this)
                                    }.action {
                                        findNext()
                                    }
                                    button("Find all") {
                                        fillHorizontal(this)
                                    }.action {
                                        searchAll()
                                        highlightAll()
                                    }
                                    button("List all") {
                                        fillHorizontal(this)
                                    }.action {
                                        // TODO: open popup with code snippets of matches
                                    }
                                }
                                vbox(5) {
                                    button("Replace this") {
                                        enableWhen { hasFindProperty.and(showReplaceProperty) }
                                        fillHorizontal(this)
                                    }.action {
                                        codeArea.replaceText(lastFindStart, lastFindEnd, replaceProperty.get())
                                    }
                                    button("Replace all") {
                                        enableWhen { showReplaceProperty }
                                        fillHorizontal(this)
                                    }.action {
                                        replaceAll()
                                    }
                                    button("Count") {
                                        fillHorizontal(this)
                                    }.action {
                                        searchAll()
                                    }
                                }
                            }
                        }
                        fieldset {
                            visibleWhen(allFindsSize.greaterThan(-1))
                            label(allFindsSize.stringBinding { "${it!!} matches" })
                        }
                    }
                }
                label("") {
                    hgrow = Priority.ALWAYS
                    maxWidth = Int.MAX_VALUE.toDouble()
                }
                button("X").action {
                    closeSearchAndReplace()
                }
            }
            add(getVirtualScrollPane(getRichTextArea()))
        }
        bottom {
            hbox(10) {
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
                    fillHorizontal(this)
                }
                label(statusTextProperty)
                progressbar(progressProperty) {
                    removeWhen(progressProperty.lessThan(0))
                }
                hbox(10) {
                    alignment = Pos.CENTER
                    visibleWhen(pagination)
                    button("<<") {
                        disableWhen {
                            page.isEqualTo(1)
                        }
                    }.action {
                        val dirty = isDirty.get()
                        moveToPage(page.get() - 1)
                        isDirty.set(dirty)
                    }
                    hbox(5) {
                        alignment = Pos.CENTER
                        // TODO: Textfield
                        label(page)
                        label("/")
                        label(maxPage)
                    }
                    button(">>") {
                        disableWhen {
                            page.greaterThanOrEqualTo(maxPage)
                        }
                    }.action {
                        val dirty = isDirty.get()
                        moveToPage(page.get() + 1)
                        isDirty.set(dirty)
                    }
                }
                button("Close").action {
                    file = null
                    replaceText("")
                    fullText = ""
                    lineCount.bind(codeArea.paragraphs.sizeProperty())
                    pagination.set(false)
                    isDirty.set(false)
                }
                hbox(10) {
                    paddingAll = 10.0
                    label("Lines:")
                    label(lineCount.stringBinding {
                        numberFormat.format(it)
                    })
                    label("Chars:")
                    label(charCountProperty.stringBinding {
                        numberFormat.format(it)
                    })
                }
                var memoryLabel: Label? = null
                label(allocatedMemory.stringBinding {
                    if (memoryLabel != null) {
                        val percentAllocated = round((allocatedMemory.get() / (maxMemory * 1.0)) * 100)
                        val percentReserved = round((reservedMemory.get() / (maxMemory * 1.0)) * 100)
                        memoryLabel!!.style = "-fx-background-color: linear-gradient(to right, #0A92BF $percentAllocated%, #0ABFEE $percentAllocated%, #0ABFEE $percentReserved%, #eee $percentReserved%)"
                    }
                    "${it}MB of ${maxMemory}MB"
                }) {
                    paddingHorizontal = 5
                    isFillHeight = true
                    maxHeight = Double.MAX_VALUE
                    removeWhen(showMonitorThread.not())
                    memoryLabel = this
                }

            }
        }

        // Drag and Drop handling
        setOnDragOver { event ->
            if (event.dragboard.hasFiles()) {
                /* allow for both copying and moving, whatever user chooses */
                event.acceptTransferModes(TransferMode.COPY)
            }
            event.consume()
        }
        setOnDragDropped { event ->
            val dragboard = event.dragboard
            var success = false
            if (dragboard.hasFiles()) {
                val file = dragboard.files.first()
                if (file.exists() && file.isFile) {
                    openFile(file)
                }
                success = true
            }
            /* let the source know whether the string was successfully
                 * transferred and used */
            event.isDropCompleted = success

            event.consume()
        }

    }

    private fun fillHorizontal(region: Region) {
        region.hgrow = Priority.ALWAYS
        region.maxWidth = Int.MAX_VALUE.toDouble()
    }

    private fun closeSearchAndReplace() {
        this.removeFindHighlighting()
        showReplaceProperty.set(false)
        showFindProperty.set(false)
    }

    override fun onDock() {
        super.onDock()
        logger.info("Docking view")
        codeArea.wrapTextProperty().set(settingsController.getSettings().wrapText)
        numberFormat = NumberFormat.getInstance(settingsController.getSettings().locale)
        checkForPagination()
        if (settingsController.getSettings().memoryIndicator) {
            if (memoryMonitorThread == null) {
                startMemoryMonitorThread()
                showMonitorThread.set(true)
            }
        } else {
            if (memoryMonitorThread != null) {
                stopMonitorThread = true
                memoryMonitorThread = null
                showMonitorThread.set(false)
            }
        }
    }

    private fun startMemoryMonitorThread() {
        val thread = Thread {
            while (true) {
                if (stopMonitorThread) {
                    stopMonitorThread = false
                    logger.info { "Stopping memory monitor thread" }
                    break
                }
                val runtime = Runtime.getRuntime()
                val allocatedMemory = runtime.totalMemory() - runtime.freeMemory()
                val reservedMemory = runtime.totalMemory()
                logger.trace {
                    "Allocated: $allocatedMemory - Reserved: $reservedMemory - Max: $maxMemory"
                }
                Platform.runLater {
                    this.allocatedMemory.set(round(allocatedMemory / 1024.0 / 1024.0).toLong())
                    this.reservedMemory.set(round(reservedMemory / 1024.0 / 1024.0).toLong())
                }
                Thread.sleep(3000)
            }
        }
        thread.start()
        memoryMonitorThread = thread
    }

    private fun replaceText(text: String) {
        this.textOperationLock = true
        this.codeArea.replaceText(text)
        this.textOperationLock = false
    }

    private fun checkForPagination() {
        logger.info("Checking for Pagination")
        if (this.settingsController.getSettings().pagination) {
            // Pagination might have been disabled previously
            // Text might still be only in codeArea
            val textLength = max(this.fullText.length, this.codeArea.text.length)
            if (textLength > this.settingsController.getSettings().paginationThreshold) {
                if (!pagination.get()) {
                    pagination.set(true)
                    logger.info("Pagination was previously disabled. Setting up pagination")
                    val wasDirty = this.isDirty.get()
                    if (this.fullText == "") {
                        logger.info("Saving Code from CodeArea to FullText")
                        this.dirtySinceLastSync = true
                        this.fullText = this.codeArea.text
                    }
                    this.moveToPage(1, SyncDirection.TO_CODEAREA)
                    this.lineCount.bind(this.pageTotalLineCount)
                    this.isDirty.set(wasDirty)
                }
                logger.info("Pagination enabled")
            } else {
                disablePagination()
                logger.info("Pagination disabled since text is too short")
            }
        } else {
            disablePagination()
            logger.info("Pagination disabled in config")
        }
    }

    private fun disablePagination() {
        val wasDirty = this.isDirty.get()
        this.pagination.set(false)
        if (this.codeArea.text.length < this.fullText.length) {
            replaceText(this.fullText)
        }
        this.fullText = ""
        this.lineCount.bind(this.codeArea.paragraphs.sizeProperty)
        this.isDirty.set(wasDirty)
    }

    private fun calcLinesAllPages() {
        this.pageLineCounts = IntArray(this.maxPage.get())
        this.pageStartingLineCounts = IntArray(this.maxPage.get())
        val pageSize = this.settingsController.getSettings().pageSize
        for (page in 1..maxPage.get()) {
            val pageIndex = page - 1
            pageLineCounts[pageIndex] = stringUtils.countLinesInString(this.fullText.substring(pageIndex * pageSize, min(this.fullText.length, page * pageSize)))
        }
        // for page 1 there are no previous pages
        pageStartingLineCounts[0] = 0
        for (page in 2..maxPage.get()) {
            val pageIndex = page - 1
            // minus 1 since page break introduces a "fake" line break
            pageStartingLineCounts[pageIndex] = pageLineCounts[pageIndex - 1] + pageStartingLineCounts[pageIndex - 1] - 1
        }
        this.pageTotalLineCount.set(pageStartingLineCounts.last() + pageLineCounts.last())
        logger.info("Set max lines to ${pageTotalLineCount.get()}")
    }

    private fun calcMaxPage(): Int {
        val max = ceil(this.fullText.length / this.settingsController.getSettings().pageSize.toDouble()).toInt()
        logger.info("Setting max page to $max")
        return max
    }

    private enum class SyncDirection {
        TO_FULLTEXT, TO_CODEAREA
    }

    /**
     * Replaces the text in the codearea with the text of the given page.
     * This code automatically recalculates line numbers/counts if the text has changed.
     *
     * @param page page number (not index)
     * @param syncDirection Code changes are not instantly reflected in the corresponding full text string.
     *      In such cases, a sync might be necessary in either direction:
     *      a) TO_FULLTEXT: from codearea to fulltext (e.g. user made a change in the editor)
     *      or
     *      b) TO_CODEAREA: from fulltext to codearea (e.g. fulltext was replaced due to formatting action)
     */
    private fun moveToPage(page: Int, syncDirection: SyncDirection) {
        logger.info("Moving to page $page with sync direction $syncDirection")
        log.info("Moving to page $page with sync direction $syncDirection")
        val pageSize = settingsController.getSettings().pageSize
        lastFindEnd = 0
        lastFindStart = 0
        hasFindProperty.set(false)
        if (dirtySinceLastSync) {
            if (syncDirection == SyncDirection.TO_FULLTEXT) {
                logger.info("Syncing CodeArea text to full text")
                this.fullText = this.fullText.replaceRange((this.page.get() - 1) * pageSize, min(this.page.get() * pageSize, this.fullText.length), this.codeArea.text)
            } else {
                logger.info("Syncing full text to CodeArea")
            }
            this.maxPage.set(calcMaxPage())
            this.calcLinesAllPages()
            this.dirtySinceLastSync = false
        }
        this.page.set(page)
        replaceText(this.fullText.substring((page - 1) * pageSize, min(page * pageSize, this.fullText.length)))
    }

    private fun moveToPage(page: Int) {
        moveToPage(page, SyncDirection.TO_FULLTEXT)
    }

    /**
     * Asks the user for input to go to a specific position in the file
     */
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
            if (pagination.get()) {
                moveToPage(this.page.get()) // Might change starting line counts if there are unsaved changes
                var page = 1
                for (i in this.maxPage.get() downTo 1) {
                    if (userLine > pageStartingLineCounts[i - 1]) {
                        page = i
                        break
                    }
                }
                if (page != this.page.get()) {
                    moveToPage(page)
                }
                val line = max(min(userLine - pageStartingLineCounts[page - 1], codeArea.paragraphs.size), 1) - 1
                val column = min(userColumn, codeArea.getParagraph(line).length())
                moveToLineColumn(line, column)
            } else {
                val line = max(min(userLine, codeArea.paragraphs.size), 1) - 1
                val column = min(userColumn, codeArea.getParagraph(line).length())
                moveToLineColumn(line, column)
            }
        }
    }

    private fun moveToIndex(index: Int) {
        if (pagination.get()) {
            val page = getPageOfIndex(index)
            if (page != this.page.get()) {
                moveToPage(page)
            }
            this.moveToLineColumn(0, index - ((this.page.get() - 1) * this.settingsController.getSettings().pageSize))
        } else {
            moveToLineColumn(0, index)
        }
    }

    private fun getPageOfIndex(index: Int): Int {
        return ceil(index / this.settingsController.getSettings().pageSize.toDouble()).toInt()
    }

    /**
     * Moves the cursor to the specified line/column
     */
    private fun moveToLineColumn(line: Int, column: Int) {
        Platform.runLater {
            codeArea.moveTo(codeArea.position(line, column).toOffset())
            codeArea.requestFollowCaret()
            logger.info("Moved to $line:$column")
        }
    }

    private fun validateSyntax() {
        logger.info("Validating Syntax...")
        val saxParserFactory = SAXParserFactory.newInstance()
        saxParserFactory.isNamespaceAware = true
        val saxParser = saxParserFactory.newSAXParser()
        val xmlReader = saxParser.xmlReader
        xmlReader.parse(InputSource(getFullText().byteInputStream()))
        alert(INFORMATION, "The council has decided", "The syntax of this xml file is valid.")
    }

    private fun validateSchema() {
        logger.info("Validating against schema")
        val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        schemaFactory.resourceResolver = ResourceResolver(settingsController.getSettings().schemaBasePath)
        schemaFactory.errorHandler = XmlErrorHandler()
        val schema = schemaFactory.newSchema()
        val validator = schema.newValidator()
        validator.resourceResolver = ResourceResolver(settingsController.getSettings().schemaBasePath)
        validator.validate(SAXSource(InputSource(getFullText().byteInputStream())))
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
        val xmlInput = StreamSource(StringReader(getFullText()))
        transformer.transform(xmlInput, xmlOutput)
        if (pagination.get()) {
            changeFullText(xmlOutput.writer.toString())
        } else {
            this.codeArea.replaceText(xmlOutput.writer.toString())
        }
    }

    private fun uglyPrint() {
        // TODO: CDATA
        // Trim each line
        val fulltext = this.getFullText()
        val stringBuilder = StringBuilder()
        var dataText = ""
        var state: XmlCharState = XmlCharState.BETWEEN
        var ignoreCharCount = 0
        for (index in fulltext.indices) {
            if (ignoreCharCount > 0) {
                ignoreCharCount--
                continue
            }
            val char = fulltext[index]
            when (state) {
                XmlCharState.BETWEEN -> {
                    // By only appending '<', we effectively remove everything else between a closing and an opening tag
                    if (char == '<') {
                        stringBuilder.append(char)
                        state = when (fulltext[index + 1]) {
                            '?' -> XmlCharState.XML_TAG
                            '/' -> XmlCharState.CLOSING_TAG
                            '!' -> if (fulltext[index + 2] == '[') XmlCharState.CDATA else XmlCharState.COMMENT
                            else -> XmlCharState.OPENING_TAG
                        }
                    }
                }
                XmlCharState.OPENING_TAG -> {
                    stringBuilder.append(char)
                    if (char == '>') {
                        state = XmlCharState.DATA
                    }
                }
                XmlCharState.CLOSING_TAG -> {
                    stringBuilder.append(char)
                    if (char == '>') {
                        state = XmlCharState.BETWEEN
                    }
                }
                XmlCharState.DATA -> {
                    // After an opening tag, there might be either data or another tag.
                    // If it is another tag, we need to remove it
                    // If it is data, we need to keep it
                    // Therefore, build a tmp string as long as you are in data and add when you know what you are
                    dataText += char
                    if (char == '<') {
                        state = if (fulltext[index + 1] == '/') {
                            // This was really data
                            stringBuilder.append(dataText)
                            XmlCharState.CLOSING_TAG
                        } else {
                            stringBuilder.append("<")
                            if (fulltext[index + 1] == '!') {
                                if (fulltext[index + 2] == '[') XmlCharState.CDATA else XmlCharState.COMMENT
                            } else {
                                XmlCharState.OPENING_TAG
                            }
                        }
                        dataText = ""
                    }
                }
                XmlCharState.XML_TAG -> {
                    stringBuilder.append(char)
                    if (char == '>') {
                        state = XmlCharState.BETWEEN
                    }
                }
                XmlCharState.COMMENT -> {
                    stringBuilder.append(char)
                    if (char == '-' && fulltext[index + 1] == '-' && fulltext[index + 2] == '>') {
                        stringBuilder.append("->")
                        ignoreCharCount = 2
                        state = XmlCharState.BETWEEN
                    }
                }
                XmlCharState.CDATA -> {
                    stringBuilder.append(char)
                    if (char == ']' && fulltext[index + 1] == ']' && fulltext[index + 2] == '>') {
                        stringBuilder.append("]>")
                        ignoreCharCount = 2
                        state = XmlCharState.DATA
                    }
                }
            }
        }
        if (pagination.get()) {
            changeFullText(stringBuilder.toString())
        } else {
            this.codeArea.replaceText(stringBuilder.toString())
        }
    }

    private fun changeFullText(fullText: String) {
        this.dirtySinceLastSync = true
        this.isDirty.set(true)
        this.fullText = fullText
        this.moveToPage(1, SyncDirection.TO_CODEAREA)
    }

    private fun saveFile() {
        logger.info("Saving")
        val file = this.file
        if (file != null) {
            val text = getFullText()
            Files.write(file.toPath(), text.toByteArray())
            isDirty.set(false)
            logger.info("Saved")
        } else {
            saveFileAs()
        }
    }

    private fun saveFileAs() {
        logger.info("Saving as")
        val fileChooser = FileChooser()
        fileChooser.title = "Save as"
        fileChooser.initialDirectory = File(settingsController.getSettings().openerBasePath).absoluteFile
        val file = fileChooser.showSaveDialog(FX.primaryStage)
        if (file != null) {
            this.file = file
            val text = getFullText()
            Files.write(file.toPath(), text.toByteArray())
            setFileTitle(file)
            isDirty.set(false)
            logger.info("Saved as")
        }
    }

    private fun openFile() {
        logger.info("Opening new file")
        val fileChooser = FileChooser()
        fileChooser.title = "Open new File"
        fileChooser.initialDirectory = File(settingsController.getSettings().openerBasePath).absoluteFile
        val file = fileChooser.showOpenDialog(FX.primaryStage)
        if (file != null && file.exists()) {
            openFile(file)
        }
    }

    private fun openFile(file: File) {
        logger.info("Opening file ${file.absolutePath}")
        this.file = file
        setFileTitle(file)
        if (this.settingsController.getSettings().pagination) {
            this.fullText = file.readText()
            this.dirtySinceLastSync = true
            checkForPagination()
        } else {
            replaceText(file.readText())
        }
        this.codeArea.moveTo(0, 0)
        this.isDirty.set(false)
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
        val scrollPane = VirtualizedScrollPane(codeArea)
        scrollPane.vgrow = Priority.ALWAYS
        return scrollPane
    }

    private fun getRichTextArea(): CodeArea {
        logger.info("Creating text area")
        val codeArea = CodeArea()
        logger.info("Setting wrap text to " + settingsController.getSettings().wrapText)
        codeArea.wrapTextProperty().set(settingsController.getSettings().wrapText)
        codeArea.plainTextChanges().subscribe {
            this.charCountProperty.set(
                    if (this.pagination.get()) {
                        this.fullText.length - this.settingsController.getSettings().pageSize + this.codeArea.text.length
                    } else {
                        this.codeArea.text.length
                    }
            )
            if (!this.textOperationLock) {
                // Only dirty if user changed something
                this.isDirty.set(true)
                this.dirtySinceLastSync = true
                if (this.pagination.get()) {
                    val insertedLines = this.stringUtils.countLinesInString(it.inserted) - 1
                    val removedLines = this.stringUtils.countLinesInString(it.removed) - 1
                    this.pageTotalLineCount.set(this.pageTotalLineCount.get() + insertedLines - removedLines)
                }
            }
            if (this.hasFindProperty.get()) {
                // Edit invalidated search result => Remove Highlight
                if (this.codeArea.text.substring(this.lastFindStart, this.lastFindEnd) != this.findProperty.get()) {
                    logger.info("Removed Highlighting because search result was invalidated through editing")
                    removeFindHighlighting()
                }
            }
        }
        this.codeArea = codeArea
        this.codeArea.setOnKeyPressed {
            if (it.code == KeyCode.ESCAPE) {
                closeSearchAndReplace()
            }
        }
        this.codeArea.stylesheets.add(internalResourceController.getAsResource(InternalResource.EDITOR_CSS))
        // Original: LineNumberFactory.get(codeArea)
        codeArea.paragraphGraphicFactory = PaginatedLineNumberFactory(codeArea) {
            // Needs to return the starting line count of the current page
            if (this.pagination.get()) {
                pageStartingLineCounts[this.page.get() - 1]
            } else {
                0
            }
        }

        return codeArea
    }

    private fun getFullText(): String {
        return if (this.pagination.get()) {
            // fullText might be out of sync
            if (this.isDirty.get()) {
                moveToPage(this.page.get()) // Syncs the page with the fulltext
            }
            this.fullText
        } else this.codeArea.text
    }


    // Search and replace functions

    private fun removeFindHighlighting() {
        this.codeArea.clearStyle(0, codeArea.length - 1)
        this.hasFindProperty.set(false)
        this.lastFindStart = 0
        this.lastFindEnd = 0
    }

    private fun searchAll() {
        this.allFinds.clear()
        val fullText = getFullText()
        val searchText = getSearchText()
        val ignoreCase = ignoreCaseProperty.get()
        val interpreterMode = this.textInterpreterMode.get() as TextInterpreterMode
        if (interpreterMode == TextInterpreterMode.REGEX) {
            val patternString = if (ignoreCase) "(?i)$searchText" else searchText
            val pattern = regexPatternMap.getOrPut(patternString) { Pattern.compile(patternString) }
            val matcher = pattern.matcher(fullText)
            while (matcher.find()) {
                this.allFinds.add(Find(matcher.start(), matcher.end()))
            }
        } else {
            var index = fullText.indexOf(searchText, 0, ignoreCase)
            while (index < fullText.length && index >= 0) {
                this.allFinds.add(Find(index, index + searchText.length))
                index = fullText.indexOf(searchText, index + 1, ignoreCase)
            }
        }
        allFindsSize.set(allFinds.size)
    }

    private fun highlightAll() {
        this.removeFindHighlighting()
        if (allFinds.isEmpty()) {
            return
        }
        moveToIndex(allFinds[0].start)
        lastFindStart = allFinds[0].start
        lastFindEnd = allFinds[0].end
        val pageSize = this.settingsController.getSettings().pageSize
        for (find in allFinds) {
            this.hasFindProperty.set(true)
            if (this.pagination.get() && isInPage(find)) {
                val start = max(find.start - ((this.page.get() - 1) * pageSize), 0)
                val end = min(find.start - (this.page.get() * pageSize), pageSize - 1)
                codeArea.setStyle(start, end, listOf("searchHighlight"))
            } else {
                codeArea.setStyle(find.start, find.end, listOf("searchHighlight"))
            }
        }
    }

    private fun replaceAll() {
        this.searchAll()
        if (allFinds.isEmpty()) {
            logger.info("No Replacements made")
            return
        }
        val fulltext = getFullText()
        val stringBuilder = StringBuilder()
        val replacementText = this.replaceProperty.get()
        var lastEndIndex = 0
        for (find in allFinds) {
            stringBuilder.append(fulltext.substring(lastEndIndex, find.start))
            stringBuilder.append(replacementText)
            lastEndIndex = find.end
        }
        stringBuilder.append(fulltext.substring(lastEndIndex))
        if (pagination.get()) {
            changeFullText(stringBuilder.toString())
        } else {
            this.codeArea.replaceText(stringBuilder.toString())
        }
    }

    private fun isInPage(find: Find): Boolean {
        val pageSize = this.settingsController.getSettings().pageSize
        return find.end >= (this.page.get() - 1) * pageSize || find.start < this.page.get() * pageSize
    }

    private fun findNext() {
        // Find out where to start searching
        val offset = if (pagination.get()) {
            codeArea.caretPosition + (this.page.get() - 1) * this.settingsController.getSettings().pageSize
        } else {
            codeArea.caretPosition
        }

        // Find text to search in and text to search for
        val fullText = getFullText()
        val searchText = getSearchText()
        val ignoreCase = ignoreCaseProperty.get()
        val interpreterMode = this.textInterpreterMode.get() as TextInterpreterMode

        // Search
        val find: Find? = if (this.searchDirection.get() as SearchDirection == SearchDirection.UP) {
            // UP
            if (interpreterMode == TextInterpreterMode.REGEX) {
                val patternString = if (ignoreCase) "(?i)$searchText" else searchText
                val pattern = regexPatternMap.getOrPut(patternString) { Pattern.compile(patternString) }
                // End index of substring is exclusive, so no -1
                val matcher = pattern.matcher(fullText)
                var regexStartIndex = -1
                var regexEndIndex = -1
                // TODO: This goes through all the matches. This is inefficient.
                while (true) {
                    try {
                        val found = matcher.find()
                        if (!found || matcher.end() > offset) {
                            break
                        }
                        regexStartIndex = matcher.start()
                        regexEndIndex = matcher.end()
                    } catch (e: Exception) {
                        break
                    }
                }
                if (regexStartIndex >= 0) Find(regexStartIndex, regexEndIndex) else null
            } else {
                // - 1 to exclude current search result
                val startIndex = fullText.lastIndexOf(searchText, offset - 1, ignoreCase)
                if (startIndex >= 0) {
                    Find(startIndex, startIndex + searchText.length)
                } else null
            }
        } else {
            // DOWN
            if (interpreterMode == TextInterpreterMode.REGEX) {
                val patternString = if (ignoreCase) "(?i)$searchText" else searchText
                val pattern = regexPatternMap.getOrPut(patternString) { Pattern.compile(patternString) }
                val matcher = pattern.matcher(fullText)
                if (matcher.find(offset + 1)) {
                    Find(matcher.start(), matcher.end())
                } else null
            } else {
                // + 1 to exclude current search result
                // TODO: This does not work if the work if the search term is next to the cursor
                //          and there hasn't been a find yet
                val startIndex = fullText.indexOf(searchText, offset + 1, ignoreCase)
                if (startIndex >= 0) {
                    Find(startIndex, startIndex + searchText.length)
                } else null
            }
        }

        // Move to search result
        if (find != null) {
            moveToIndex(find.start)
            Platform.runLater {
                val findStart = codeArea.anchor
                val findLength = find.end - find.start
                val findEnd = codeArea.anchor + findLength
                codeArea.clearStyle(lastFindStart, lastFindEnd)
                codeArea.setStyle(findStart, findEnd, listOf("searchHighlight"))
                lastFindStart = findStart
                lastFindEnd = findEnd
                this.hasFindProperty.set(true)
            }
        } else {
            // TODO: first enter => overlay at bottom right saying there are no more results and fading out after x seconds
            //      second enter => start at beginning of file
            //          no alert, vlt. Info unten in Statusbar
            val alert = Alert(Alert.AlertType.WARNING, "I went a bit over the edge there. There are no more results.")
            alert.title = "End of file"
            alert.dialogPane.minHeight = Region.USE_PREF_SIZE
            alert.showAndWait()
        }
    }

    private fun getSearchText(): String {
        return if (textInterpreterMode.get() as TextInterpreterMode == TextInterpreterMode.EXTENDED) {
            findProperty.get().replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
        } else {
            findProperty.get()
        }
    }
}