package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.Styles
import de.henningwobken.vpex.main.controllers.InternalResourceController
import de.henningwobken.vpex.main.controllers.MemoryMonitor
import de.henningwobken.vpex.main.controllers.SettingsController
import de.henningwobken.vpex.main.controllers.VpexExecutor
import de.henningwobken.vpex.main.model.DisplayMode
import de.henningwobken.vpex.main.model.InternalResource
import javafx.beans.property.*
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.Priority
import mu.KotlinLogging
import tornadofx.*
import java.text.NumberFormat
import kotlin.math.round

class StatusBarView : View() {

    private val logger = KotlinLogging.logger {}
    private val internalResourceController: InternalResourceController by inject()
    private val vpexExecutor: VpexExecutor by inject()
    private val settingsController: SettingsController by inject()
    private val memoryMonitor: MemoryMonitor by inject()

    // General

    private var numberFormat = NumberFormat.getInstance(settingsController.getSettings().locale)
    private val showMonitorThread = SimpleBooleanProperty(false)


    // Main-specific

    private val downloadProgressProperty = SimpleDoubleProperty(-1.0)


    // Tab-specific

    // General
    private val isDirty: BooleanProperty = SimpleBooleanProperty(false)
    private val saveLockProperty = SimpleBooleanProperty(false)
    private val statusTextProperty = SimpleStringProperty("")
    private val fileProgressProperty = SimpleDoubleProperty(-1.0)
    private val displayMode = SimpleObjectProperty<DisplayMode>(DisplayMode.PLAIN)
    private var lineCount = SimpleIntegerProperty(0)
    private val charCountProperty = SimpleIntegerProperty(0)
    // Functions
    private val moveToPage = SimpleObjectProperty<(Int) -> Unit>()
    // Pagination
    private val page = SimpleIntegerProperty(1)
    private val pageDisplayProperty = SimpleIntegerProperty(1)
    private var maxPage = SimpleIntegerProperty(0)
    // Selection and Cursor
    private val selectionLength = SimpleIntegerProperty(0)
    private val cursorLine = SimpleIntegerProperty(0)
    private val cursorColumn = SimpleIntegerProperty(0)

