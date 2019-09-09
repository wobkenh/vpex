package de.henningwobken.vpex.views

import de.henningwobken.vpex.controllers.SettingsController
import de.henningwobken.vpex.model.Settings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.stage.DirectoryChooser
import javafx.util.Duration
import javafx.util.StringConverter
import tornadofx.*
import java.util.*

class SettingsView : View("VPEX - Einstellungen") {
    private val settingsController: SettingsController by inject()
    private val wrapProperty = SimpleBooleanProperty()
    private val schemaBasePathProperty = SimpleStringProperty()
    private val openerBasePathProperty = SimpleStringProperty()
    private val prettyPrintIndentProperty = SimpleIntegerProperty()
    private val localeProperty = SimpleStringProperty()

    init {
        val settings = settingsController.getSettings()
        wrapProperty.set(settings.wrapText)
        schemaBasePathProperty.set(settings.schemaBasePath)
        openerBasePathProperty.set(settings.openerBasePath)
        prettyPrintIndentProperty.set(settings.prettyPrintIndent)
        localeProperty.set(settings.locale.toLanguageTag())

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
                Locale.forLanguageTag(localeProperty.get())
        )
        settingsController.saveSettings(settings)
        backToMainScreen()
    }

    private fun backToMainScreen() {
        replaceWith<MainView>(ViewTransition.Metro(Duration.millis(500.0)))
    }
}
