package de.henningwobken.vpex

import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import tornadofx.*

class Styles : Stylesheet() {
    companion object {
        val unchanged by cssclass()
        val changed by cssclass()
    }

    init {
        unchanged {
            textFill = Color.WHITE
            backgroundColor += Color.rgb(31, 30, 47)
            fontWeight = FontWeight.BOLD
        }
        changed {
            textFill = Color.WHITE
            backgroundColor += Color.INDIANRED
            fontWeight = FontWeight.BOLD
        }
    }
}