    override val root =
            hbox(10) {
                hgrow = Priority.ALWAYS
                alignment = Pos.CENTER

                /*
                    Dirty Label
                */

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

                /*
                    Save Lock
                */

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

                /*
                    Status
                */

                label(statusTextProperty)
                button("Cancel") {
                    removeWhen { vpexExecutor.isRunning.not() }
                }.action {
                    vpexExecutor.cancel()
                    statusTextProperty.set("")
                    fileProgressProperty.set(-1.0)
                }
                progressbar(downloadProgressProperty) {
                    removeWhen(downloadProgressProperty.lessThan(0))
                }
                progressbar(fileProgressProperty) {
                    removeWhen(fileProgressProperty.lessThan(0))
                }

                /*
                    Disk Pagination Info
                 */
                label("Disk") {
                    removeWhen { displayMode.isNotEqualTo(DisplayMode.DISK_PAGINATION) }
                    paddingAll = 10.0
                    prefWidth = 60.0
                    addClass(Styles.warning)
                    val tooltipText = """
                        Disk Pagination mode is activated. Only the page you are viewing will be kept in memory.
                        This means:
                        - Changes need to be saved on page switch
                        - You should never overwrite the current file when saving
                        - The result of "Replace All" needs to be saved to a new file
                        - "Find All"/"Replace All" will work, but might be slow
                    """.trimIndent()
                    tooltip(tooltipText)
                    tooltip.isAutoHide = false
                }

                /*
                    Pagination
                */

                hbox(10) {
                    alignment = Pos.CENTER
                    removeWhen(displayMode.isEqualTo(DisplayMode.PLAIN))
                    button("<<") {
                        disableWhen {
                            page.isEqualTo(1)
                        }
                    }.action {
                        moveToPage.get()(page.get() - 1)
                    }
                    hbox(5) {
                        alignment = Pos.CENTER
                        textfield(pageDisplayProperty) {
                            page.onChange { pageDisplayProperty.set(page.get()) }
                            prefWidth = 60.0
                            maxWidth = 60.0
                        }.action {
                            val enteredPage = pageDisplayProperty.get()
                            if (enteredPage < 1 || enteredPage > maxPage.get()) {
                                pageDisplayProperty.set(page.get())
                            } else {
                                moveToPage.get()(pageDisplayProperty.get())
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
                        moveToPage.get()(page.get() + 1)
                    }
                }

                /*
                     Character Stats
                 */

                hbox(10) {
                    hbox(5) {
                        removeWhen { selectionLength.eq(0) }
                        alignment = Pos.CENTER
                        label("Selected:")
                        label(selectionLength.stringBinding {
                            numberFormat.format(it)
                        })
                        label("chars")
                    }
                    hbox(5) {
                        alignment = Pos.CENTER
                        label("Position:")
                        label(cursorLine.stringBinding(cursorColumn) {
                            "${numberFormat.format(cursorLine.get())}:${numberFormat.format(cursorColumn.get())}"
                        })
                    }
                    hbox(5) {
                        alignment = Pos.CENTER
                        label("Lines:")
                        label(lineCount.stringBinding {
                            numberFormat.format(it)
                        })
                    }
                    hbox(5) {
                        alignment = Pos.CENTER
                        label("Chars:")
                        label(charCountProperty.stringBinding {
                            numberFormat.format(it)
                        })
                    }
                }

                /*
                     Memory Label
                 */

                var memoryLabel: Label? = null
                label(memoryMonitor.allocatedMemory.stringBinding {
                    val allocatedMemory = memoryMonitor.allocatedMemory
                    val reservedMemory = memoryMonitor.reservedMemory
                    val maxMemory = memoryMonitor.maxMemory
                    if (memoryLabel != null) {
                        val percentAllocated = round((allocatedMemory.get() / (maxMemory * 1.0)) * 100)
                        val percentReserved = round((reservedMemory.get() / (maxMemory * 1.0)) * 100)
                        memoryLabel!!.style = "-fx-background-color: linear-gradient(to right, #0A92BF $percentAllocated%, #0ABFEE $percentAllocated%, #0ABFEE $percentReserved%, #eee $percentReserved%)"
                    }
                    "${it}MiB of ${maxMemory}MiB"
                }) {
                    paddingHorizontal = 5
                    isFillHeight = true
                    maxHeight = Double.MAX_VALUE
                    removeWhen(showMonitorThread.not())
                    memoryLabel = this
                }

            }

    init {
        settingsController.settingsProperty.onChange {
            updateSettings()
        }
        updateSettings()
    }

    private fun updateSettings() {
        numberFormat = NumberFormat.getInstance(settingsController.getSettings().locale)
        showMonitorThread.set(settingsController.getSettings().memoryIndicator)
    }


    fun bind(tabView: TabView) {
        logger.debug { "Binding to tab view for file ${tabView.getFile()?.absolutePath}" }
        isDirty.bind(tabView.isDirty)
        saveLockProperty.bind(tabView.saveLockProperty)
        fileProgressProperty.bind(tabView.fileProgressProperty)
        displayMode.bind(tabView.displayMode)
        lineCount.bind(tabView.lineCount)
        charCountProperty.bind(tabView.charCountProperty)
        moveToPage.set { page ->
            tabView.moveToPage(page)
        }
        page.bind(tabView.page)
        maxPage.bind(tabView.maxPage)
        selectionLength.bind(tabView.selectionLength)
        cursorLine.bind(tabView.cursorLine)
        cursorColumn.bind(tabView.cursorColumn)
    }

    fun unbind() {
        logger.debug { "Binding from any tab view" }
        isDirty.unbind()
        isDirty.set(false)
        saveLockProperty.unbind()
        fileProgressProperty.unbind()
        fileProgressProperty.set(-1.0)
        displayMode.unbind()
        displayMode.set(DisplayMode.PLAIN)
        lineCount.unbind()
        lineCount.set(0)
        charCountProperty.unbind()
        charCountProperty.set(0)
        moveToPage.unbind()
        page.unbind()
        page.set(0)
        maxPage.unbind()
        maxPage.set(0)
        selectionLength.unbind()
        selectionLength.set(0)
        cursorLine.unbind()
        cursorLine.set(0)
        cursorColumn.unbind()
        cursorColumn.set(0)
    }
}
