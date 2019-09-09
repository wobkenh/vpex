package de.henningwobken.vpex

import de.henningwobken.vpex.controllers.InternalResourceController
import de.henningwobken.vpex.model.InternalResource
import de.henningwobken.vpex.views.MainView
import javafx.application.Application
import javafx.stage.Stage
import tornadofx.*

class Vpex : App(MainView::class, InternalWindow.Styles::class, Styles::class) {
    private val internalResourceController: InternalResourceController by inject()

    override fun start(stage: Stage) {
        super.start(stage)
        stage.isMaximized = true
        stage.icons.add(internalResourceController.getAsImage(InternalResource.ICON));
    }

    // Hot Reloading of Components seems to break the application on ubuntu/javafx8
    //    override fun init() {
    //        reloadViewsOnFocus()
    //    }
}

/**
 * The main method is needed to support the mvn jfx:run goal.
 */
fun main(args: Array<String>) {
    Application.launch(Vpex::class.java, *args)
}