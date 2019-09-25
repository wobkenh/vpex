package de.henningwobken.vpex.updater

import de.henningwobken.vpex.updater.views.UpdaterView
import javafx.application.Application
import javafx.scene.image.Image
import javafx.stage.Stage
import javafx.stage.StageStyle
import tornadofx.*
import java.io.File

class VpexUpdater : App(
        Image(VpexUpdater::class.java.classLoader.getResourceAsStream("vpex_icon.png")),
        UpdaterView::class
) {

    override fun start(stage: Stage) {
        stage.initStyle(StageStyle.UNDECORATED)
        super.start(stage)
        stage.isMaximized = false
        stage.isAlwaysOnTop = true
        stage.isResizable = false
        stage.width = 400.0
        stage.height = 225.0
        find<UpdaterView>().copy(parameters.raw)

    }

    // Hot Reloading of Components seems to break the application on ubuntu/javafx8
    //    override fun init() {
    //        reloadViewsOnFocus()
    //    }
}

lateinit var oldJar: File
lateinit var newJar: File

/**
 * The main method is needed to support the mvn jfx:run goal.
 */
fun main(args: Array<String>) {
    Application.launch(VpexUpdater::class.java, *args)
}