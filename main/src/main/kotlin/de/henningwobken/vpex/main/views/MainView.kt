package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.controllers.*
import de.henningwobken.vpex.main.model.InternalResource
import javafx.application.Platform
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.input.*
import javafx.scene.layout.BorderPane
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.StageStyle
import javafx.util.Duration
import mu.KotlinLogging
import tornadofx.*
import java.awt.Desktop
import java.awt.Robot
import java.io.File
import java.text.NumberFormat


class MainView : View("VPEX: View, parse and edit large XML Files") {
    private val logger = KotlinLogging.logger {}
    private val internalResourceController: InternalResourceController by inject()
    private val settingsController: SettingsController by inject()
    private val updateController: UpdateController by inject()
    private val vpexExecutor: VpexExecutor by inject()
    private val windowsContextMenuController: WindowsContextMenuController by inject()
    private val vpexTriggerMonitor: VpexTriggerMonitor by inject()
    private val tabController: TabController by inject()
    private val statusBarView: StatusBarView by inject()
    private val memoryMonitor: MemoryMonitor by inject()
    private val fileWatcher: FileWatcher by inject()
    private val windowsLinkController: WindowsLinkController by inject()

    private var numberFormat = NumberFormat.getInstance(settingsController.getSettings().locale)
    private val statusTextProperty = SimpleStringProperty("")
    private val downloadProgressProperty = SimpleDoubleProperty(-1.0)
    private var newCounter = 0

    // Draggable Tabs
    private var currentDraggingTab: Tab? = null
    private val draggingId = "VPEX-MAINVIEW"

    private lateinit var tabPane: TabPane


    init {
        internalResourceController.getAsStrings(InternalResource.BANNER).forEach(::println)
        if (settingsController.getSettings().contextMenu) {
            windowsContextMenuController.addVpexContextMenuEntry()
        }
        if (settingsController.getSettings().startMenu) {
            windowsLinkController.addVpexStartMenuEntry()
        }
        if (settingsController.getSettings().desktopIcon) {
            windowsLinkController.addVpexDesktopIcon()
        }
        statusBarView.bindDownloadProperties(downloadProgressProperty, statusTextProperty)
        if (settingsController.getSettings().autoUpdate) {
            statusTextProperty.set("Checking for updates")
            Thread {
                updateController.updateRoutine(downloadStartedCallback = {
                    Platform.runLater {
                        logger.debug { "Starting download" }
                        statusTextProperty.set("Downloading updates")
                        downloadProgressProperty.set(0.0)
                    }
                }, downloadProgressCallback = { progress, max ->
                    Platform.runLater {
                        downloadProgressProperty.set(progress / (max * 1.0))
                    }
                }, downloadFinishedCallback = {
                    logger.debug { "Download finished" }
                    Platform.runLater {
                        downloadProgressProperty.set(-1.0)
                        statusTextProperty.set("")
                    }
                }, noUpdateCallback = {
                    Platform.runLater {
                        statusTextProperty.set("")
                    }
                })
            }.start()
        }

        settingsController.settingsProperty.onChange {
            updateSettings()
        }
        updateSettings()

        FX.primaryStage.setOnShowing {
            // Memory Monitor will be started in updateSettings
            fileWatcher.start()
            vpexTriggerMonitor.start {
                Platform.runLater {
                    val robot = Robot()
                    // TODO: Spawn a fake-window, click that with robot, close it
                    FX.primaryStage.toFront()
                    open(File(it))
                }
            }
            open(null)
        }
        FX.primaryStage.setOnCloseRequest {
            memoryMonitor.stop()
            fileWatcher.stop()
            vpexExecutor.shutdown()
            vpexTriggerMonitor.shutdown()
        }
    }

