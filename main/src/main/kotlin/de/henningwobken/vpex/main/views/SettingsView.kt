package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.controllers.SettingsController
import de.henningwobken.vpex.main.controllers.UpdateController
import de.henningwobken.vpex.main.model.Settings
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.util.Duration
import javafx.util.StringConverter
import tornadofx.*
import java.io.File
import java.util.*

class SettingsView : View("VPEX - Einstellungen") {
    private val updateController: UpdateController by inject()
    private val settingsController: SettingsController by inject()
    private val wrapProperty = SimpleBooleanProperty()
    private val schemaBasePathList = mutableListOf<String>()
    private val openerBasePathProperty = SimpleStringProperty()
    private val prettyPrintIndentProperty = SimpleIntegerProperty()
    private val localeProperty = SimpleStringProperty()
    private val paginationProperty = SimpleBooleanProperty()
    private val pageSizeProperty = SimpleIntegerProperty()
    private val paginationThresholdProperty = SimpleIntegerProperty()
    private val autoUpdateProperty = SimpleBooleanProperty()
    private val proxyHostProperty = SimpleStringProperty()
    private val proxyPortProperty = SimpleStringProperty()
    private val targetVersionProperty = SimpleStringProperty()
    private val progressProperty = SimpleDoubleProperty(-1.0)
    private val memoryIndicatorProperty = SimpleBooleanProperty()
    private val saveLockProperty = SimpleBooleanProperty()
    private val diskPaginationProperty = SimpleBooleanProperty()
    private val diskPaginationThresholdProperty = SimpleIntegerProperty()
    private val trustStoreProperty = SimpleStringProperty()
    private val trustStorePasswordProperty = SimpleStringProperty()
    private val insecureProperty = SimpleBooleanProperty()

    init {
        val settings = settingsController.getSettings()
        wrapProperty.set(settings.wrapText)
        schemaBasePathList.addAll(settings.schemaBasePathList)
        prettyPrintIndentProperty.set(settings.prettyPrintIndent)
        localeProperty.set(settings.locale.toLanguageTag())
        paginationProperty.set(settings.pagination)
        pageSizeProperty.set(settings.pageSize)
        paginationThresholdProperty.set(settings.paginationThreshold)
        autoUpdateProperty.set(settings.autoUpdate)
        proxyHostProperty.set(settings.proxyHost)
        proxyPortProperty.set(settings.proxyPort?.toString() ?: "")
        memoryIndicatorProperty.set(settings.memoryIndicator)
        saveLockProperty.set(settings.saveLock)
        diskPaginationProperty.set(settings.diskPagination)
        diskPaginationThresholdProperty.set(settings.diskPaginationThreshold)
        trustStoreProperty.set(settings.trustStore)
        trustStorePasswordProperty.set(settings.trustStorePassword)
        insecureProperty.set(settings.insecure)
    }

