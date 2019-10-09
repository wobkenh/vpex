package de.henningwobken.vpex.main.views

import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import tornadofx.*

object ViewHelper {
    fun fillHorizontal(region: Region) {
        region.hgrow = Priority.ALWAYS
        region.maxWidth = Int.MAX_VALUE.toDouble()
    }

    fun fillVertical(region: Region) {
        region.vgrow = Priority.ALWAYS
        region.maxHeight = Int.MAX_VALUE.toDouble()
    }
}
