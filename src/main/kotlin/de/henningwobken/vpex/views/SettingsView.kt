package de.henningwobken.vpex.views

import de.henningwobken.vpex.controllers.SettingsController
import javafx.util.Duration
import tornadofx.*

class SettingsView : View("VPEX - Einstellungen") {
    private val settingsController: SettingsController by inject()

    override val root = borderpane {
        center = form {
            fieldset("Paths") {
                field("Schema Root Location") {
                    textfield()
                }
                field("File Opener Start Location") {
                    textfield()
                }
            }
        }
        bottom = hbox(50) {
            paddingAll = 25.0
            button("Abbrechen").action {
                backToMainScreen()
            }
            button("Speichern").action {
                saveSettings()
            }
        }
    }

    init {
        val settings = settingsController.getSettings()
        // TODO: Set Form values via property bindings
    }

    private fun saveSettings() {

        backToMainScreen()
    }

    private fun backToMainScreen() {
        replaceWith<MainView>(ViewTransition.Metro(Duration.millis(500.0)))
    }
}
