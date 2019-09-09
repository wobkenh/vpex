package de.henningwobken.vpex.views

import de.henningwobken.vpex.controllers.InternalResourceController
import de.henningwobken.vpex.model.InternalResource
import javafx.geometry.Pos
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
        }
    }
}