    // UI
    override val root: BorderPane = borderpane {
        top {
            menubar {
                menu("File") {
                    item("New", "Shortcut+N").action {
                        open(null)
                    }
                    item("Open", "Shortcut+O").action {
                        open()
                    }
                    item("Save", "Shortcut+S").action {
                        currentlyActiveTabView().saveFile()
                    }
                    item("Save as", "Shortcut+Shift+S").action {
                        currentlyActiveTabView().saveFileAs()
                    }
                    item("Close", "Shortcut+W").action {
                        requestCloseTab(currentlyActiveTab())
                    }
                }
                menu("View") {
                    item("Move to", "Shortcut+G").action {
                        currentlyActiveTabView().moveTo()
                    }
                    item("Search", "Shortcut+F").action {
                        currentlyActiveTabView().openSearch()
                    }
                }
                menu("Edit") {
                    item("Pretty print", "Shortcut+Shift+F").action {
                        currentlyActiveTabView().prettyPrint()
                    }
                    item("Force pretty print").action {
                        confirm("This pretty print is experimental", "Please check the result after using this.", ButtonType.OK, ButtonType.CANCEL, actionFn = {
                            currentlyActiveTabView().forcePrettyPrint()
                        })
                    }
                    item("Ugly print", "Shortcut+Alt+Shift+F").action {
                        confirm("Ugly print is experimental", "Please check the result after using this.", ButtonType.OK, ButtonType.CANCEL, actionFn = {
                            currentlyActiveTabView().uglyPrint()
                        })
                    }
                    item("Replace", "Shortcut+R").action {
                        currentlyActiveTabView().openReplace()
                    }
                }
                menu("Validate") {
                    item("Syntax", "Shortcut+H").action {
                        currentlyActiveTabView().validateSyntax()
                    }
                    item("Schema", "Shortcut+J") {
                        enableWhen { vpexExecutor.isRunning.not() }
                    }.action {
                        currentlyActiveTabView().validateSchema()
                    }
                    item("Schema (Multiple Files)", "Shortcut+K") {
                        enableWhen { vpexExecutor.isRunning.not() }
                    }.action {
                        validateSchemaMultipleFiles()
                    }
                    item("Schema (Directory)", "Shortcut+Shift+K") {
                        enableWhen { vpexExecutor.isRunning.not() }
                    }.action {
                        validateSchemaDirectory()
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
        center = tabpane {
            tabPane = this
            stylesheets.add(internalResourceController.getAsResource(InternalResource.TABPANE_CSS))
            selectionModel.selectedItemProperty().addListener(ChangeListener { observable, oldValue, newValue ->
                logger.debug { "----------" }
                logger.debug { "Old Tab: $oldValue" }
                logger.debug { "New Tab: $newValue" }
                logger.debug { "----------" }
                if (newValue != null) {
                    logger.info { "Tab Selection changed to tab $newValue" }
                    statusBarView.bind(tabController.getTabView(newValue))
                    Platform.runLater {
                        // Without runLater, the focus does not work
                        // And since runLater executes this code async, we need to check that there still is a selected tab
                        if (tabPane.selectionModel.selectedItem != null) {
                            currentlyActiveTabView().focusCodeArea()
                        }
                    }
                } else {
                    statusBarView.unbind()
                }
            })
            tabClosingPolicy = TabPane.TabClosingPolicy.ALL_TABS
            // if we drag onto a tab pane (but not onto the tab graphic), add the tab to the end of the list of tabs:
            onDragOver = EventHandler { e: DragEvent ->
                if (draggingId == e.dragboard.string && currentDraggingTab != null && currentDraggingTab!!.tabPane !== tabPane) {
                    e.acceptTransferModes(TransferMode.MOVE)
                }
            }
            onDragDropped = EventHandler { e: DragEvent ->
                if (draggingId == e.dragboard.string && currentDraggingTab != null && currentDraggingTab!!.tabPane !== tabPane) {
                    currentDraggingTab!!.tabPane.tabs.remove(currentDraggingTab)
                    tabPane.tabs.add(currentDraggingTab)
                    currentDraggingTab!!.tabPane.selectionModel.select(currentDraggingTab)
                }
            }
            tabs.addListener { c: ListChangeListener.Change<out Tab?> ->
                while (c.next()) {
                    if (c.wasAdded()) {
                        c.addedSubList.forEach { tab: Tab? ->
                            logger.debug { "Added drag handler for $tab" }
                            addDragHandlers(tab)
                        }
                    }
                    if (c.wasRemoved()) {
                        c.removed.forEach { tab: Tab? ->
                            logger.debug { "Removed drag handler for $tab" }
                            removeDragHandlers(tab)
                        }
                    }
                }
            }
        }
        bottom = statusBarView.root

        shortcut(KeyCombination.valueOf("Ctrl+Tab")) {
            logger.debug("Selecting next tab")
            tabPane.selectionModel.selectNext()
        }
        shortcut(KeyCombination.valueOf("Ctrl+Shift+Tab")) {
            logger.debug("Selecting last tab")
            tabPane.selectionModel.selectLast()
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
                for (file in dragboard.files) {
                    if (file.exists() && file.isFile) {
                        open(file)
                    }
                }
                success = true
            }
            /* let the source know whether the string was successfully
                 * transferred and used */
            event.isDropCompleted = success

            event.consume()
        }

    }

    private fun updateSettings() {
        logger.info("Settings updated")
        numberFormat = NumberFormat.getInstance(settingsController.getSettings().locale)
        if (settingsController.getSettings().memoryIndicator) {
            if (!memoryMonitor.isRunning) {
                memoryMonitor.start()
            }
        } else {
            if (memoryMonitor.isRunning) {
                memoryMonitor.stop()
            }
        }
    }

    private fun currentlyActiveTabView(): TabView {
        return tabController.getTabView(tabPane.selectionModel.selectedItem)
    }

    private fun currentlyActiveTab(): Tab {
        return tabPane.selectionModel.selectedItem
    }


    private fun validateSchemaDirectory() {
        val directoryChooser = DirectoryChooser()
        directoryChooser.title = "Choose directory to validate"
        directoryChooser.initialDirectory = File(settingsController.getOpenerBasePath())
        val directory = directoryChooser.showDialog(FX.primaryStage)
        if (directory == null) {
            return
        }
        settingsController.setOpenerBasePath(directory.absolutePath)
        val files = directory.walk().filter { it.isFile }.toList()
        if (files.isEmpty()) {
            return
        }
        validateSchemaForFiles(files)
    }

    private fun validateSchemaMultipleFiles() {
        val fileChooser = FileChooser()
        fileChooser.title = "Choose files to validate"
        fileChooser.initialDirectory = File(settingsController.getOpenerBasePath())
        val files = fileChooser.showOpenMultipleDialog(FX.primaryStage)
        if (files == null || files.isEmpty()) {
            return
        }
        settingsController.setOpenerBasePath(files[0].parentFile.absolutePath)
        validateSchemaForFiles(files)
    }

    private fun validateSchemaForFiles(files: List<File>) {
        val resultFragment = find<SchemaResultFragment>()
        resultFragment.gotoLineColumn = { line, column, file ->
            if (file != null) {
                open(file)
                val tabView = currentlyActiveTabView()
                tabView.moveTo(line, column)
                tabView.focusCodeArea()
            }
        }
        val stage = resultFragment.openWindow(stageStyle = StageStyle.UTILITY)
        stage?.requestFocus()
        resultFragment.validateSchemaForFiles(files)
    }

    private fun open(file: File?) {
        val tabView = if (file != null) {
            tabController.getTabView(file)
        } else null
        if (tabView != null) {
            logger.debug { "Selecting tabview for ${tabView.getFile()?.absolutePath ?: ""}" }
            tabPane.selectionModel.select(tabController.getTab(tabView))
        } else {
            val tab = Tab()
            tab.text = if (file != null) {
                // If the current tab is a temporary "new"-tab that has not been edited, replace it
                val selectedTab = tabPane.selectionModel.selectedItem
                if (selectedTab != null) {
                    val selectedTabView = tabController.getTabView(selectedTab)
                    // TODO: Fix isDirty to return to false if original content is restored
                    if (!selectedTabView.isDirty.get() && !selectedTabView.hasFile.get()) {
                        requestCloseTab(selectedTab)
                    }
                }
                file.name
            } else {
                newCounter++
                "New $newCounter"
            }
            logger.debug { "Creating tab $tab" }
            tab.setOnCloseRequest {
                logger.debug { "Request to close tab $tab" }
                var callbackWasCalled = false
                tabController.requestCloseTab(tab) {
                    logger.debug { "Removing tab $tab from tabPane by X-Button" }
                    tabController.closeTab(tab)
                    callbackWasCalled = true
                }
                if (!callbackWasCalled) {
                    it.consume()
                }
            }
            val view = find(TabView::class)
            if (file != null) {
                view.openFile(file)
            }
            tab.contextMenu = contextmenu {
                item("Close this").action {
                    requestCloseTab(tab)
                }
                item("Close Others").action {
                    tabPane.tabs.filter { it != tab }.forEach { requestCloseTab(it) }
                }
                item("Close All").action {
                    tabPane.tabs.slice(IntRange(0, tabPane.tabs.size - 1))
                            .forEach { requestCloseTab(it) }
                }
                item("Close All to the Left").action {
                    val index = tabPane.tabs.indexOf(tab)
                    tabPane.tabs.slice(IntRange(0, index - 1))
                            .forEach { requestCloseTab(it) }
                }
                item("Close All to the Reft").action {
                    val index = tabPane.tabs.indexOf(tab)
                    tabPane.tabs.slice(IntRange(index + 1, tabPane.tabs.size - 1))
                            .forEach { requestCloseTab(it) }
                }
                separator()
                item("Show in Explorer") {
                    disableWhen { view.hasFile.not() }
                }.action {
                    val currentFile = view.getFile()
                    if (currentFile != null) {
                        Thread {
                            // TODO: Highlight File in Directory (Available in Java 9+)
                            Desktop.getDesktop().open(currentFile.parentFile)
                        }.start()
                    }
                }
                item("Copy Filename") {
                    disableWhen { view.hasFile.not() }
                }.action {
                    val currentFile = view.getFile()
                    if (currentFile != null) {
                        Clipboard.getSystemClipboard().putString(currentFile.name)
                    }
                }
            }
            tab.content = view.root
            tabController.addTab(tab, view)
            tabPane.tabs.add(tab)
            tabPane.selectionModel.select(tab)
        }
    }

    private fun open() {
        logger.info("Opening new file")
        val fileChooser = FileChooser()
        fileChooser.title = "Open new File"
        fileChooser.initialDirectory = File(settingsController.getOpenerBasePath()).absoluteFile
        val file = fileChooser.showOpenDialog(FX.primaryStage)
        if (file != null && file.exists()) {
            settingsController.setOpenerBasePath(file.parentFile.absolutePath)
            open(file)
        }
    }

    private fun requestCloseTab(tab: Tab) {
        tabController.requestCloseTab(tab) {
            logger.debug { "Removing tab $tab from tabPane" }
            tabPane.tabs.remove(tab)
            logger.debug { "Removed tab $tab from tabPane" }
            tabController.closeTab(tab)
        }
    }

    // Draggable Tabs

    private fun addDragHandlers(tab: Tab?) {
        // move text to label graphic:
        if (tab != null) {
            if (tab.text != null && tab.text.isNotEmpty()) {
                val label = Label(tab.text, tab.graphic)
                label.style = "-fx-text-fill: #fafafa"
                tab.text = null
                tab.graphic = label
            }
            val graphic = tab.graphic
            graphic.onDragDetected = EventHandler {
                val dragboard = graphic.startDragAndDrop(TransferMode.MOVE)
                val content = ClipboardContent()
                // dragboard must have some content, but we need it to be a Tab, which isn't supported
                // So we store it in a local variable and just put arbitrary content in the dragbaord:
                content.putString(draggingId)
                dragboard.setContent(content)
                dragboard.dragView = graphic.snapshot(null, null)
                currentDraggingTab = tab
            }
            graphic.onDragOver = EventHandler { e: DragEvent ->
                if (draggingId == e.dragboard.string && currentDraggingTab != null && currentDraggingTab!!.graphic !== graphic) {
                    e.acceptTransferModes(TransferMode.MOVE)
                }
            }
            graphic.onDragDropped = EventHandler { e: DragEvent ->
                if (draggingId == e.dragboard.string && currentDraggingTab != null && currentDraggingTab!!.graphic !== graphic) {
                    val index = tab.tabPane.tabs.indexOf(tab)
                    currentDraggingTab!!.tabPane.tabs.remove(currentDraggingTab)
                    tab.tabPane.tabs.add(index, currentDraggingTab)
                    currentDraggingTab!!.tabPane.selectionModel.select(currentDraggingTab)
                }
            }
            graphic.onDragDone = EventHandler { e: DragEvent? -> currentDraggingTab = null }
        }
    }

    private fun removeDragHandlers(tab: Tab?) {
        if (tab != null) {
            tab.graphic.onDragDetected = null
            tab.graphic.onDragOver = null
            tab.graphic.onDragDropped = null
            tab.graphic.onDragDone = null
        }
    }

}
