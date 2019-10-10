package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.Styles
import de.henningwobken.vpex.main.controllers.InternalResourceController
import de.henningwobken.vpex.main.model.InternalResource
import de.henningwobken.vpex.main.xml.SchemaDescriptor
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Pos
import javafx.scene.Node
import tornadofx.*
import java.awt.Desktop
import java.io.File

class SchemaChooserFragment : Fragment("Schema Chooser") {

    private val internalResourceController by inject<InternalResourceController>()

    private val filesCount = SimpleIntegerProperty(0)
    private lateinit var addChild: (node: Node) -> Unit
    private lateinit var callback: (file: File?) -> Unit
    private var schemaDescriptorProperty = SimpleObjectProperty<SchemaDescriptor>()


    override val root = borderpane {
        prefWidth = 1000.0
        prefHeight = 500.0
        top = vbox {
            addClass(Styles.primaryHeader)
            label(schemaDescriptorProperty.stringBinding { "Searching for namespace URI ${it?.namespaceURI} and system id ${it?.systemId}" })
            label(filesCount.stringBinding { "Found $it possible schemes. Choose one." })
        }

        center = scrollpane {
            isFitToWidth = true
            vbox {
                addChild = {
                    children.add(it)
                }
            }
        }
        bottom = hbox {
            paddingAll = 15.0
            label {
                ViewHelper.fillHorizontal(this)
            }
            button("Abort") {
                action {
                    callback(null)
                }
            }
        }
    }

    fun chooseFile(files: List<File>, schemaDescriptor: SchemaDescriptor, callback: (file: File?) -> Unit) {
        filesCount.set(files.size)
        this.schemaDescriptorProperty.set(schemaDescriptor)
        this.callback = callback
        for (file in files) {
            val fileUi = hbox(25) {
                paddingTop = 15
                paddingHorizontal = 25
                alignment = Pos.CENTER
                hbox(10) {
                    ViewHelper.fillHorizontal(this)
                    addClass(Styles.card)
                    paddingAll = 10.0
                    hbox {
                        alignment = Pos.CENTER
                        prefWidth = 48.0
                        maxWidth = 48.0
                        label {
                            graphic = internalResourceController.getAsSvg(InternalResource.FILE_ICON)
                            graphic.scaleX = 2.0
                            graphic.scaleY = 2.0
                        }
                    }
                    vbox {
                        ViewHelper.fillHorizontal(this)
                        textfield(file.name) {
                            style = "-fx-font-weight: bold;"
                            addClass(Styles.selectable)
                        }
                        textfield("In: ${file.absolutePath}") {
                            addClass(Styles.selectable)
                        }
                    }
                }
                button {
                    graphic = internalResourceController.getAsSvg(InternalResource.OPEN_IN_NEW_ICON, "#FFFFFF")
                    graphic.scaleX = 1.5
                    graphic.scaleY = 1.5
                    prefWidth = 55.0
                    prefHeight = 55.0
                    action {
                        Thread {
                            Desktop.getDesktop().open(file.parentFile)
                        }.start()
                    }
                }
                button {
                    graphic = internalResourceController.getAsSvg(InternalResource.SEND_ICON, "#FFFFFF")
                    graphic.scaleX = 1.5
                    graphic.scaleY = 1.5
                    prefWidth = 55.0
                    prefHeight = 55.0
                    action {
                        callback(file)
                    }
                }
            }
            addChild(fileUi)
        }
    }

}
