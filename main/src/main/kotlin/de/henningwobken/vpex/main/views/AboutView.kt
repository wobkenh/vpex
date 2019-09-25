package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.controllers.InternalResourceController
import de.henningwobken.vpex.main.model.InternalResource
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.util.Duration
import tornadofx.*
import java.awt.Desktop
import java.net.URI

class AboutView : View("My View") {
    private val internalResourceController by inject<InternalResourceController>()
    private val versionNumber: String

    init {
        versionNumber = internalResourceController.getAsString(InternalResource.VERSION)
    }

    override val root = borderpane {
        top = button("Back") {
            action {
                replaceWith<MainView>(ViewTransition.Metro(Duration.millis(500.0)))
            }
        }
        center = vbox(25) {
            paddingAll = 50
            alignment = Pos.CENTER
            imageview(internalResourceController.getAsImage(InternalResource.LOGO))
            label("Version $versionNumber ALPHA")
            label("")
            label("Contributors")
            label("Henning Wobken (henning.wobken@simplex24.de)")
            label("")
            label("Sources")
            hyperlink("Github").action {
                Thread {
                    try {
                        Desktop.getDesktop().browse(URI("https://github.com/wobkenh/vpex"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            }
            label("")
            hbox(50) {
                alignment = Pos.TOP_CENTER
                vbox(25) {
                    alignment = Pos.TOP_CENTER
                    cursor = Cursor.HAND
                    label("You like VPEX? Consider donating:")
                    imageview(internalResourceController.getAsImage(InternalResource.DONATE_BUTTON)) {
                        setOnMouseClicked {
                            Thread {
                                try {
                                    Desktop.getDesktop().browse(URI("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=8XUTLRVVU2TM8&source=url"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }.start()
                        }
                    }
                }
                vbox(25) {
                    alignment = Pos.TOP_CENTER
                    label("or scan the following QR code:")
                    imageview(internalResourceController.getAsImage(InternalResource.DONATE_QR))
                }
            }


        }
    }
}
