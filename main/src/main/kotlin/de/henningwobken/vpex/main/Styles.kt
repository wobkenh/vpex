package de.henningwobken.vpex.main

import de.henningwobken.vpex.main.controllers.InternalResourceController
import de.henningwobken.vpex.main.model.InternalResource
import javafx.geometry.Pos
import javafx.geometry.Side
import javafx.scene.Cursor
import javafx.scene.effect.DropShadow
import javafx.scene.layout.BackgroundPosition
import javafx.scene.layout.BackgroundRepeat
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import tornadofx.*

class Styles : Stylesheet() {

    private val internalResourceController = InternalResourceController()

    companion object {
        // classes
        val unchanged by cssclass()
        val changed by cssclass()
        val warning by cssclass()
        val primaryHeader by cssclass()
        val button by cssclass()
        val card by cssclass()
        val selectable by cssclass()
        val selectableMultiline by cssclass()
        val councilBackground by cssclass()

        // effects
        val dropShadow: DropShadow = {
            val dropShadow = DropShadow()
            dropShadow.radius = 3.0
            dropShadow.offsetX = 1.0
            dropShadow.offsetY = 2.0
            dropShadow.color = Color.color(0.0, 0.0, 0.0, 0.2)
            // Alternative drop shadow color:
            // dropShadow.color = Color.color(0.4, 0.5, 0.5)
            dropShadow
        }()


        // colors
        val dangerColor = c("#AF8016")
        val errorColor = c("#A1140A")
        val primaryColor = c("#1F1E2F")
        val secondaryColor = c("#0A92BF")
        val secondaryColorDarker = c("#0A7BA5")
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
        warning {
            textFill = Color.WHITE
            backgroundColor += dangerColor
            fontWeight = FontWeight.BOLD
            cursor = Cursor.HAND
        }
        primaryHeader {
            backgroundColor += primaryColor
            fontWeight = FontWeight.BOLD
            padding = box(10.px)
            alignment = Pos.CENTER
            label {
                textFill = Color.WHITE
            }
        }
        button {
            backgroundColor += secondaryColor
            fontWeight = FontWeight.BOLD
            textFill = Color.WHITE
            cursor = Cursor.HAND
            effect = dropShadow
            and(pressed) {
                backgroundColor += secondaryColorDarker
            }
        }
        card {
            backgroundColor += Color.WHITE
            effect = dropShadow
        }
        selectable {
            backgroundColor += Color.TRANSPARENT
            backgroundInsets += box(0.px)
        }
        selectableMultiline {
            backgroundColor += Color.TRANSPARENT
            backgroundInsets += box(0.px)
            focusColor = Color.TRANSPARENT
            textBoxBorder = Color.TRANSPARENT
        }
        councilBackground {
            backgroundImage += internalResourceController.getAsURI(InternalResource.COUNCIL_IMG)
            backgroundRepeat += Pair(BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT)
            backgroundPosition += BackgroundPosition(Side.LEFT, 0.5, true, Side.BOTTOM, 0.0, true) // bottom center
        }

    }
}
