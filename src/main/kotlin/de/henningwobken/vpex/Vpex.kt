package de.henningwobken.vpex

import de.henningwobken.vpex.views.MainView
import javafx.application.Application
import javafx.stage.Stage
import tornadofx.*

class Vpex : App(MainView::class, InternalWindow.Styles::class) {
    override fun start(stage: Stage) {
        super.start(stage)
        stage.isMaximized = true
    }

    override fun init() {
        reloadViewsOnFocus()
    }
}

/**
 * The main method is needed to support the mvn jfx:run goal.
 */
fun main(args: Array<String>) {
    Application.launch(Vpex::class.java, *args)
}