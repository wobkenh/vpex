package de.henningwobken.vpex.views

import de.henningwobken.vpex.Styles
import javafx.scene.control.Alert.AlertType.INFORMATION
import tornadofx.*

class MainView : View("Hello TornadoFX") {
    override val root = borderpane {
        addClass(Styles.welcomeScreen)
        top {
            menubar {
                menu ("File") {
                    item("Open").action {
                        println("Opened")
                    }
                    item("Save", "Shortcut+S").action {
                        println("Saved!")
                    }
                    item("Save as", "Shortcut+Shift+S").action {
                        println("Saved as!")
                    }
                }
                menu ("Settings") {
                    item("Settings")
                }
            }
        }
        center {
            stackpane {
                addClass(Styles.content)
                button("Click me not") {
                    setOnAction {
                        alert(INFORMATION, "Well done!", "You clicked me!")
                    }
                }
            }
        }
    }
}