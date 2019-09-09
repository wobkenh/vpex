package de.henningwobken.vpex.views

import de.henningwobken.vpex.controllers.SettingsController
import de.henningwobken.vpex.model.Settings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.stage.DirectoryChooser
import javafx.util.Duration
import tornadofx.*

class SettingsView : View("VPEX - Einstellungen") {
    private val settingsController: SettingsController by inject()
    private val wrapProperty = SimpleBooleanProperty()
    private val schemaBasePathProperty = SimpleStringProperty()
    private val openerBasePathProperty = SimpleStringProperty()

    init {
        val settings = settingsController.getSettings()
        wrapProperty.set(settings.wrapText)
        schemaBasePathProperty.set(settings.schemaBasePath)
        openerBasePathProperty.set(settings.openerBasePath)
    }

    override val root = borderpane {
        center = form {
            paddingAll = 50
            fieldset("Appearance") {
                field("Wrap text") {
                    checkbox("", wrapProperty)
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
                    textfield()
                }
            }
        }
        bottom = hbox(50) {
            paddingAll = 50
            button("Abbrechen").action {
                backToMainScreen()
            }
            button("Speichern").action {
                saveSettings()
            }
        }
    }

    private fun saveSettings() {
        val settings = Settings(
                openerBasePathProperty.get(),
                schemaBasePathProperty.get(),
                wrapProperty.get()
        )
        settingsController.saveSettings(settings)
        backToMainScreen()
    }

    private fun backToMainScreen() {
        replaceWith<MainView>(ViewTransition.Metro(Duration.millis(500.0)))
    }
}
