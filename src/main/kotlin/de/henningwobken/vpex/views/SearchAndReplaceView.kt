package de.henningwobken.vpex.views

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.control.ToggleGroup
import tornadofx.*

class SearchAndReplaceView : View("Search and replace") {

    private val mainView: MainView by inject()
    private val findProperty = SimpleStringProperty("")
    private val replaceProperty = SimpleStringProperty("")
    private val interpreterType = ToggleGroup()
    private val matchCase = SimpleBooleanProperty(true)

    override val root = tabpane {
        tab("Replace") {
            vbox(10) {
                paddingAll = 10
                form {
                    fieldset {
                        field("Find") {
                            textfield(findProperty)
                        }
                        field("Replace") {
                            textfield(replaceProperty)
                        }
                    }
                }
                hbox(10) {
                    button("Find Next").action {
                        //                        findNext()
                    }
                    button("Count")
                    button("Find All")
                    button("Replace")
                }
                hbox(20) {
                    paddingAll = 10
                    vbox(10) {
                        paddingRight = 10
                        radiobutton("Normal", interpreterType)
                        radiobutton("Regex", interpreterType)
                    }
                    checkbox("Match case", matchCase)
                }
            }
        }
    }

//    private fun findNext() {
//        val offset = mainView.codeArea.caretPosition
//        val fullText = mainView.getFullText()
//        val index = fullText.indexOf(this.findProperty.get(), offset + 1)
//        if (index >= 0) {
//            mainView.moveToIndex(index)
//            // TODO: Select searched word / Move focus
////            Platform.runLater {
////                mainView.codeArea.select(offset, offset + this.findProperty.get().length)
////                mainView.codeArea.requestFocus()
////            }
//        } else {
//            alert(Alert.AlertType.WARNING, "Ouch", "I went a bit over the edge there. There are no more results.")
//        }
//    }

}