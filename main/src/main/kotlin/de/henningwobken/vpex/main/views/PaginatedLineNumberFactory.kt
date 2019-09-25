package de.henningwobken.vpex.main.views

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontPosture
import org.fxmisc.richtext.GenericStyledArea
import org.reactfx.collection.LiveList
import org.reactfx.value.Val
import java.util.function.IntFunction
import kotlin.math.floor
import kotlin.math.log10

/**
 * Vpex offers pagination. Pagination means that the editor might start at line x.
 * To account for that, we need to add the starting line for the page to each line number
 * @param area code area
 * @param calculateStartingLine function that calculates the lines before the current page
 */
class PaginatedLineNumberFactory(
        private val area: GenericStyledArea<*, *, *>,
        private val calculateStartingLine: () -> Int
) : IntFunction<Node> {
    private val nParagraphs: Val<Int> = LiveList.sizeOf(area.paragraphs)
    private val format: IntFunction<String> = IntFunction { digits: Int -> "%1$" + digits + "s" }

    override fun apply(idx: Int): Node {
        val startingLine = calculateStartingLine()
        val formatted = this.nParagraphs.map { n -> this.format(startingLine + idx + 1, n!!) }
        val lineNo = Label()
        lineNo.font = DEFAULT_FONT
        lineNo.background = DEFAULT_BACKGROUND
        lineNo.textFill = DEFAULT_TEXT_FILL
        lineNo.padding = DEFAULT_INSETS
        lineNo.alignment = Pos.TOP_RIGHT
        lineNo.styleClass.add("lineno")
        lineNo.textProperty().bind(formatted.conditionOnShowing(lineNo))
        return lineNo
    }

    private fun format(x: Int, max: Int): String {
        val digits = floor(log10(max.toDouble())).toInt() + 1
        return String.format(this.format.apply(digits) as String, x)
    }

    companion object {
        private val DEFAULT_INSETS = Insets(0.0, 5.0, 0.0, 5.0)
        private val DEFAULT_TEXT_FILL = Color.web("#666")
        private val DEFAULT_FONT: Font = Font.font("monospace", FontPosture.ITALIC, 13.0)
        private val DEFAULT_BACKGROUND: Background = Background(BackgroundFill(Color.web("#ddd"), null as CornerRadii?, null as Insets?))
    }
}