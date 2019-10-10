package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.Styles
import de.henningwobken.vpex.main.controllers.*
import de.henningwobken.vpex.main.model.*
import de.henningwobken.vpex.main.other.FileWatcher
import de.henningwobken.vpex.main.xml.ProgressInputStream
import de.henningwobken.vpex.main.xml.ProgressReader
import de.henningwobken.vpex.main.xml.XmlFormattingService
import javafx.application.Platform
import javafx.beans.property.*
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType.INFORMATION
import javafx.scene.input.KeyCode
import javafx.scene.input.TransferMode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.FileChooser
import javafx.stage.StageStyle
import javafx.util.Duration
import mu.KotlinLogging
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.CodeArea
import org.xml.sax.InputSource
import tornadofx.*
import java.io.*
import java.nio.file.Files
import java.text.NumberFormat
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
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
    private val xmlFormattingService: XmlFormattingService by inject()
    private val searchAndReplaceController by inject<SearchAndReplaceController>()

    private var codeArea: CodeArea by singleAssign()
    private val isDirty: BooleanProperty = SimpleBooleanProperty(false)
    private val charCountProperty = SimpleIntegerProperty(0)
    private var numberFormat = NumberFormat.getInstance(settingsController.getSettings().locale)
    private var file: File? = null
    private var lineCount = SimpleIntegerProperty(0)
    private val statusTextProperty = SimpleStringProperty("")
    private val downloadProgressProperty = SimpleDoubleProperty(-1.0)
    private val fileProgressProperty = SimpleDoubleProperty(-1.0)
    private val saveLockProperty = SimpleBooleanProperty(false)

    private lateinit var findTextField: TextField

    private var fileWatcher: FileWatcher? = null
    private var isAskingForFileReload = false

    // Memory monitor
    private val maxMemory = round(Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0).toLong()
    private val allocatedMemory = SimpleLongProperty(0)
    private val reservedMemory = SimpleLongProperty(0)
    private var memoryMonitorThread: Thread? = null
    private var stopMonitorThread = false
    private val showMonitorThread = SimpleBooleanProperty(false)

    // Search and Replace

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
    private val ignoreCaseProperty = SimpleBooleanProperty(false)

    // Pagination
    private val displayMode = SimpleObjectProperty<DisplayMode>(DisplayMode.PLAIN)
    private var fullText: String = ""
    private val page = SimpleIntegerProperty(1)
    private val pageDisplayProperty = SimpleIntegerProperty(1)
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
            Thread {
                logger.info { "Checking for updates" }
                if (updateController.updateAvailable()) {
                    logger.info { "Update available. Downloading." }
                    Platform.runLater {
                        statusTextProperty.set("Downloading updates")
                        downloadProgressProperty.set(0.0)
                    }
                    updateController.downloadUpdate(progressCallback = { progress, max ->
                        Platform.runLater {
                            downloadProgressProperty.set(progress / (max * 1.0))
                        }
                    }, finishCallback = {
                        Platform.runLater {
                            logger.info { "Download finished." }
                            downloadProgressProperty.set(-1.0)
                            statusTextProperty.set("")
                            confirm("New Version", "A new version has been downloaded. Restart?", ButtonType.OK, ButtonType.CANCEL, actionFn = {
                                updateController.applyUpdate()
                            })
                        }
                    })
                } else {
                    logger.info { "Up to date." }
                    Platform.runLater {
                        statusTextProperty.set("")
                    }
                }
            }.start()
        }
        FX.primaryStage.setOnCloseRequest {
            if (memoryMonitorThread != null) {
                stopMonitorThread = true
                logger.info { "Set stop flag for memory monitor thread" }
            }
            fileWatcher?.stopThread()
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
                    item("Close").action {
                        closeFile()
                    }
                }
                menu("View") {
                    item("Move to", "Shortcut+G").action {
                        moveTo()
                    }
                    item("Search", "Shortcut+F").action {
                        showReplaceProperty.set(false)
                        showFindProperty.set(true)
                        findTextField.requestFocus()
                        findTextField.selectAll()
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
                        findTextField.requestFocus()
                        findTextField.selectAll()
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
                                    findTextField = this
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
                                        ViewHelper.fillHorizontal(this)
                                    }.action {
                                        statusTextProperty.set("Searching")
                                        searchAll { finds ->
                                            Platform.runLater {
                                                highlightFinds(finds)
                                            }
                                        }
                                        val firstInPage = allFinds.filter { isInPage(it) }.minBy { it.start }
                                        if (firstInPage != null) {
                                            moveToFind(firstInPage)
                                        }
                                        statusTextProperty.set("")
                                    }
                                    button("List all") {
                                        ViewHelper.fillHorizontal(this)
                                    }.action {
                                        // TODO: open popup with code snippets of matches
                                    }
                                }
                                vbox(5) {
                                    button("Replace this") {
                                        enableWhen { hasFindProperty.and(showReplaceProperty) }
                                        ViewHelper.fillHorizontal(this)
                                    }.action {
                                        codeArea.replaceText(lastFindStart, lastFindEnd, replaceProperty.get())
                                    }
                                    button("Replace all") {
                                        enableWhen { showReplaceProperty }
                                        ViewHelper.fillHorizontal(this)
                                    }.action {
                                        replaceAll()
                                    }
                                    button("Count") {
                                        ViewHelper.fillHorizontal(this)
                                    }.action {
                                        searchAll { }
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
                button {
                    removeWhen { saveLockProperty }
                    tooltip("Unlocked - Changes will be written to file when pressing CTRL+S. Click to lock.")
                    graphic = internalResourceController.getAsSvg(InternalResource.LOCK_OPEN_ICON)
                }.action {
                    saveLockProperty.set(true)
                }
                button {
                    removeWhen { saveLockProperty.not() }
                    tooltip("Locked - CTRL+S will open a 'save as' dialog. Click to unlock.")
                    graphic = internalResourceController.getAsSvg(InternalResource.LOCK_CLOSED_ICON)
                }.action {
                    saveLockProperty.set(false)
                }
                label("") {
                    ViewHelper.fillHorizontal(this)
                }
                label(statusTextProperty)
                progressbar(downloadProgressProperty) {
                    removeWhen(downloadProgressProperty.lessThan(0))
                }
                progressbar(fileProgressProperty) {
                    removeWhen(fileProgressProperty.lessThan(0))
                }
                hbox(10) {
                    alignment = Pos.CENTER
                    removeWhen(displayMode.isEqualTo(DisplayMode.PLAIN))
                    button("<<") {
                        disableWhen {
                            page.isEqualTo(1)
                        }
                    }.action {
                        val dirty = isDirty.get()
                        moveToPage(getPageIndex())
                        isDirty.set(dirty)
                    }
                    hbox(5) {
                        alignment = Pos.CENTER
                        textfield(pageDisplayProperty) {
                            page.onChange { pageDisplayProperty.set(page.get()) }
                            prefWidth = 50.0
                            maxWidth = 50.0
                        }.action {
                            val enteredPage = pageDisplayProperty.get()
                            if (enteredPage < 1 || enteredPage > maxPage.get()) {
                                pageDisplayProperty.set(page.get())
                            } else {
                                val dirty = isDirty.get()
                                moveToPage(pageDisplayProperty.get())
                                isDirty.set(dirty)
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
                        val dirty = isDirty.get()
                        moveToPage(page.get() + 1)
                        isDirty.set(dirty)
                    }
                }
                button("Close").action {
                    closeFile()
                }
                hbox(10) {
                    removeWhen { displayMode.isEqualTo(DisplayMode.DISK_PAGINATION) }
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

    private fun closeFile() {
        file = null
        replaceText("")
        fullText = ""
        lineCount.bind(codeArea.paragraphs.sizeProperty())
        displayMode.set(DisplayMode.PLAIN)
        isDirty.set(false)
        statusTextProperty.set("")
        fileProgressProperty.set(-1.0)
        fileWatcher?.stopThread()
        fileWatcher = null
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
        checkForDisplayModeChange()
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

    private fun checkForDisplayModeChange() {
        logger.info("Checking for Display Mode Change")
        // When a new file has been opened or the settings have changed,
        // the display mode might have to be changed

        val file = this.file
        val settings = this.settingsController.getSettings()
        if (settings.diskPagination && file != null && file.length() > settings.diskPaginationThreshold * 1024 * 1024) {
            // Should be disk pagination
            if (displayMode.get() == DisplayMode.PLAIN || displayMode.get() == DisplayMode.PAGINATION) {
                displayMode.set(DisplayMode.DISK_PAGINATION)
                codeArea.replaceText("")
                fullText = ""
                moveToPage(1)
                codeArea.isEditable = false
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
                codeArea.isEditable = true
                displayMode.set(DisplayMode.PAGINATION)
                if (file != null) {
                    openFile(file)
                }
            }

        } else {
            // Should be plain
            if (displayMode.get() == DisplayMode.PAGINATION) {
                displayMode.set(DisplayMode.PLAIN)
                disablePagination()
            } else if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
                displayMode.set(DisplayMode.PLAIN)
                codeArea.isEditable = true
                if (file != null) {
                    openFile(file)
                }
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
        val max = if (displayMode.get() == DisplayMode.PAGINATION) {
            ceil(this.fullText.length / this.settingsController.getSettings().pageSize.toDouble()).toInt()
        } else {
            val file = file
            if (file != null) {
                ceil(file.length() / settingsController.getSettings().pageSize.toDouble()).toInt()
            } else 0
        }
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
        val pageSize = settingsController.getSettings().pageSize
        lastFindEnd = 0
        lastFindStart = 0
        hasFindProperty.set(false)
        if (dirtySinceLastSync) {
            if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
                logger.warn { "Changes will be lost." }
            } else {
                if (syncDirection == SyncDirection.TO_FULLTEXT) {
                    logger.info("Syncing CodeArea text to full text")
                    this.fullText = this.fullText.replaceRange((this.getPageIndex()) * pageSize, min(this.page.get() * pageSize, this.fullText.length), this.codeArea.text)
                } else {
                    logger.info("Syncing full text to CodeArea")
                }
                this.maxPage.set(calcMaxPage())
                this.calcLinesAllPages()
                this.dirtySinceLastSync = false
            }
        }
        this.page.set(page)
        if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
            val file = getFile()
            val destinationBuffer = ByteArray(pageSize)
            val offset = pageSize * (page - 1)
            val randomAccessFile = RandomAccessFile(file, "r")
            randomAccessFile.seek(offset.toLong())
            randomAccessFile.read(destinationBuffer, 0, pageSize)
            randomAccessFile.close()
            replaceText(String(destinationBuffer))
        } else {
            replaceText(this.fullText.substring((page - 1) * pageSize, min(page * pageSize, this.fullText.length)))
        }
    }

    private fun getFile(): File {
        val file = this.file
        if (file == null) {
            logger.error { "File not set" }
        }
        return file!!
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
            moveTo(userLine.toLong(), userColumn.toLong())
        }
    }

    private fun moveTo(userLine: Long, userColumn: Long) {
        if (displayMode.get() == DisplayMode.PAGINATION) {
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
            val line = max(min((userLine - pageStartingLineCounts[page - 1]).toInt(), codeArea.paragraphs.size), 1) - 1
            // TODO: Should be long
            val column = min(userColumn.toInt(), codeArea.getParagraph(line).length())
            moveToLineColumn(line, column)
        } else if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
            // TODO: Read next page from file
            throw UnsupportedOperationException("Not yet implemented")
        } else {
            val line = max(min(userLine.toInt(), codeArea.paragraphs.size), 1) - 1
            val column = min(userColumn.toInt(), codeArea.getParagraph(line).length())
            moveToLineColumn(line, column)
        }
    }

    private fun moveToIndex(index: Long) {
        when {
            displayMode.get() == DisplayMode.PLAIN -> moveToLineColumn(0, index.toInt())
            else -> {
                val page = getPageOfIndex(index)
                if (page != this.page.get()) {
                    moveToPage(page)
                }
                val inPageIndex: Int = (index - ((this.getPageIndex()) * this.settingsController.getSettings().pageSize)).toInt()
                if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
                    // In Disk Pagination, the index refers to bytes, not characters
                    // Assuming at least 1 byte per character, we can start at string byte length and work our way backwards
                    var charIndex = min(inPageIndex, codeArea.text.length)
                    var byteCount = codeArea.text.substring(0, charIndex).toByteArray().size
                    while (true) {
                        if (byteCount == inPageIndex) {
                            break
                        }
                        val codePoint = codeArea.text.codePointAt(charIndex)
                        byteCount -=
                                when {
                                    codePoint <= 0x7F -> 1
                                    codePoint <= 0x7FF -> 2
                                    codePoint <= 0xFFFF -> 3
                                    codePoint <= 0x1FFFFF -> 4
                                    else -> 0
                                }
                        charIndex--
                    }
                    this.moveToLineColumn(0, charIndex)
                } else {
                    this.moveToLineColumn(0, inPageIndex)
                }

            }
        }
    }

    private fun moveToFind(find: Find) {
        moveToIndex(find.start)
        lastFindStart = codeArea.anchor
        val findLength = find.end - find.start
        val findEnd = codeArea.anchor + findLength.toInt()
        lastFindEnd = findEnd
    }

    private fun getPageOfIndex(index: Long): Int {
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
        statusTextProperty.set("Validating Syntax")
        Thread {
            logger.info("Validating Syntax...")
            val saxParserFactory = SAXParserFactory.newInstance()
            saxParserFactory.isNamespaceAware = true
            val saxParser = saxParserFactory.newSAXParser()
            val xmlReader = saxParser.xmlReader
            if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
                val file = getFile()
                val fileLength = file.length()
                xmlReader.parse(InputSource(ProgressInputStream(file.inputStream()) {
                    Platform.runLater {
                        fileProgressProperty.set(it / fileLength.toDouble())
                    }
                }))
            } else {
                val text = getFullText()
                val textLength = text.length
                xmlReader.parse(InputSource(ProgressInputStream(text.byteInputStream()) {
                    Platform.runLater {
                        fileProgressProperty.set(it / textLength.toDouble())
                    }
                }))
            }
            Platform.runLater {
                fileProgressProperty.set(-1.0)
                statusTextProperty.set("")
                alert(INFORMATION, "The council has decided", "The syntax of this xml file is valid.")
            }
        }.start()
    }

    private fun validateSchema() {
        statusTextProperty.set("Validating Schema")
        val resultFragment = find<SchemaResultFragment>()
        resultFragment.gotoLineColumn = this::moveTo
        resultFragment.openWindow(stageStyle = StageStyle.UTILITY)

        if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
            val file = getFile()
            val fileLength = file.length()
            resultFragment.validateSchema(file.inputStream(), fileLength)
        } else {
            val text = getFullText()
            val textLength = text.length
            resultFragment.validateSchema(text.byteInputStream(), textLength.toLong())
        }
    }

    private fun prettyPrint() {
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
                    fileWatcher?.ignore?.set(true)
                    val inputFile = getFile()
                    val inputFileLength = inputFile.length()
                    val xmlOutput = StreamResult(FileWriter(outputFile))
                    val xmlInput = StreamSource(InputStreamReader(ProgressInputStream(inputFile.inputStream()) {
                        Platform.runLater {
                            fileProgressProperty.set(it / inputFileLength.toDouble())
                        }
                    }))
                    transformer.transform(xmlInput, xmlOutput)
                    fileWatcher?.ignore?.set(false)
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
                val textLength = text.length
                val stringWriter = StringWriter()
                val xmlOutput = StreamResult(stringWriter)
                val xmlInput = StreamSource(InputStreamReader(ProgressInputStream(text.byteInputStream()) {
                    Platform.runLater {
                        fileProgressProperty.set(it / textLength.toDouble())
                    }
                }))
                transformer.transform(xmlInput, xmlOutput)
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

    private fun uglyPrint() {
        statusTextProperty.set("Making this xml ugly like a Fiat Multipla")
        if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
            // Overwriting the existing file would probably be a bad idea
            // So let the user chose a new one
            val outputFile = choseFile()
            if (outputFile != null && (outputFile.isFile || !outputFile.exists())) {
                Thread {
                    val xmlOutput = FileWriter(outputFile)
                    val inputFile = getFile()
                    val inputFileLength = inputFile.length().toDouble()
                    val xmlInput = ProgressReader(FileReader(inputFile)) {
                        Platform.runLater {
                            fileProgressProperty.set(it / inputFileLength)
                        }
                    }
                    xmlFormattingService.uglyPrint(xmlInput, xmlOutput)
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
                xmlFormattingService.uglyPrint(xmlInput, xmlOutput)
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

    private fun choseFile(): File? {
        val fileChooser = FileChooser()
        fileChooser.title = "Chose destination"
        fileChooser.initialDirectory = File(settingsController.getSettings().openerBasePath).absoluteFile
        return fileChooser.showSaveDialog(FX.primaryStage)
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
        if (file != null && !saveLockProperty.get()) {
            val text = getFullText()
            fileWatcher?.ignore?.set(true)
            Files.write(file.toPath(), text.toByteArray())
            fileWatcher?.ignore?.set(false)
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
            fileWatcher?.ignore?.set(true)
            Files.write(file.toPath(), text.toByteArray())
            fileWatcher?.ignore?.set(false)
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
        codeArea.replaceText("")
        fullText = ""
        codeArea.isEditable = true
        val fileWatcher = FileWatcher(file) {
            Platform.runLater {
                if (!isAskingForFileReload) {
                    isAskingForFileReload = true
                    confirmation("Update", "The file was updated externally. Reload?", ButtonType.OK, ButtonType.CANCEL, actionFn = {
                        isAskingForFileReload = false
                        if (it.buttonData.isDefaultButton) {
                            closeFile()
                            openFile(file)
                        }
                    })
                }
            }
        }
        this.fileWatcher = fileWatcher
        fileWatcher.start()
        val settings = settingsController.getSettings()
        if (settings.diskPagination && file.length() > settings.diskPaginationThreshold * 1024 * 1024) {
            logger.info { "Opening file in disk pagination mode" }
            displayMode.set(DisplayMode.DISK_PAGINATION)
            moveToPage(1)
            maxPage.set(calcMaxPage())
            codeArea.isEditable = false
        } else if (this.settingsController.getSettings().pagination) {
            this.fullText = file.readText()
            if (fullText.length > settingsController.getSettings().paginationThreshold) {
                logger.info { "Opening file in pagination mode" }
                displayMode.set(DisplayMode.PAGINATION)
                dirtySinceLastSync = true
                moveToPage(1, SyncDirection.TO_CODEAREA)
                lineCount.bind(pageTotalLineCount)
            } else {
                logger.info { "Opening file in plain mode" }
                displayMode.set(DisplayMode.PLAIN)
                replaceText(file.readText())
                lineCount.bind(codeArea.paragraphs.sizeProperty)
            }
        } else {
            logger.info { "Opening file in plain mode" }
            displayMode.set(DisplayMode.PLAIN)
            lineCount.bind(codeArea.paragraphs.sizeProperty)
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
                    when {
                        displayMode.get() == DisplayMode.PAGINATION -> this.fullText.length - this.settingsController.getSettings().pageSize + this.codeArea.text.length
                        displayMode.get() == DisplayMode.DISK_PAGINATION -> {
                            // TODO: Handle Disk pagination
                            0
                        }
                        else -> this.codeArea.text.length
                    }
            )
            if (!this.textOperationLock) {
                // Only dirty if user changed something
                this.isDirty.set(true)
                this.dirtySinceLastSync = true
                if (displayMode.get() == DisplayMode.PAGINATION) {
                    val insertedLines = this.stringUtils.countLinesInString(it.inserted) - 1
                    val removedLines = this.stringUtils.countLinesInString(it.removed) - 1
                    this.pageTotalLineCount.set(this.pageTotalLineCount.get() + insertedLines - removedLines)
                }
            }
            if (this.hasFindProperty.get()) {
                // The edit has invalidated the search result => Remove Highlight
                if (this.codeArea.text.length >= this.findProperty.get().length &&
                        this.codeArea.text.substring(this.lastFindStart, this.lastFindEnd) != this.findProperty.get()) {
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
            if (displayMode.get() == DisplayMode.PAGINATION) {
                pageStartingLineCounts[this.getPageIndex()]
            } else {
                0
            } // TODO: Disk Pagination?
        }

        return codeArea
    }

    private fun getFullText(): String {
        return if (displayMode.get() == DisplayMode.PAGINATION) {
            // fullText might be out of sync
            if (this.isDirty.get()) {
                moveToPage(this.page.get()) // Syncs the page with the fulltext
            }
            this.fullText
        } else this.codeArea.text
    }


    // Search and replace functions

    private fun removeFindHighlighting() {
        if (codeArea.length > 0) {
            this.codeArea.clearStyle(0, codeArea.length - 1)
        }
        this.hasFindProperty.set(false)
        this.lastFindStart = 0
        this.lastFindEnd = 0
    }

    private fun searchAll(callback: (finds: List<Find>) -> Unit) {
        allFinds.clear()

        val searchText = getSearchText()
        val interpreterMode = textInterpreterMode.get() as SearchTextMode
        val ignoreCase = ignoreCaseProperty.get()
        Thread {
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
                val pageOverlap = max(100, searchText.length)
                // We dont want page overlap on our first search. Add it here so it gets substracted in the iteration
                // Also, we want to start at the current page to display the visible results asap
                var fileOffset = pageSize * getPageIndex() + pageOverlap.toLong()
                val startingFileOffset = fileOffset // So we know when to stop later
                var readTotal = -pageOverlap.toLong()
                val file = RandomAccessFile(file, "r")
                val buffer = ByteArray(pageSize)
                var hasReset = false // Whether we already reached the end or not
                while (true) {
                    val cursorPosition = fileOffset - pageOverlap
                    file.seek(cursorPosition)
                    val read = file.read(buffer)
                    readTotal += read
                    if (read == -1 || read == pageOverlap) {
                        // Reached End -> Reset to page no 1
                        fileOffset = pageOverlap.toLong()
                        hasReset = true
                        continue
                    }
                    val finds = searchAndReplaceController.findAll(String(buffer, 0, read), searchText, interpreterMode, ignoreCase)
                            .map { find -> Find(find.start + cursorPosition, find.end + cursorPosition) }
                            .filter { find -> !allFinds.contains(find) } // Filter Duplicates
                    allFinds.addAll(finds)
                    callback(finds)
                    Platform.runLater {
                        allFindsSize.set(allFinds.size)
                        fileProgressProperty.set(readTotal / fileSize.toDouble())
                    }
                    fileOffset += read
                    if (hasReset && fileOffset >= startingFileOffset) {
                        break
                    }
                }
                Platform.runLater {
                    fileProgressProperty.set(-1.0)
                }
            } else {
                val fullText = getFullText()
                allFinds.addAll(searchAndReplaceController.findAll(fullText, searchText, interpreterMode, ignoreCase))
                callback(allFinds)
            }
            allFindsSize.set(allFinds.size)
        }.start()
    }

    private fun highlightFinds(finds: List<Find>) {
        if (finds.isEmpty()) {
            return
        }
        this.hasFindProperty.set(true)
        val pageSize = this.settingsController.getSettings().pageSize
        if (displayMode.get() == DisplayMode.PLAIN) {
            for (find in finds) {
                codeArea.setStyle(find.start.toInt(), find.end.toInt(), listOf("searchHighlight"))
            }
        } else {
            val pageOffset = this.getPageIndex() * pageSize.toLong()
            for (find in finds) {
                if (isInPage(find)) {
                    val start = max(find.start - pageOffset, 0).toInt()
                    val end = min(find.end - pageOffset, pageSize.toLong() - 1).toInt()
                    codeArea.setStyle(start, end, listOf("searchHighlight"))
                }
            }
        }

    }

    private fun replaceAll() {
        this.searchAll { } // TODO: Do replacements here?
        if (allFinds.isEmpty()) {
            logger.info("No Replacements made")
            return
        }
        if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
            // We need to read the original file page by page,
            // do the replacements on the fly
            // and write each page back to the target file
//            file.inputStream()
            throw java.lang.UnsupportedOperationException("Not yet implemented")
        } else {
            val fulltext = getFullText()
            val stringBuilder = StringBuilder()
            val replacementText = this.replaceProperty.get()
            var lastEndIndex = 0
            for (find in allFinds) {
                stringBuilder.append(fulltext.substring(lastEndIndex, find.start.toInt()))
                stringBuilder.append(replacementText)
                lastEndIndex = find.end.toInt()
            }
            stringBuilder.append(fulltext.substring(lastEndIndex))
            if (displayMode.get() == DisplayMode.PAGINATION) { // TODO: Disk Pagination
                changeFullText(stringBuilder.toString())
            } else {
                this.codeArea.replaceText(stringBuilder.toString())
            }
        }

    }

    private fun isInPage(find: Find): Boolean {
        val pageSize = this.settingsController.getSettings().pageSize
        val lowerBound = this.getPageIndex() * pageSize
        val upperBound = this.page.get() * pageSize
        return find.end in lowerBound until upperBound || find.start in lowerBound until upperBound
    }

    private fun findNext() {
        // Find out where to start searching
        val offset = if (displayMode.get() == DisplayMode.PLAIN) {
            codeArea.caretPosition
        } else {
            codeArea.caretPosition + (this.getPageIndex()) * this.settingsController.getSettings().pageSize
        }

        // Optional offset which prevents us from finding the last find again by skipping the first character
        val searchDirection = searchDirection.get() as SearchDirection
        val skipLastFindOffset = if (lastFindEnd > 0) {
            if (searchDirection == SearchDirection.UP) {
                -1
            } else {
                1
            }
        } else 0

        // Find text to search in and text to search for
        val searchText = getSearchText()
        val ignoreCase = ignoreCaseProperty.get()

        // Search
        val find: Find? = if (displayMode.get() == DisplayMode.DISK_PAGINATION) {
            // We can't load full text into memory
            // therefore, we have to go page by page
            // this means that page breaks might hide/split search results
            // to counter this, a pageOverlap is introduced which will cause the searches to overlap
            val pageOverlap = max(100, searchText.length)
            // We dont want page overlap on our first search. Add it here so it gets substracted in the iteration
            var fileOffset = (offset + skipLastFindOffset).toLong() + pageOverlap
            val file = RandomAccessFile(file, "r")
            val buffer = ByteArray(this.settingsController.getSettings().pageSize)
            var tmpFind: Find? = null
            while (true) {
                file.seek(fileOffset - pageOverlap)
                val read = file.read(buffer)
                if (read == -1) {
                    break
                }
                val string = String(buffer, 0, read)
                tmpFind = searchAndReplaceController.findNext(string, searchText, 0,
                        searchDirection, textInterpreterMode.get() as SearchTextMode, ignoreCase)
                if (tmpFind != null) {
                    // If the file is unicode, one byte != one character
                    // Since we search through the file in pages of byte arrays, there is no way to know
                    // what character number we are at right now.
                    // Therefore, convert the char indices to byte indices
                    // If this solution is causing performance problems, refer to the following SO Thread:
                    // https://stackoverflow.com/questions/27651543/character-index-to-and-from-byte-index
                    val cursorPosition = fileOffset - pageOverlap
                    // Bytes before the find
                    val prefixByteLength = string.substring(0, tmpFind.start.toInt()).toByteArray().size
                    // Bytes of the find
                    val findByteLength = string.substring(tmpFind.start.toInt(), tmpFind.end.toInt()).toByteArray().size
                    tmpFind = Find(prefixByteLength + cursorPosition, prefixByteLength + findByteLength + cursorPosition)
                    break
                }
                fileOffset += read
            }
            tmpFind

        } else {
            val fullText = getFullText()
            searchAndReplaceController.findNext(fullText, searchText, offset + skipLastFindOffset,
                    searchDirection, textInterpreterMode.get() as SearchTextMode, ignoreCase)
        }

        // Move to search result
        if (find != null) {
            moveToIndex(find.start)
            Platform.runLater {
                val findStart = codeArea.anchor
                val findLength = find.end - find.start
                val findEnd = codeArea.anchor + findLength.toInt()
                codeArea.clearStyle(lastFindStart, lastFindEnd)
                // Search Result may be split by two pages, so cap at text length
                codeArea.setStyle(findStart, min(findEnd, codeArea.text.length), listOf("searchHighlight"))
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
}
