package de.henningwobken.vpex.main

import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.effect.DropShadow
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import tornadofx.*

class Styles : Stylesheet() {
    companion object {
        // classes
        val unchanged by cssclass()
        val changed by cssclass()
        val primaryHeader by cssclass()
        val button by cssclass()
        val card by cssclass()
        val selectable by cssclass()

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
        primaryHeader {
            backgroundColor += primaryColor
            fontWeight = FontWeight.BOLD
            padding = box(10.px)
            alignment = Pos.CENTER
        }
        button {
            backgroundColor += secondaryColor
            fontWeight = FontWeight.BOLD
            textFill = Color.WHITE
            cursor = Cursor.HAND
            effect = dropShadow
        }
        card {
            backgroundColor += Color.WHITE
            effect = dropShadow
        }
        selectable {
            backgroundColor += Color.TRANSPARENT
            backgroundInsets += box(0.px)
        }

    }
}