    override val root = borderpane {
        center = scrollpane {
            form {
                paddingAll = 50

                fieldset("Appearance") {
                    field("Wrap text") {
                        checkbox("", wrapProperty)
                    }
                    field("Number format") {
                        combobox(localeProperty, listOf("de", "en", "fr")) {
                            converter = object : StringConverter<String>() {
                                override fun toString(languageTag: String): String {
                                    return Locale.forLanguageTag(languageTag).displayName
                                }

                                override fun fromString(displayName: String): String {
                                    return when (displayName) {
                                        "English" -> Locale.ENGLISH.toLanguageTag()
                                        "German" -> Locale.GERMAN.toLanguageTag()
                                        "French" -> Locale.FRENCH.toLanguageTag()
                                        else -> Locale.ENGLISH.toLanguageTag()
                                    }
                                }
                            }
                        }
                    }
                    field("Memory Indicator") {
                        checkbox("", memoryIndicatorProperty)
                    }
                }
                fieldset("Files") {
                    field("Schema Root Location") {
                        vbox {
                            val basePathBox = vbox {}
                            val addBasePathChild = { basePath: String ->
                                basePathBox.children.add(hbox(10) {
                                    alignment = Pos.CENTER_LEFT
                                    paddingTop = 10
                                    val hbox = this
                                    button("Delete") {
                                        action {
                                            schemaBasePathList.remove(basePath)
                                            basePathBox.children.remove(hbox)
                                        }
                                    }
                                    label(basePath)
                                })
                            }
                            for (schemaBasePath in schemaBasePathList) {
                                addBasePathChild(schemaBasePath)
                            }
                            hbox {
                                paddingTop = 10
                                button("Add") {
                                    action {
                                        val directoryChooser = DirectoryChooser()
                                        directoryChooser.title = "Schema Base Path"
                                        directoryChooser.initialDirectory = File(openerBasePathProperty.get())
                                        val file = directoryChooser.showDialog(FX.primaryStage)
                                        if (file != null) {
                                            schemaBasePathList.add(file.absolutePath)
                                            addBasePathChild(file.absolutePath)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    field("Lock save operations") {
                        tooltip("When save lock is activated, you will not be able to overwrite the file currently open. (Save as is still possible)")
                        checkbox("Disables 'save' in favor of 'save as'", saveLockProperty)
                    }
                }
                fieldset("Transformation") {
                    field("Pretty print indent") {
                        textfield(prettyPrintIndentProperty) {
                            prefWidth = 200.0
                            maxWidth = 200.0
                        }
                    }
                }
                fieldset("Pagination") {
                    field("Paginate large files") {
                        checkbox("", paginationProperty)
                    }
                    field("Page size") {
                        tooltip("Number of characters per page")
                        textfield(pageSizeProperty) {
                            prefWidth = 200.0
                            maxWidth = 200.0
                        }
                    }
                    field("Pagination threshold") {
                        tooltip("Minimum number of characters to activate pagination")
                        textfield(paginationThresholdProperty) {
                            prefWidth = 200.0
                            maxWidth = 200.0
                        }
                    }
                }
                fieldset("Paginate from disk mode") {
                    label("Context: Very large files can't be kept in memory. This mode always reads from disk.")
                    field("Paginate from disk") {
                        checkbox("", diskPaginationProperty)
                    }
                    field("Paginate from disk threshold") {
                        tooltip("Minimum filesize to activate paginate from disk")
                        textfield(diskPaginationThresholdProperty) {
                            prefWidth = 100.0
                            maxWidth = 100.0
                        }
                        label("MiB")
                    }
                }
                fieldset("Updates") {
                    field("Check for updates at startup") {
                        checkbox("", autoUpdateProperty)
                    }
                    field("Proxy") {
                        textfield(proxyHostProperty) {
                            prefWidth = 200.0
                            maxWidth = 200.0
                        }
                        textfield(proxyPortProperty) {
                            prefWidth = 50.0
                            maxWidth = 50.0
                        }
                    }
                    field("Truststore") {
                        button("Change") {
                            action {
                                val fileChooser = FileChooser()
                                fileChooser.title = "Truststore file"
                                val file = fileChooser.showOpenDialog(FX.primaryStage)
                                if (file != null) {
                                    trustStoreProperty.set(file.absolutePath)
                                }
                            }
                        }
                        button("Delete") {
                            action {
                                trustStoreProperty.set("")
                            }
                        }
                        label(trustStoreProperty)
                    }
                    field("Truststore password") {
                        passwordfield(trustStorePasswordProperty) {
                            prefWidth = 200.0
                            maxWidth = 200.0
                        }
                    }
                    field("Use insecure connection / Ignore SSL") {
                        checkbox("WARNING: This is a major security risk.", insecureProperty)
                    }
                    field("Version") {
                        label(updateController.currentVersion)
                    }
                    field("Available Versions") {
                        combobox(targetVersionProperty, updateController.availableVersions)
                        button("Change Version").action {
                            progressProperty.set(0.0)
                            updateController.downloadUpdate({ progress, max ->
                                Platform.runLater {
                                    progressProperty.set(progress / (max * 1.0))
                                }
                            }, {
                                Platform.runLater {
                                    progressProperty.set(-1.0)
                                    updateController.applyUpdate()
                                }
                            }, targetVersionProperty.get())
                        }
                    }
                    field("Progress") {
                        removeWhen(progressProperty.lessThan(0))
                        progressbar(progressProperty) {
                        }
                    }
                }
            }
        }

        bottom = hbox(50) {
            paddingLeft = 50
            paddingVertical = 30
            button("Cancel").action {
                backToMainScreen()
            }
            button("Save").action {
                saveSettings()
            }
        }
    }

    private fun saveSettings() {
        val settings = Settings(
                schemaBasePathList,
                wrapProperty.get(),
                prettyPrintIndentProperty.get(),
                Locale.forLanguageTag(localeProperty.get()),
                paginationProperty.get(),
                pageSizeProperty.get(),
                paginationThresholdProperty.get(),
                autoUpdateProperty.get(),
                proxyHostProperty.get(),
                proxyPortProperty.get().toIntOrNull(),
                memoryIndicatorProperty.get(),
                saveLockProperty.get(),
                diskPaginationProperty.get(),
                diskPaginationThresholdProperty.get(),
                trustStoreProperty.get(),
                trustStorePasswordProperty.get(),
                insecureProperty.get()
        )
        settingsController.saveSettings(settings)
        backToMainScreen()
    }

    private fun backToMainScreen() {
        replaceWith<MainView>(ViewTransition.Metro(Duration.millis(500.0)))
    }
}
