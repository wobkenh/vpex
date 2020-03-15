package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.controllers.*
import de.henningwobken.vpex.main.model.*
import de.henningwobken.vpex.main.xml.ProgressReader
import de.henningwobken.vpex.main.xml.TotalProgressInputStream
import de.henningwobken.vpex.main.xml.XmlFormattingService
import javafx.application.Platform
import javafx.beans.property.*
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.TextArea
import javafx.scene.control.TextInputDialog
import javafx.scene.input.Clipboard
import javafx.scene.input.KeyCode
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.FileChooser
import javafx.stage.StageStyle
import mu.KotlinLogging
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.CodeArea
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import tornadofx.*
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.NumberFormat
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class TabView : Fragment("File") {
    private val logger = KotlinLogging.logger {}
    private val internalResourceController: InternalResourceController by inject()
    private val settingsController: SettingsController by inject()
    private val stringUtils: StringUtils by inject()
    private val xmlFormattingService: XmlFormattingService by inject()
    private val searchAndReplaceController: SearchAndReplaceController by inject()
    private val vpexExecutor: VpexExecutor by inject()
    private val fileCalculationController: FileCalculationController by inject()
    private val fileWatcher: FileWatcher by inject()
    private val highlightingExcutor: HighlightingController by inject()

    val isDirty: BooleanProperty = SimpleBooleanProperty(false)
    val saveLockProperty = SimpleBooleanProperty(false)
    val fileProgressProperty = SimpleDoubleProperty(-1.0)
    val lineCount = SimpleIntegerProperty(0)
    val charCountProperty = SimpleIntegerProperty(0)
    val hasFile = SimpleBooleanProperty(false)
    val statusTextProperty = SimpleStringProperty("")
    private var file: File? = null
    private var numberFormat = NumberFormat.getInstance(settingsController.getSettings().locale)

    // Visible for Testing:
    lateinit var codeArea: CodeArea
    lateinit var findTextField: TextArea
    lateinit var replaceTextField: TextArea

    private var isAskingForFileReload = false

    // Search and Replace

    private val showReplaceProperty = SimpleBooleanProperty(false)
    private val showFindProperty = SimpleBooleanProperty(false)
    private val findProperty = SimpleStringProperty("")
    private val replaceProperty = SimpleStringProperty("")
    private var lastFindStart = 0
    private var lastFindEnd = 0
    private var lastFind = Find(0L, 0L)
    private val allFinds = mutableListOf<Find>()
    private val allFindsSize = SimpleIntegerProperty(-1)
    private val currentAllFindsIndex = SimpleIntegerProperty(-1).apply {
        onChange { currentAllFindsDisplayIndex.set(it + 1) }
    }
    private val currentAllFindsDisplayIndex = SimpleIntegerProperty(-1)
    private val hasFindProperty = SimpleBooleanProperty(false)
    private val searchDirection = SimpleObjectProperty<Any>()
    private val textInterpreterMode = SimpleObjectProperty<Any>()
    private val ignoreCaseProperty = SimpleBooleanProperty(false)
    private var showedEndOfFileDialog = false
    private var showedEndOfFileDialogCaretPosition = 0

    // Pagination
    val displayMode = SimpleObjectProperty(DisplayMode.PLAIN)
    val page = SimpleIntegerProperty(1)
    val maxPage = SimpleIntegerProperty(0)
    private var fullText: String = ""
    private var pageLineCounts = mutableListOf<Int>() // Line count of each page
    private var pageStartingLineCounts = mutableListOf<Int>() // For each page the number of lines before this page
    private var pageStartingByteIndexes = mutableListOf<Long>() // For each page the byte index that the page starts at
    private val pageTotalLineCount = SimpleIntegerProperty(0)
    private var ignoreTextChanges = false // If set to true, ignore changes in code area for line counts / dirty detection
    private var dirtySinceLastSync = false

    // Selection and cursor

    val selectionLength = SimpleIntegerProperty(0)
    val selectionLines = SimpleIntegerProperty(0)
    val cursorLine = SimpleIntegerProperty(0)
    val cursorColumn = SimpleIntegerProperty(0)


    // UI
    override val root = vbox {
        hbox(20) {
            this.removeWhen(showReplaceProperty.not().and(showFindProperty.not()))
            form {
                hbox(20) {
                    fieldset {
                        field("Find") {
                            textarea(findProperty) {
                                id = "findField"
                                findTextField = this
                                autogrowTextArea(this)
                                this.setOnKeyPressed {
                                    // TODO: Select next allfind if allFinds is filled?
                                    // Select the find if there was a find and the user did not change his position in the code area
                                    if (it.code == KeyCode.TAB && hasFindProperty.get() && lastFindStart == codeArea.anchor) {
                                        codeArea.requestFocus()
                                        codeArea.selectRange(codeArea.anchor, codeArea.anchor + findProperty.get().length)
                                        it.consume()
                                    } else if (it.code == KeyCode.ESCAPE) {
                                        closeSearchAndReplace()
                                    } else if (it.code == KeyCode.ENTER) {
                                        it.consume()
                                        if (it.isShiftDown) {
                                            if (it.isControlDown) {
                                                val text = findTextField.text
                                                val index = findTextField.caretPosition
                                                findTextField.text = text.substring(0, index) + System.lineSeparator()
                                                findTextField.positionCaret(index + 1)
                                            } else {
                                                findNext(reverse = true)
                                            }
                                        } else {
                                            findNext()
                                        }
                                    } else if (it.code == KeyCode.TAB) {
                                        it.consume()
                                        if (showReplaceProperty.get()) {
                                            replaceTextField.requestFocus()
                                        }
                                    }
                                }
                            }

                        }
                        field("Replace") {
                            removeWhen(showReplaceProperty.not())
                            textarea(replaceProperty) {
                                id = "replaceField"
                                replaceTextField = this
                                autogrowTextArea(this)
                                this.setOnKeyPressed {
                                    if (it.code == KeyCode.ENTER) {
                                        it.consume()
                                        if (it.isShiftDown) {
                                            val text = this.text
                                            val index = this.caretPosition
                                            this.text = text.substring(0, index) + System.lineSeparator()
                                            this.positionCaret(index + 1)
                                        } else {
                                            // TODO: What to do here? Find next? Replace this?
                                        }
                                    } else if (it.code == KeyCode.TAB) {
                                        it.consume()
                                        findTextField.requestFocus()
                                    }
                                }
                            }
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
                                    radiobutton("Normal", toggleGroup, SearchTextMode.NORMAL) {
                                        this.selectedProperty().set(true)
                                    }
                                    radiobutton("Extended", toggleGroup, SearchTextMode.EXTENDED)
                                    radiobutton("Regex", toggleGroup, SearchTextMode.REGEX)
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
                                    ViewHelper.fillHorizontal(this)
                                }.action {
                                    findNext()
                                }
                                button("Find all") {
                                    id = "findAll"
                                    enableWhen { vpexExecutor.isRunning.not() }
                                    ViewHelper.fillHorizontal(this)
                                }.action {
                                    onFindAllClicked()
                                }
                                button("List all") {
                                    enableWhen { vpexExecutor.isRunning.not() }
                                    ViewHelper.fillHorizontal(this)
                                }.action {
                                    onListAllClicked()
                                }
                            }
                            vbox(5) {
                                button("Replace this") {
                                    enableWhen { hasFindProperty.and(showReplaceProperty).and(vpexExecutor.isRunning.not()) }
                                    ViewHelper.fillHorizontal(this)
                                }.action {
                                    codeArea.replaceText(lastFindStart, lastFindEnd, replaceProperty.get())
                                }
                                button("Replace all") {
                                    id = "replaceAll"
                                    enableWhen { showReplaceProperty.and(vpexExecutor.isRunning.not()) }
                                    ViewHelper.fillHorizontal(this)
                                }.action {
                                    resetFinds()
                                    statusTextProperty.set("Replacing. (0 so far)")
                                    replaceAll(callback = {
                                        Platform.runLater {
                                            statusTextProperty.set("Replacing. (${allFinds.size} so far)")
                                        }
                                    }, endCallback = {
                                        Platform.runLater {
                                            statusTextProperty.set("")
                                        }
                                    })
                                }
                                button("Count") {
                                    enableWhen { vpexExecutor.isRunning.not() }
                                    ViewHelper.fillHorizontal(this)
                                }.action {
                                    statusTextProperty.set("Counting")
                                    allFindsSize.set(0)
                                    currentAllFindsIndex.set(-1) // Shows as 0 in gui (display index)
                                    searchAll({ _, _, _ ->
                                        Platform.runLater {
                                            allFindsSize.set(allFinds.size)
                                        }
                                    }, endCallback = {
                                        Platform.runLater {
                                            statusTextProperty.set("")
                                        }
                                    })
                                }
                            }
                        }
                    }
                    // AllFinds Controls
                    fieldset {
                        visibleWhen(allFindsSize.greaterThan(-1))
                        vbox(10) {
                            hbox(5) {
                                alignment = Pos.CENTER
                                textfield(currentAllFindsDisplayIndex) {
                                    prefWidth = 50.0
                                    maxWidth = 50.0
                                }.action {
                                    val enteredIndex = currentAllFindsDisplayIndex.get() - 1
                                    if (enteredIndex < 0 || enteredIndex >= allFinds.size) {
                                        currentAllFindsDisplayIndex.set(currentAllFindsIndex.get() + 1)
                                    } else {
                                        moveToFindIndex(enteredIndex)
                                    }
                                }
                                label("/")
                                label(allFindsSize.stringBinding { "${it!!} matches" })
                            }
                            hbox(10) {
                                alignment = Pos.CENTER
                                button("<") {
                                    disableWhen {
                                        currentAllFindsDisplayIndex.lessThanOrEqualTo(1)
                                    }
                                }.action {
                                    onLastAllFindClicked()
                                }
                                button(">") {
                                    disableWhen {
                                        currentAllFindsDisplayIndex.greaterThanOrEqualTo(allFindsSize)
                                    }
                                }.action {
                                    onNextAllFindClicked()
                                }
                            }
                        }
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

    init {
        settingsController.settingsProperty.addListener { _, oldSettings, newSettings ->
            updateSettings(oldSettings, newSettings)
        }
        updateSettings()
    }

    private fun autogrowTextArea(textArea: TextArea) {
        textArea.isWrapText = false
        textArea.maxWidth = 200.0
        textArea.prefRowCount = 1
        textArea.textProperty().onChange {
            if (it != null) {
                val lines = stringUtils.countLinesInString(it)
                textArea.prefRowCount = min(lines, 3)
            }
        }
    }

    private fun updateSettings(oldSettings: Settings? = null, newSettings: Settings? = null) {
        codeArea.wrapTextProperty().set(settingsController.getSettings().wrapText)
        numberFormat = NumberFormat.getInstance(settingsController.getSettings().locale)

        if (oldSettings != null && newSettings != null) {
            if (oldSettings.syntaxHighlightingColorScheme != newSettings.syntaxHighlightingColorScheme) {
                codeArea.stylesheets.clear()
                addStylesheetsToCodearea()
            }
            if (oldSettings.syntaxHighlighting != newSettings.syntaxHighlighting) {
                highlightEverything(this.getPageIndex())
            }
        }

        checkForDisplayModeChange()
    }


    /* ==============================
    =
    =   Methods available from Main View
    =   Methods called "on<sth>Clicked" are actions issued from UI Controls. They are public so
    =       a) the UI can be controlled in tests
    =       b) we can do programmatically what the user can do manually (e.g. for shortcuts)
    =
     ================================ */

    fun onFindAllClicked() {
        statusTextProperty.set("Searching")
        resetFinds()
        var hasSelectedFind = false
        searchAll({ finds, _, _ ->
            Platform.runLater {
                if (!hasSelectedFind && finds.isNotEmpty()) {
                    hasSelectedFind = true
                    moveToFindIndex(0) {
                        highlightingExcutor.allFinds(codeArea, displayMode.get(), getPageIndex(), allFinds)
                    }
                }
                allFindsSize.set(allFinds.size)
            }
        }, endCallback = {
            Platform.runLater {
                statusTextProperty.set("")
                // TODO: Do we need that? Highlighting should be done by above callback
                // Only need to change find highlighting, not everything
                // highlightingExcutor.highlightEverything(codeArea, allFinds, lastFind, displayMode.get(), getPageIndex(), showFindProperty.get())
            }
        })
    }

    fun onListAllClicked() {
        // TODO: open popup with code snippets of matches
    }

    fun onNextAllFindClicked() {
        moveToFindIndex(currentAllFindsIndex.get() + 1)
    }

    fun onLastAllFindClicked() {
        moveToFindIndex(currentAllFindsIndex.get() - 1)
    }

    fun focusCodeArea() {
        codeArea.requestFocus()
    }

    /**
     * Asks the user for input to go to a specific position in the file
     */
    fun moveTo() {
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
            moveTo(userLine.toLong(), userColumn.toLong())
        }
    }

    /**
     * Opens the search panel and sets the search term if text is currently selected.
     */
    fun openSearch() {
        // TODO: Redo find highlighting
        showReplaceProperty.set(false)
        showFindProperty.set(true)
        if (!codeArea.selectedText.isNullOrBlank()) {
            findTextField.text = codeArea.selectedText
        }
        findTextField.requestFocus()
        findTextField.selectAll()
    }

    /**
     * Opens the search panel and activates the replace property.
     * If text is selected, it will be copied into the search text field.
     */
    fun openReplace() {
        showReplaceProperty.set(true)
        showFindProperty.set(false)
        if (!codeArea.selectedText.isNullOrBlank()) {
            findTextField.text = codeArea.selectedText
        }
        findTextField.requestFocus()
        findTextField.selectAll()
    }

    /**
     * Pretty prints the current file using default indentation of 4.
     * Syntax errors are ignored. Invalid XML at the beginning and end will be deleted.
     */
    fun forcePrettyPrint() {
        statusTextProperty.set("Making this xml pretty like a princess")
        formatPrint { xmlInput, xmlOutput ->
            xmlFormattingService.format(xmlInput, xmlOutput, withNewLines = true, indentSize = 4)
        }
    }

    /**
     * Ugly prints the current file. All unnecessary text between tags will be deleted.
     * Ignores syntax. Invalid XML at the beginning and end will be deleted.
     */
    fun uglyPrint() {
        statusTextProperty.set("Making this xml ugly like a Fiat Multipla")
        formatPrint { xmlInput, xmlOutput ->
            xmlFormattingService.format(xmlInput, xmlOutput, withNewLines = false, indentSize = 0)
        }
    }

    /**
     * Pretty prints the file using a xml parser. Invalid XML will cause an error to be thrown.
     */
    fun prettyPrint() {
        statusTextProperty.set("Making this xml pretty like a princess")

        // Setting the indent on the transformerFactory does not work anymore, see:
        // https://stackoverflow.com/questions/15134861/java-lang-illegalargumentexception-not-supported-indent-number
        // transformerFactory.setAttribute("indent-number", settingsController.getSettings().prettyPrintIndent)
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        // Otherwise the root tag will be written into the first line
        // see https://stackoverflow.com/questions/18249490/since-moving-to-java-1-7-xml-document-element-does-not-indent
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", settingsController.getSettings().prettyPrintIndent.toString())

        if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
            // Overwriting the existing file would probably be a bad idea
            // So let the user chose a new one
            val outputFile = choseFile()
            if (outputFile != null && (outputFile.isFile || !outputFile.exists())) {
                Thread {
                    val oldFile = file
                    if (oldFile != null) {
                        // TOOD: Start Watching new File too? + Ignore new File?
                        fileWatcher.stopWatching(oldFile)
                    }
                    val inputFile = getFileNotNull()
                    val inputFileLength = inputFile.length()
                    val xmlOutput = StreamResult(FileWriter(outputFile))
                    val xmlInput = StreamSource(InputStreamReader(TotalProgressInputStream(inputFile.inputStream()) {
                        Platform.runLater {
                            fileProgressProperty.set(it / inputFileLength.toDouble())
                        }
                    }))
                    try {
                        transformer.transform(xmlInput, xmlOutput)
                    } catch (e: Exception) {
                        reopenFile(outputFile)
                        throw e
                    }
                    reopenFile(outputFile)
                }.start()
            } else {
                logger.info { "User did not chose a valid file - aborting" }
                statusTextProperty.set("")
            }
        } else {
            Thread {
                val text = getFullText()
                val textLength = text.length
                val stringWriter = StringWriter()
                val xmlOutput = StreamResult(stringWriter)
                val xmlInput = StreamSource(InputStreamReader(TotalProgressInputStream(text.byteInputStream()) {
                    Platform.runLater {
                        fileProgressProperty.set(it / textLength.toDouble())
                    }
                }))
                try {
                    transformer.transform(xmlInput, xmlOutput)
                } catch (e: Exception) {
                    Platform.runLater {
                        fileProgressProperty.set(-1.0)
                        statusTextProperty.set("")
                    }
                    throw e
                }
                Platform.runLater {
                    fileProgressProperty.set(-1.0)
                    statusTextProperty.set("")
                    if (displayMode.get() == DisplayMode.PAGINATION) {
                        changeFullText(xmlOutput.writer.toString())
                    } else {
                        codeArea.replaceText(xmlOutput.writer.toString())
                    }
                }
            }.start()
        }
    }

    /**
     * Validate the XML Schema of the current file
     * Opens a new window to display results
     */
    fun validateSchema() {
        val resultFragment = find<SchemaResultFragment>()
        resultFragment.gotoLineColumn = { line, column, _ -> moveTo(line, column) }
        val stage = resultFragment.openWindow(stageStyle = StageStyle.UTILITY)
        stage?.requestFocus()

        val (inputStream: InputStream, length: Long) = if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
            val file = getFileNotNull()
            Pair(file.inputStream(), file.length())
        } else {
            val text = getFullText()
            Pair(text.byteInputStream(), text.length.toLong())
        }
        resultFragment.validateSchema(inputStream, length)
    }

    /**
     * Validate the XML Syntax of the current file.
     * Throws an exception on the first error.
     * If syntax is valid, an alert is displayed.
     */
    // TODO: Display in separate window (see validateSchema)
    fun validateSyntax() {
        statusTextProperty.set("Validating Syntax")
        vpexExecutor.execute {
            logger.info("Validating Syntax...")
            val saxParserFactory = SAXParserFactory.newInstance()
            saxParserFactory.isNamespaceAware = true
            val saxParser = saxParserFactory.newSAXParser()
            val xmlReader = saxParser.xmlReader
            try {
                if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
                    val file = getFileNotNull()
                    val fileLength = file.length()
                    xmlReader.parse(InputSource(TotalProgressInputStream(file.inputStream()) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Cancelled")
                        }
                        Platform.runLater {
                            fileProgressProperty.set(it / fileLength.toDouble())
                        }
                    }))
                } else {
                    val text = getFullText()
                    val textLength = text.length
                    xmlReader.parse(InputSource(TotalProgressInputStream(text.byteInputStream()) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Cancelled")
                        }
                        Platform.runLater {
                            fileProgressProperty.set(it / textLength.toDouble())
                        }
                    }))
                }
            } catch (e: SAXException) {
                if (e.exception is InterruptedException) {
                    logger.info { "Cancelling Syntax Validation" }
                    Platform.runLater {
                        fileProgressProperty.set(-1.0)
                        statusTextProperty.set("")
                    }
                    return@execute
                } else {
                    throw e
                }
            }
            Platform.runLater {
                fileProgressProperty.set(-1.0)
                statusTextProperty.set("")
                alert(Alert.AlertType.INFORMATION, "The council has decided", "The syntax of this xml file is valid.")
            }
        }
    }

    /**
     * Moves to said page. May be executed asynchronously.
     * Use callback to reliably execute code after the page move
     * @param page page to move to
     */
    fun moveToPage(page: Int, callback: (() -> Unit)? = null) {
        moveToPage(page, SyncDirection.TO_FULLTEXT, callback)
    }

    fun syncPage() {
        if (this.displayMode.get() == DisplayMode.PAGINATION && dirtySinceLastSync) {
            val pageSize = settingsController.getSettings().pageSize
            this.fullText = this.fullText.replaceRange((this.getPageIndex()) * pageSize, min(this.page.get() * pageSize, this.fullText.length), this.codeArea.text)
            Platform.runLater {
                calcLinesAllPages()
            }
            dirtySinceLastSync = false
        }
    }

    /**
     * Move to a line and column in the current file
     */
    fun moveTo(userLine: Long, userColumn: Long) {
        if (displayMode.get() == DisplayMode.PLAIN) {
            val line = max(min(userLine.toInt(), codeArea.paragraphs.size), 1) - 1
            val column = min(userColumn.toInt(), codeArea.getParagraph(line).length())
            moveToLineColumn(line, column)
        } else {
            // Might change starting line counts if there are unsaved changes
            syncPage()
            var page = 1
            for (i in this.maxPage.get() downTo 1) {
                if (userLine > pageStartingLineCounts[i - 1]) {
                    page = i
                    break
                }
            }
            val afterPageSwitch = {
                val line = max(min((userLine - pageStartingLineCounts[page - 1]).toInt(), codeArea.paragraphs.size), 1) - 1
                // TODO: Should be long
                val column = min(userColumn.toInt(), codeArea.getParagraph(line).length())
                moveToLineColumn(line, column)
            }
            if (page != this.page.get()) {
                moveToPage(page) {
                    afterPageSwitch()
                }
            } else {
                afterPageSwitch()
            }
        }
    }

    /**
     * The currently opened file or null if the text is in memory only
     */
    fun getFile(): File? {
        return file
    }

    /**
     * Cleanup work before this tab can be removed
     */
    fun closeTab() {
        logger.debug { "Closing Tab @ TabView" }
        val oldFile = file
        if (oldFile != null) {
            fileWatcher.stopWatching(oldFile)
        }
        logger.debug { "Closed Tab @ TabView" }
    }

    /**
     * Check if tab can be closed and if there are unsaved changes, ask the user if he wants to close the tab.
     */
    fun requestCloseTab(callback: (() -> Unit)) {
        if (this.isDirty.get()) {
            alertUnsavedChanges(callback)
        } else {
            callback()
        }
    }

    /**
     * Save the current text to the current file
     * or a new file if the text is not associated with a file
     */
    fun saveFile() {
        logger.info("Saving")
        val file = this.file
        if (file != null && !saveLockProperty.get() && displayMode.get() != DisplayMode.DISK_PAGINATION) {
            val text = getFullText()
            fileWatcher.startIgnoring(file)
            Files.write(file.toPath(), text.toByteArray())
            fileWatcher.stopIgnoring(file)
            isDirty.set(false)
            logger.info("Saved")
        } else {
            saveFileAs()
        }
    }

    /**
     * Save the current text to a new file
     */
    fun saveFileAs(onSaveCallback: (() -> Unit)? = null, onCancelCallback: (() -> Unit)? = null) {
        logger.info("Saving as")
        val fileChooser = FileChooser()
        fileChooser.title = "Save as"
        fileChooser.initialDirectory = File(settingsController.getOpenerBasePath()).absoluteFile
        val file = fileChooser.showSaveDialog(FX.primaryStage)
        if (file != null) {
            settingsController.setOpenerBasePath(file.parentFile.absolutePath)
            val oldFile = this.file
            if (oldFile != null) {
                fileWatcher.stopWatching(oldFile)
            }
            if (displayMode.get() != DisplayMode.DISK_PAGINATION) {
                val text = getFullText()
                Files.write(file.toPath(), text.toByteArray())
                this.file = file
                this.hasFile.set(true)
                setFileTitle(file)
                isDirty.set(false)
                startFileWatcher(file)
                logger.info("Saved as")
            } else {
                statusTextProperty.set("Saving")
                fileProgressProperty.set(0.0)
                // TODO: Vpex Executor
                Thread {
                    requireNotNull(oldFile) // Can't have disk pagination without file
                    val oldFileLength = oldFile.length() * 1.0
                    // We need a tmp file because the user might want to overwrite the file. (cant read from a file were writing to)
                    val tmpFile = File.createTempFile("vpex-", ".tmp")
                    val outputStream = FileOutputStream(tmpFile)

                    val inputStream = TotalProgressInputStream(oldFile.inputStream()) {
                        Platform.runLater {
                            fileProgressProperty.set(it / oldFileLength)
                        }
                    }

                    val currentPage = page.get()
                    val pageSize = settingsController.getSettings().pageSize
                    val buffer = ByteArray(pageSize)
                    var page = 1
                    var read = 0
                    while (read >= 0) {
                        if (page == currentPage) {
                            outputStream.write(codeArea.text.toByteArray())
                            read = inputStream.skip(pageSize.toLong()).toInt()
                        } else {
                            read = inputStream.read(buffer)
                            if (read >= 0) {
                                outputStream.write(buffer, 0, read)
                            }
                        }
                        page++
                    }

                    Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    startFileWatcher(file)

                    this.file = file
                    this.hasFile.set(true)
                    logger.info("Saved as")
                    Platform.runLater {
                        statusTextProperty.set("")
                        fileProgressProperty.set(-1.0)
                        setFileTitle(file)
                        isDirty.set(false)
                        onSaveCallback?.let { onSaveCallback() }
                    }
                }.start()
            }
        } else {
            if (onCancelCallback != null) {
                onCancelCallback()
            }
        }
    }

    fun openFile(file: File) {
        if (this.file != null) {
            logger.info("Closing currently open file")
            closeFile()
        }
        logger.info("Opening file ${file.absolutePath}")
        this.file = file
        this.hasFile.set(true)
        hasFile.set(true)
        setFileTitle(file)
        codeArea.replaceText("")
        fullText = ""
        startFileWatcher(file)
        val settings = settingsController.getSettings()
        if (settings.diskPagination && file.length() > settings.diskPaginationThreshold * 1024 * 1024) {
            logger.info { "Opening file in disk pagination mode" }
            displayMode.set(DisplayMode.DISK_PAGINATION)
            dirtySinceLastSync = true
            lineCount.bind(pageTotalLineCount)
            moveToPage(1, SyncDirection.TO_CODEAREA) {
                this.codeArea.moveTo(0, 0)
                codeArea.undoManager.forgetHistory()
            }
        } else {
            val fullText = file.readText()
            if (this.settingsController.getSettings().pagination
                    && fullText.length > settingsController.getSettings().paginationThreshold) {
                this.fullText = fullText
                logger.info { "Opening file in pagination mode" }
                displayMode.set(DisplayMode.PAGINATION)
                dirtySinceLastSync = true
                lineCount.bind(pageTotalLineCount)
                moveToPage(1, SyncDirection.TO_CODEAREA) {
                    codeArea.undoManager.forgetHistory()
                }
            } else {
                logger.info { "Opening file in plain mode" }
                displayMode.set(DisplayMode.PLAIN)
                lineCount.bind(codeArea.paragraphs.sizeProperty)
                replaceText(fullText)
                codeArea.undoManager.forgetHistory()
                // normally, moveToPage would repaint highlighting. here, we have to do that manually
                highlightEverything(0)
            }
        }
        this.codeArea.moveTo(0, 0)
        this.isDirty.set(false)
    }

    /* ==============================
    =
    =   Private Methods
    =
     ================================ */

    private fun closeFile() {
        val oldFile = file
        if (oldFile != null) {
            fileWatcher.stopWatching(oldFile)
        }
        file = null
        hasFile.set(false)
        replaceText("")
        fullText = ""
        lineCount.bind(codeArea.paragraphs.sizeProperty())
        displayMode.set(DisplayMode.PLAIN)
        isDirty.set(false)
        statusTextProperty.set("")
        fileProgressProperty.set(-1.0)
        resetFinds()
        showedEndOfFileDialog = false
    }

    private fun resetFinds() {
        allFinds.clear()
        allFindsSize.set(-1)
        lastFind = Find(0L, 0L)
        lastFindStart = 0
        lastFindEnd = 0
        highlightingExcutor.removeFinds(codeArea)
        currentAllFindsIndex.set(-1)
    }

    private fun closeSearchAndReplace() {
        showReplaceProperty.set(false)
        showFindProperty.set(false)
        this.highlightingExcutor.removeFinds(codeArea)
    }

    private fun replaceText(text: String) {
        this.ignoreTextChanges = true
        this.codeArea.replaceText(text)
        this.ignoreTextChanges = false
    }

    private fun checkForDisplayModeChange() {
        logger.info("Checking for Display Mode Change")
        // When a new file has been opened or the settings have changed,
        // the display mode might have to be changed

        val file = this.file
        val settings = this.settingsController.getSettings()
        if (settings.diskPagination && file != null && file.length() > settings.diskPaginationThreshold * 1024 * 1024) {
            // Should be disk pagination
            if (displayMode.get() == DisplayMode.PLAIN || displayMode.get() == DisplayMode.PAGINATION) {
                logger.info("Switching to Disk Pagination")
                displayMode.set(DisplayMode.DISK_PAGINATION)
                codeArea.replaceText("")
                fullText = ""
                moveToPage(1)
            } else {
                logger.info("Already in correct mode (Disk Pagination)")
            }
        } else if (settings.pagination && max(this.fullText.length, this.codeArea.text.length) > settings.paginationThreshold) {
            // Should be pagination
            if (displayMode.get() == DisplayMode.PLAIN) {
                displayMode.set(DisplayMode.PAGINATION)
                // Text might still be only in codeArea
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
            } else if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
                displayMode.set(DisplayMode.PAGINATION)
                if (file != null) {
                    openFile(file)
                }
            } else {
                logger.info("Already in correct mode (Pagination)")
            }

        } else {
            // Should be plain
            if (displayMode.get() == DisplayMode.PAGINATION) {
                logger.info("Switching to Plain From Pagination")
                displayMode.set(DisplayMode.PLAIN)
                disablePagination()
            } else if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
                logger.info("Switching to Plain From Disk Pagination")
                displayMode.set(DisplayMode.PLAIN)
                if (file != null) {
                    openFile(file)
                }
            } else {
                logger.info("Already in correct mode (Plain)")
            }
        }
    }

    private fun disablePagination() {
        val wasDirty = this.isDirty.get()
        this.displayMode.set(DisplayMode.PLAIN)
        if (this.codeArea.text.length < this.fullText.length) {
            replaceText(this.fullText)
        }
        this.fullText = ""
        this.lineCount.bind(this.codeArea.paragraphs.sizeProperty)
        this.isDirty.set(wasDirty)
    }

    /**
     * Asynchronously rebuilds the complete page starting line counts
     *
     * IMPORTANT: The max page property need to be set BEFORE this method is called!
     */
    private fun calcLinesAllPages() {
        statusTextProperty.set("Calculating Line Numbers")
        vpexExecutor.execute {
            this.pageLineCounts.clear()
            this.pageStartingLineCounts.clear()
            this.pageStartingByteIndexes.clear()
            val pageSize = this.settingsController.getSettings().pageSize
            if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
                val file = getFileNotNull()
                try {
                    val result = fileCalculationController.calcStartingByteIndexesAndLineCounts(file, pageSize) { progress ->
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Cancelled")
                        }
                        Platform.runLater {
                            fileProgressProperty.set(progress)
                        }
                    }
                    pageLineCounts.addAll(result.pageLineCounts)
                    pageStartingByteIndexes.addAll(result.pageStartingByteIndexes)
                } catch (interruptedException: InterruptedException) {
                    logger.warn("Line Number Calculation cancelled!")
                }
            } else {
                val maxPage = ceil(this.fullText.length / this.settingsController.getSettings().pageSize.toDouble()).toInt()
                for (page in 1..maxPage) {
                    val pageIndex = page - 1
                    pageLineCounts.add(stringUtils.countLinesInString(this.fullText.substring(pageIndex * pageSize, min(this.fullText.length, page * pageSize))))
                }
            }
            val maxPage = pageLineCounts.size
            pageStartingLineCounts.addAll(fileCalculationController.calculateStartingLineCounts(pageLineCounts))
            Platform.runLater {
                this.maxPage.set(maxPage)
                fileProgressProperty.set(-1.0)
                statusTextProperty.set("")
                if (pageLineCounts.isNotEmpty()) {
                    this.pageTotalLineCount.set(pageStartingLineCounts.last() + pageLineCounts.last())
                }
            }
            logger.info("Set max page to $maxPage")
            logger.info("Set max lines to ${pageTotalLineCount.get()}")
        }
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
    private fun moveToPage(page: Int, syncDirection: SyncDirection, callback: (() -> Unit)? = null) {
        logger.info("Moving to page $page with sync direction $syncDirection")
        val pageIndex = page - 1
        val pageSize = settingsController.getSettings().pageSize
        showedEndOfFileDialog = false

        // How many characters the new page ist before or after the current page
        val characterDeltaOffset = page - this.page.get()
        lastFindStart += characterDeltaOffset
        lastFindEnd += characterDeltaOffset

        if (displayMode.get() == DisplayMode.DISK_PAGINATION) {

            var askingIfUserWantsToSave = false
            var wantsToCancel = false
            var saveDone = false

            // TO_CODEAREA signals that the fulltext (i.e. the file) has changed
            // Therefore, we only need to save in TO_FULLTEXT direction (e.g. when the user changes sth that is not saved yet)
            if (dirtySinceLastSync && syncDirection == SyncDirection.TO_FULLTEXT) {
                logger.warn { "Changes will be lost." }
                askingIfUserWantsToSave = true
                alertUnsavedChanges(proceedCallback = {
                    saveDone = true
                }, cancelCallback = {
                    saveDone = true
                    wantsToCancel = true
                })
            }

            val mainView = this
            runAsync {
                if (askingIfUserWantsToSave) {
                    while (!saveDone) {
                        Thread.sleep(40)
                    }
                }

                if (wantsToCancel) {
                    // do not call callback as page will not be switched
                    return@runAsync
                }

                // At this point we can assume that the file content is up to date

                val file = getFileNotNull()
                val pageStartingByteIndexes = mainView.pageStartingByteIndexes
                val text = if (pageStartingByteIndexes.isEmpty()) {
                    // user is opening the file. Just load the first page.
                    val reader = file.reader()
                    val buffer = CharArray(pageSize)
                    val read = reader.read(buffer)
                    reader.close()
                    String(buffer, 0, read)
                } else {
                    // load specific page
                    val startByteIndex = mainView.pageStartingByteIndexes[pageIndex]
                    val endByteIndex = if (page >= mainView.pageStartingByteIndexes.size) {
                        file.length()
                    } else {
                        mainView.pageStartingByteIndexes[page]
                    }
                    val pageByteSize = (endByteIndex - startByteIndex).toInt()
                    val destinationBuffer = ByteArray(pageByteSize)
                    val randomAccessFile = RandomAccessFile(file, "r")
                    randomAccessFile.seek(startByteIndex)
                    val read = randomAccessFile.read(destinationBuffer, 0, pageByteSize)
                    randomAccessFile.close()
                    String(destinationBuffer, 0, read)
                }
                Platform.runLater {
                    if (mainView.dirtySinceLastSync) {
                        // TODO: In TO_FULLTEXT mode we only have to recalculate line breaks of all pages starting with the current page
                        // All pages before that are unaffected
                        mainView.calcLinesAllPages()
                    }
                    mainView.dirtySinceLastSync = false
                    mainView.isDirty.set(false) // Changes were either saved or discarded
                    replaceText(text)
                    mainView.page.set(page)
                    highlightEverything(pageIndex)
                    if (callback != null) {
                        callback()
                    }
                }
            }

        } else {
            if (dirtySinceLastSync) {
                if (syncDirection == SyncDirection.TO_FULLTEXT) {
                    logger.info("Syncing CodeArea text to full text")
                    this.fullText = this.fullText.replaceRange((this.getPageIndex()) * pageSize, min(this.page.get() * pageSize, this.fullText.length), this.codeArea.text)
                } else {
                    logger.info("Syncing full text to CodeArea")
                }
                this.calcLinesAllPages()
                this.dirtySinceLastSync = false
            }
            this.page.set(page)
            replaceText(this.fullText.substring((page - 1) * pageSize, min(page * pageSize, this.fullText.length)))
            highlightEverything(page - 1)
            if (callback != null) {
                callback()
            }
        }
    }

    private fun getFileNotNull(): File {
        val file = this.file
        if (file == null) {
            logger.error { "File not set" }
        }
        return file!!
    }

    private fun moveToIndex(index: Long, callback: (() -> Unit)? = null) {
        when (displayMode.get()) {
            DisplayMode.PLAIN -> {
                moveToLineColumn(0, index.toInt()) {
                    if (callback != null) {
                        callback()
                    }
                }
            }
            else -> {
                val page = getPageOfIndex(index)
                val afterPageMove = {
                    val inPageIndex: Int = (index - ((page - 1) * this.settingsController.getSettings().pageSize)).toInt()
                    this.moveToLineColumn(0, inPageIndex) {
                        if (callback != null) {
                            callback()
                        }
                    }
                }
                if (page != this.page.get()) {
                    moveToPage(page) {
                        afterPageMove()
                    }
                } else {
                    afterPageMove()
                }
            }
        }
    }

    private fun moveToFindIndex(allFindsIndex: Int, callback: (() -> Unit)? = null) {
        val find = allFinds[allFindsIndex]
        moveToFind(find) {
            currentAllFindsIndex.set(allFindsIndex)
            if (callback != null) {
                callback()
            }
        }
    }

    private fun moveToFind(find: Find, callback: (() -> Unit)? = null) {
        moveToIndex(find.start) {
            Platform.runLater {
                val findStart = codeArea.anchor
                val findLength = find.end - find.start
                val findEnd = codeArea.anchor + findLength.toInt()
                highlightingExcutor.nextFind(codeArea, lastFindStart, lastFindEnd, findStart, findEnd)
                lastFindStart = findStart
                lastFindEnd = findEnd
                lastFind = find
                this.hasFindProperty.set(true)
                if (callback != null) {
                    callback()
                }
            }
        }
    }

    private fun getPageOfIndex(index: Long): Int {
        if (index == 0L) {
            return 1
        }
        return ceil(index / this.settingsController.getSettings().pageSize.toDouble()).toInt()
    }

    /**
     * Moves the cursor to the specified line/column
     */
    private fun moveToLineColumn(line: Int, column: Int, callback: (() -> Unit)? = null) {
        Platform.runLater {
            codeArea.moveTo(codeArea.position(line, column).toOffset())
            codeArea.requestFollowCaret()
            logger.info("Moved to $line:$column")
            if (callback != null) {
                callback()
            }
        }
    }

    private fun formatPrint(format: (xmlInput: ProgressReader, xmlOutput: Writer) -> Unit) {
        if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
            // Overwriting the existing file would probably be a bad idea
            // So let the user chose a new one
            // TODO: FileWatcher: Stop old File / Start + Ignore + Unignore new File
            val outputFile = choseFile()
            if (outputFile != null && (outputFile.isFile || !outputFile.exists())) {
                Thread {
                    val xmlOutput = FileWriter(outputFile)
                    val inputFile = getFileNotNull()
                    val inputFileLength = inputFile.length().toDouble()
                    val xmlInput = ProgressReader(FileReader(inputFile)) {
                        Platform.runLater {
                            fileProgressProperty.set(it / inputFileLength)
                        }
                    }
                    format(xmlInput, xmlOutput)
                    Platform.runLater {
                        fileProgressProperty.set(-1.0)
                        statusTextProperty.set("")
                        openFile(outputFile)
                    }
                }.start()
            } else {
                logger.info { "User did not chose a valid file - aborting" }
                statusTextProperty.set("")
            }
        } else {
            Thread {
                val text = getFullText()
                val textLength = text.length.toDouble()
                val xmlOutput = StringWriter()
                val xmlInput = ProgressReader(StringReader(text)) {
                    Platform.runLater {
                        fileProgressProperty.set(it / textLength)
                    }
                }
                format(xmlInput, xmlOutput)
                Platform.runLater {
                    fileProgressProperty.set(-1.0)
                    statusTextProperty.set("")
                    if (displayMode.get() == DisplayMode.PAGINATION) {
                        changeFullText(xmlOutput.toString())
                    } else {
                        codeArea.replaceText(xmlOutput.toString())
                    }
                }
            }.start()
        }
    }

    /**
     * (Re)opens the specified file after an operation
     * @param file
     */
    private fun reopenFile(file: File) {
        fileWatcher.stopIgnoring(file)
        Platform.runLater {
            fileProgressProperty.set(-1.0)
            statusTextProperty.set("")
            openFile(file)
        }
    }

    private fun choseFile(): File? {
        val fileChooser = FileChooser()
        fileChooser.title = "Chose destination"
        fileChooser.initialDirectory = File(settingsController.getOpenerBasePath()).absoluteFile
        val file = fileChooser.showSaveDialog(FX.primaryStage)
        if (file != null) {
            settingsController.setOpenerBasePath(file.parentFile.absolutePath)
        }
        return file
    }

    private fun changeFullText(fullText: String) {
        this.dirtySinceLastSync = true
        this.isDirty.set(true)
        this.fullText = fullText
        this.moveToPage(1, SyncDirection.TO_CODEAREA)
    }

    private fun startFileWatcher(file: File) {
        fileWatcher.startWatching(file) {
            Platform.runLater {
                if (!isAskingForFileReload) {
                    isAskingForFileReload = true
                    confirmation("Update", "The file was updated externally. Reload?", ButtonType.OK, ButtonType.CANCEL, actionFn = {
                        isAskingForFileReload = false
                        if (it.buttonData.isDefaultButton) {
                            openFile(file)
                        }
                    })
                }
            }
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
        val scrollPane = VirtualizedScrollPane(codeArea)
        scrollPane.vgrow = Priority.ALWAYS
        return scrollPane
    }

    private fun getRichTextArea(): CodeArea {
        logger.info("Creating text area")
        val codeArea = CodeArea()
        codeArea.id = "codeArea"
        logger.info("Setting wrap text to " + settingsController.getSettings().wrapText)
        codeArea.wrapTextProperty().set(settingsController.getSettings().wrapText)
        codeArea.selectionProperty().onChange {
            selectionLength.set(it?.length ?: 0)
            if (it != null && it.length > 0) {
                logger.debug { "Selection changed: $it" }
                val substring = this.getFullText().substring(it.start, it.end)
                selectionLines.set(stringUtils.countLinesInString(substring))
            } else {
                selectionLines.set(0)
            }
        }
        codeArea.caretPositionProperty().onChange {
            var line = codeArea.currentParagraph
            val column = codeArea.caretSelectionBind.columnPosition
            if (displayMode.get() != DisplayMode.PLAIN && pageStartingLineCounts.isNotEmpty()) {
                line += pageStartingLineCounts[getPageIndex()]
            }
            cursorLine.set(line + 1)
            cursorColumn.set(column)
        }
        codeArea.plainTextChanges().subscribe { textChange ->

            logger.debug { "Text change at ${textChange.position} +${textChange.inserted.length} / -${textChange.removed.length}" }

            this.charCountProperty.set(
                    when {
                        displayMode.get() == DisplayMode.PAGINATION -> this.fullText.length - this.settingsController.getSettings().pageSize + this.codeArea.text.length
                        displayMode.get() == DisplayMode.DISK_PAGINATION -> {
                            // TODO: Handle Disk pagination
                            0
                        }
                        else -> this.codeArea.text.length
                    }
            )
            if (!this.ignoreTextChanges) {
                // Only dirty if user changed something
                this.isDirty.set(true)
                this.dirtySinceLastSync = true
                if (displayMode.get() == DisplayMode.PAGINATION) {
                    val insertedLines = this.stringUtils.countLinesInString(textChange.inserted) - 1
                    val removedLines = this.stringUtils.countLinesInString(textChange.removed) - 1
                    this.pageTotalLineCount.set(this.pageTotalLineCount.get() + insertedLines - removedLines)
                }

                // TODO: Move the following code to a controller
                // find next => remove highlighting if in removed and change bounds if after removed
                var findInvalidated = false
                val removeLength = textChange.removed.length
                val removeStart = textChange.position
                val removeEnd = removeStart + removeLength
                val lastFindWasInAllFinds = allFinds.find { it == lastFind } != null

                if (this.hasFindProperty.get()) {
                    if (!lastFindWasInAllFinds) {
                        if (lastFindStart <= removeEnd && lastFindEnd >= removeStart) {
                            // was removed ==> invalidate
                            removeLastFind()
                            findInvalidated = true
                        } else if (lastFindStart >= removeEnd) {
                            lastFindStart += textChange.netLength
                            lastFindEnd += textChange.netLength
                            lastFind = Find(lastFind.start + textChange.netLength, lastFind.end + textChange.netLength)
                        }
                    }
                }

                // TODO: Check if find was revalidated or new find was created through change

                // all finds => remove highlighting if in removed and change bounds if after removed
                val offset = when (displayMode.get()) {
                    DisplayMode.PLAIN -> 0
                    else -> this.getPageIndex() * this.settingsController.getSettings().pageSize
                }
                val indicesToRemove = mutableListOf<Int>()
                for (index in allFinds.indices) {
                    val find = allFinds[index]
                    if (find.start - offset <= removeEnd && find.end - offset >= removeStart) {
                        // remove later to not invalidate the index range we are looping over
                        indicesToRemove.add(index)
                        if (find == lastFind) {
                            removeLastFind()
                            findInvalidated = true
                        }
                    } else if (find.start >= removeEnd) {
                        allFinds[index] = Find(find.start + textChange.netLength, find.end + textChange.netLength)
                        if (find == lastFind) {
                            lastFindStart += textChange.netLength
                            lastFindEnd += textChange.netLength
                            lastFind = Find(lastFind.start + textChange.netLength, lastFind.end + textChange.netLength)
                        }
                    }
                }
                for (index in indicesToRemove.reversed()) {
                    allFinds.removeAt(index)
                }

                if (allFindsSize.get() > -1) {
                    allFindsSize.set(allFinds.size)
                    // TODO: Recalculate find index?
                    if (findInvalidated) {
                        currentAllFindsIndex.set(-1) // Shows as 0 in gui (display index)
                    }
                }

                // TODO: If a find was removed, we need to "repaint" at least the area in which the find used to be
                // so pass min/max bounds to textChangedTask
                highlightingExcutor.textChanged(codeArea, textChange)
            }
        }
        this.codeArea = codeArea
        this.codeArea.setOnKeyPressed {
            if (it.code == KeyCode.ESCAPE) {
                closeSearchAndReplace()
            }
        }
        addStylesheetsToCodearea()
        // Original: LineNumberFactory.get(codeArea)
        codeArea.paragraphGraphicFactory = PaginatedLineNumberFactory(codeArea) {
            // Needs to return the starting line count of the current page
            if (displayMode.get() != DisplayMode.PLAIN) {
                val pageIndex = this.getPageIndex()
                // Starting line count might not have been calculated yet
                if (pageStartingLineCounts.size > pageIndex) {
                    pageStartingLineCounts[pageIndex]
                } else 0
            } else 0
        }
        codeArea.contextMenu = contextmenu {
            val hasClipboardContent = SimpleBooleanProperty(false)
            item("Cut") {
                disableWhen { selectionLength.eq(0) }
            }.action {
                Clipboard.getSystemClipboard().putString(codeArea.selectedText)
                val text = codeArea.text
                replaceText(text.substring(0, codeArea.selection.start) + text.substring(codeArea.selection.end))
            }
            item("Copy") {
                disableWhen { selectionLength.eq(0) }
            }.action {
                Clipboard.getSystemClipboard().putString(codeArea.selectedText)
            }
            item("Paste") { disableWhen { hasClipboardContent.not() } }.action {
                val clipboard = Clipboard.getSystemClipboard()
                val additionalText = when {
                    clipboard.hasString() -> clipboard.string
                    clipboard.hasFiles() -> {
                        var text = ""
                        for (file in clipboard.files) {
                            if (text.isNotEmpty()) {
                                text += System.lineSeparator()
                            }
                            text += file.absolutePath
                        }
                        text
                    }
                    clipboard.hasUrl() -> clipboard.url
                    else -> ""
                }
                val caretIndex = codeArea.caretPosition
                val text = codeArea.text
                replaceText(text.substring(0, caretIndex) + additionalText + text.substring(caretIndex))
            }
            setOnShowing {
                val clipboard = Clipboard.getSystemClipboard()
                hasClipboardContent.set(clipboard.hasString() || clipboard.hasUrl() || clipboard.hasFiles())
            }
        }

        return codeArea
    }

    private fun removeLastFind() {
        lastFindStart = 0
        lastFindEnd = 0
        lastFind = Find(0L, 0L)
        hasFindProperty.set(false)
    }

    private fun addStylesheetsToCodearea() {
        val syntaxHighlightingColorScheme = settingsController.getSettings().syntaxHighlightingColorScheme.internalResource
        this.codeArea.stylesheets.add(internalResourceController.getAsResource(syntaxHighlightingColorScheme))
        this.codeArea.stylesheets.add(internalResourceController.getAsResource(InternalResource.EDITOR_CSS))
    }

    private fun getFullText(): String {
        return if (displayMode.get() == DisplayMode.PAGINATION) {
            // fullText might be out of sync
            syncPage()
            this.fullText
        } else this.codeArea.text
    }


    // Search and replace functions

    /**
     * Searches through the whole text in search for the current search term.
     * All finds will be added to {@see #allFinds}.
     * The callback will either be called once with all findings (plain/pagination)
     * or once per page with all findings of that page (disk pagination)
     */
    private fun searchAll(callback: (finds: List<Find>, text: String, startCharIndex: Long) -> Unit, endCallback: (() -> Unit)? = null) {
        allFinds.clear()

        val searchText = getSearchText()
        val interpreterMode = textInterpreterMode.get() as SearchTextMode
        val ignoreCase = ignoreCaseProperty.get()
        vpexExecutor.execute {
            if (displayMode.get() == DisplayMode.DISK_PAGINATION) {

                Platform.runLater {
                    fileProgressProperty.set(0.0)
                }

                val pageSize = settingsController.getSettings().pageSize
                val fileSize = file!!.length()


                // We can't load full text into memory
                // therefore, we have to go page by page
                // this means that page breaks might hide/split search results
                // to counter this, a pageOverlap is introduced which will cause the searches to overlap
                // TODO: Service Method ==> See SearchAndReplaceController => findNextFromDisk
                // TODO: Reimplement the start-search-from-current-page mechanism
                val accessFile = RandomAccessFile(file, "r")
                var totalBytesRead = 0
                for (pageIndex in 0 until this.maxPage.get()) {
                    val startCharIndex = pageSize.toLong() * pageIndex.toLong()
                    val startByteIndex = pageStartingByteIndexes[pageIndex]
                    val endByteIndex = if (pageIndex + 1 >= pageStartingByteIndexes.size) {
                        fileSize
                    } else {
                        pageStartingByteIndexes[pageIndex + 1]
                    }
                    val pageByteSize = (endByteIndex - startByteIndex).toInt()
                    val buffer = ByteArray(pageByteSize)
                    accessFile.seek(startByteIndex)
                    val read = accessFile.read(buffer)
                    if (read == -1) {
                        break
                    }
                    totalBytesRead += read
                    val string = String(buffer, 0, read)
                    val finds = searchAndReplaceController.findAll(string, searchText, interpreterMode, ignoreCase)
                            .map { find -> Find(find.start + startCharIndex, find.end + startCharIndex) }
                            .filter { find -> !allFinds.contains(find) } // Filter Duplicates
                    allFinds.addAll(finds)
                    callback(finds, string, startCharIndex)
                    Platform.runLater {
                        fileProgressProperty.set(totalBytesRead / fileSize.toDouble())
                    }
                    if (Thread.currentThread().isInterrupted) {
                        break
                    }
                }
                Platform.runLater {
                    fileProgressProperty.set(-1.0)
                }
            } else {
                val fullText = getFullText()
                allFinds.addAll(searchAndReplaceController.findAll(fullText, searchText, interpreterMode, ignoreCase))
                callback(allFinds, fullText, 0)
            }
            if (endCallback != null) {
                endCallback()
            }
        }
    }

    private fun replaceAll(callback: () -> Unit, endCallback: () -> Unit) {
        val replacementText = this.replaceProperty.get()
        if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
            // We need to read the original file page by page,
            // do the replacements on the fly
            // and write each page back to the target file

            // Overwriting the existing file would probably be a bad idea
            // So let the user chose a new one
            val outputFile = choseFile()
            if (outputFile != null && (outputFile.isFile || !outputFile.exists())) {
                val writer = FileWriter(outputFile)
                // SearchAll End Callback will be executed async in a non-ui-thread
                this.searchAll({ unsortedFinds, text, charIndex ->
                    // One Page has been searched. Write that page to file with replacements
                    val finds = unsortedFinds.sortedBy { it.start }
                    // Indexes can be Int since they refer to indexes withing a single page
                    var startIndex = 0
                    var endIndex: Int
                    for (find in finds) {
                        // find index refers to whole file
                        // char index shows offset of this page in the whole file
                        endIndex = (find.start - charIndex).toInt()
                        // Write all Text before this find followed by the replacement
                        writer.write(text, startIndex, endIndex - startIndex)
                        writer.write(replacementText)
                        startIndex = (find.end - charIndex).toInt()
                    }
                    // Whats left of the page
                    endIndex = text.length
                    writer.write(text, startIndex, endIndex - startIndex)
                    callback()
                }, endCallback = {
                    writer.close()
                    endCallback()
                })
            } else {
                logger.info { "User did not chose a valid file - aborting" }
                endCallback()
            }

        } else {
            val stringBuilder = StringBuilder()
            var lastEndIndex = 0

            // SearchAll Callback will be executed once with all findings async in a non-ui-thread
            this.searchAll({ finds, fulltext, _ ->
                logger.debug("Replacing {} findings", finds.size)
                for (find in finds) {
                    stringBuilder.append(fulltext.substring(lastEndIndex, find.start.toInt()))
                    stringBuilder.append(replacementText)
                    lastEndIndex = find.end.toInt()
                }
                logger.debug("Replaced {} findings. Saving to textarea now.", finds.size)
                Platform.runLater {
                    stringBuilder.append(fulltext.substring(lastEndIndex))
                    if (displayMode.get() == DisplayMode.PAGINATION) {
                        changeFullText(stringBuilder.toString())
                    } else {
                        this.codeArea.replaceText(stringBuilder.toString())
                    }
                    logger.debug("Text replaced")
                }
            }, endCallback = endCallback)
        }
    }

    private fun findNext(reverse: Boolean = false) {
        if (showedEndOfFileDialog) {
            showedEndOfFileDialog = false
            if (showedEndOfFileDialogCaretPosition == codeArea.caretPosition) {
                if ((searchDirection.get() == SearchDirection.DOWN && !reverse) || (searchDirection.get() == SearchDirection.UP && reverse)) {
                    moveToIndex(0) {
                        findNextFromCurrentCaretPosition()
                    }
                } else {
                    // TODO: Fix me
                    moveToLineColumn(this.lineCount.get(), 0) {
                        findNextFromCurrentCaretPosition()
                    }
                }
            } else {
                findNextFromCurrentCaretPosition()
            }
        } else {
            findNextFromCurrentCaretPosition()
        }
    }

    private fun findNextFromCurrentCaretPosition(reverse: Boolean = false) {

        Platform.runLater { statusTextProperty.set("Searching next") }

        // Find out where to start searching
        val offset = when (displayMode.get()) {
            DisplayMode.PLAIN -> codeArea.caretPosition
            DisplayMode.DISK_PAGINATION, DisplayMode.PAGINATION -> {
                codeArea.caretPosition + (this.getPageIndex()) * this.settingsController.getSettings().pageSize
            }
            else -> 0 // cant happen
        }

        // Optional offset which prevents us from finding the last find again by skipping the first character
        var searchDirection = searchDirection.get() as SearchDirection
        if (reverse) {
            searchDirection = when (searchDirection) {
                SearchDirection.UP -> SearchDirection.DOWN
                SearchDirection.DOWN -> SearchDirection.UP
            }
        }
        val skipLastFindOffset = if (lastFindEnd > 0 && codeArea.caretPosition == lastFindStart) {
            val lastFindLength = lastFindEnd - lastFindStart
            if (searchDirection == SearchDirection.UP) {
                -lastFindLength
            } else {
                lastFindLength
            }
        } else 0

        // Find text to search in and text to search for
        val searchText = getSearchText()
        val ignoreCase = ignoreCaseProperty.get()

        // Callback after search is done
        val afterSearchResult = { find: Find? ->
            Platform.runLater { statusTextProperty.set("") }
            // Move to search result
            if (find != null) {
                moveToFind(find)
            } else {
                val text = if (searchDirection == SearchDirection.DOWN) {
                    "End reached. Press enter twice to search from the top."
                } else {
                    "Start reached. Press enter twice to search from the bottom."
                }
                val alert = Alert(Alert.AlertType.INFORMATION, text)
                alert.title = "End of file"
                alert.dialogPane.minHeight = Region.USE_PREF_SIZE
                alert.showAndWait()
                showedEndOfFileDialog = true
                showedEndOfFileDialogCaretPosition = codeArea.caretPosition
                findTextField.requestFocus()
                findTextField.selectAll()
            }
        }

        // Search
        if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
            val file = file
            if (file != null) {
                val pageSize = this.settingsController.getSettings().pageSize
                val charOffset = pageSize.toLong() * (this.page.get() - 1) + codeArea.caretPosition + skipLastFindOffset
                vpexExecutor.execute {
                    try {
                        val find = searchAndReplaceController.findNextFromDisk(file, searchText, charOffset, pageSize, pageStartingByteIndexes,
                                searchDirection, textInterpreterMode.get() as SearchTextMode, ignoreCase,
                                currentPageIndex = getPageIndex(), currentPageText = getFullText(), progressCallback = { progress ->
                            if (Thread.currentThread().isInterrupted) {
                                throw InterruptedException("Cancelled")
                            }
                            Platform.runLater {
                                fileProgressProperty.set(progress)
                            }
                        })
                        Platform.runLater {
                            fileProgressProperty.set(-1.0)
                            afterSearchResult(find)
                        }
                    } catch (e: InterruptedException) {
                        logger.info { "Cancelling Syntax Validation" }
                        Platform.runLater {
                            fileProgressProperty.set(-1.0)
                        }
                    }
                }
            } else {
                afterSearchResult(null)
            }
        } else {
            val fullText = getFullText()
            val find = searchAndReplaceController.findNext(fullText, searchText, offset + skipLastFindOffset,
                    searchDirection, textInterpreterMode.get() as SearchTextMode, ignoreCase)
            afterSearchResult(find)
        }


    }

    private fun getSearchText(): String {
        return if (textInterpreterMode.get() as SearchTextMode == SearchTextMode.EXTENDED) {
            findProperty.get().replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
        } else {
            findProperty.get()
        }
    }

    private fun getPageIndex(): Int {
        return this.page.get() - 1
    }

    private fun highlightEverything(pageIndex: Int = 0) {
        highlightingExcutor.highlightEverything(codeArea, allFinds, lastFind, displayMode.get(), pageIndex, showFindProperty.get())
    }

    private fun alertUnsavedChanges(proceedCallback: () -> Unit, cancelCallback: (() -> Unit)? = null) {
        alert(Alert.AlertType.CONFIRMATION, "Unsaved changes", "Unsaved changes will be lost if you do not save.\nDo you want to save?", ButtonType.YES, ButtonType.NO, ButtonType.CANCEL, actionFn = {
            when (it.buttonData.typeCode) {
                ButtonType.YES.buttonData.typeCode -> {
                    saveFileAs(onSaveCallback = {
                        logger.debug { "Saved unsaved changes" }
                        proceedCallback()
                    }, onCancelCallback = {
                        logger.debug { "Cancelled saving unsaved changes" }
                        if (cancelCallback != null) {
                            cancelCallback()
                        }
                    })
                }
                ButtonType.NO.buttonData.typeCode -> {
                    logger.debug { "Drop unsaved changes" }
                    proceedCallback()
                }
                ButtonType.YES.buttonData.typeCode -> {
                    logger.debug { "Cancel @ Unsaved Changes Dialog" }
                    if (cancelCallback != null) {
                        cancelCallback()
                    }
                }
            }
        })
    }

}
