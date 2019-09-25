package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.controllers.SettingsController
import de.henningwobken.vpex.main.controllers.UpdateController
import de.henningwobken.vpex.main.model.Settings
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.stage.DirectoryChooser
import javafx.util.Duration
import javafx.util.StringConverter
import tornadofx.*
import java.util.*

class SettingsView : View("VPEX - Einstellungen") {
    private val updateController: UpdateController by inject()
    private val settingsController: SettingsController by inject()
    private val wrapProperty = SimpleBooleanProperty()
    private val schemaBasePathProperty = SimpleStringProperty()
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

    init {
        val settings = settingsController.getSettings()
        wrapProperty.set(settings.wrapText)
        schemaBasePathProperty.set(settings.schemaBasePath)
        openerBasePathProperty.set(settings.openerBasePath)
        prettyPrintIndentProperty.set(settings.prettyPrintIndent)
        localeProperty.set(settings.locale.toLanguageTag())
        paginationProperty.set(settings.pagination)
        pageSizeProperty.set(settings.pageSize)
        paginationThresholdProperty.set(settings.paginationThreshold)
        autoUpdateProperty.set(settings.autoUpdate)
        proxyHostProperty.set(settings.proxyHost)
        proxyPortProperty.set(settings.proxyPort?.toString() ?: "")
        memoryIndicatorProperty.set(settings.memoryIndicator)
    }

    override val root = borderpane {
        center = form {
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
            fieldset("Paths") {
                field("Schema Root Location") {
                    button("Change") {
                        action {
                            val directoryChooser = DirectoryChooser()
                            directoryChooser.title = "Schema Base Path"
                            val file = directoryChooser.showDialog(FX.primaryStage)
                            if (file != null) {
                                schemaBasePathProperty.set(file.absolutePath)
                            }
                        }
                    }
                    label(schemaBasePathProperty)
                }
                field("File Opener Start Location") {
                    button("Change") {
                        action {
                            val directoryChooser = DirectoryChooser()
                            directoryChooser.title = "File Opener Base Path"
                            val file = directoryChooser.showDialog(FX.primaryStage)
                            if (file != null) {
                                openerBasePathProperty.set(file.absolutePath)
                            }
                        }
                    }
                    label(openerBasePathProperty)
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
        bottom = hbox(50) {
            paddingAll = 50
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
                openerBasePathProperty.get(),
                schemaBasePathProperty.get(),
                wrapProperty.get(),
                prettyPrintIndentProperty.get(),
                Locale.forLanguageTag(localeProperty.get()),
                paginationProperty.get(),
                pageSizeProperty.get(),
                paginationThresholdProperty.get(),
                autoUpdateProperty.get(),
                proxyHostProperty.get(),
                proxyPortProperty.get().toIntOrNull(),
                memoryIndicatorProperty.get()
        )
        settingsController.saveSettings(settings)
        backToMainScreen()
    }

    private fun backToMainScreen() {
        replaceWith<MainView>(ViewTransition.Metro(Duration.millis(500.0)))
    }
}
