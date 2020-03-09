package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.controllers.ExpectedClass.*
import org.fxmisc.richtext.model.StyleSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tornadofx.*

internal class XmlSyntaxHighlightingControllerTest {
    private val scope = Scope(StringUtils(), XmlSyntaxHighlightingController())
    private val xmlSyntaxHighlightingController = FX.getComponents(scope)[XmlSyntaxHighlightingController::class] as XmlSyntaxHighlightingController

    @Test
    fun computeHighlighting() {
        val text = """
            <?xml version="1.0" encoding="UTF-8"?>
            <shiporder xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="shiporder.xsd" orderid="889923">
                <!-- The person to ship to -->
                <xsi:shipto>
                ${"\t"}<name>Ola Nordmann</name>
                    <description><![CDATA[
                        Our famous Belgian Waffles with plenty of real maple syrup. [] 5 < 6
                    ]]></description>
                </xsi:shipto>
            </shiporder>
        """.trimIndent()
        val result = xmlSyntaxHighlightingController.computeHighlighting(text)

        val expectedStyles = listOf(
                ExpectedStyle(2, TAGMARK),          // <?
                ExpectedStyle(3, ANYTAG),           // xml
                ExpectedStyle(1, NONE),             // " "
                ExpectedStyle(7, ATTRIBUTE),        // version
                ExpectedStyle(1, TAGMARK),          // =
                ExpectedStyle(5, AVALUE),           // "1.0"
                ExpectedStyle(1, NONE),             // " "
                ExpectedStyle(8, ATTRIBUTE),        // encoding
                ExpectedStyle(1, TAGMARK),          // =
                ExpectedStyle(7, AVALUE),           // "UTF-8"
                ExpectedStyle(2, TAGMARK),          // ?>
                ExpectedStyle(1, NONE),             // \n
                ExpectedStyle(1, TAGMARK),          // <
                ExpectedStyle(9, ANYTAG),           // shiporder
                ExpectedStyle(1, NONE),             // " "
                ExpectedStyle(6, ATTRIBUTENAMESPACE),// xmlns:
                ExpectedStyle(3, ATTRIBUTE),        // xsi
                ExpectedStyle(1, TAGMARK),          // =
                ExpectedStyle(43, AVALUE),          // "http://www.w3.org/2001/XMLSchema-instance"
                ExpectedStyle(1, NONE),             // " "
                ExpectedStyle(4, ATTRIBUTENAMESPACE),// xsi:
                ExpectedStyle(25, ATTRIBUTE),       // noNamespaceSchemaLocation
                ExpectedStyle(1, TAGMARK),          // =
                ExpectedStyle(15, AVALUE),          // "shiporder.xsd"
                ExpectedStyle(1, NONE),             // " "
                ExpectedStyle(7, ATTRIBUTE),        // orderid
                ExpectedStyle(1, TAGMARK),          // =
                ExpectedStyle(8, AVALUE),           // "889923"
                ExpectedStyle(1, TAGMARK),          // >
                ExpectedStyle(5, NONE),             // \n"    "
                ExpectedStyle(30, COMMENT),         // <!-- The person to ship to -->
                ExpectedStyle(5, NONE),             // \n"    "
                ExpectedStyle(1, TAGMARK),          // <
                ExpectedStyle(4, ANYNAMESPACE),     // xsi:
                ExpectedStyle(6, ANYTAG),           // shipto
                ExpectedStyle(1, TAGMARK),          // >
                ExpectedStyle(6, NONE),             // \n"    "\t
                ExpectedStyle(1, TAGMARK),          // <
                ExpectedStyle(4, ANYTAG),           // name
                ExpectedStyle(1, TAGMARK),          // >
                ExpectedStyle(12, NONE),            // Ola Nordmann
                ExpectedStyle(2, TAGMARK),          // </
                ExpectedStyle(4, ANYTAG),           // name
                ExpectedStyle(1, TAGMARK),          // >
                ExpectedStyle(9, NONE),             // \n"        "
                ExpectedStyle(1, TAGMARK),          // <
                ExpectedStyle(11, ANYTAG),          // description
                ExpectedStyle(1, TAGMARK),          // >
                ExpectedStyle(9, CDATATAG),         // <![CDATA[
                ExpectedStyle(90, CDATADATA),       // Content from CDATA...
                ExpectedStyle(3, CDATATAG),         // ]]>
                ExpectedStyle(2, TAGMARK),          // </
                ExpectedStyle(11, ANYTAG),          // description
                ExpectedStyle(1, TAGMARK),          // >
                ExpectedStyle(5, NONE),             // \n"    "
                ExpectedStyle(2, TAGMARK),          // </
                ExpectedStyle(4, ANYNAMESPACE),     // xsi:
                ExpectedStyle(6, ANYTAG),           // shipto
                ExpectedStyle(1, TAGMARK),          // >
                ExpectedStyle(1, NONE),             // \n
                ExpectedStyle(2, TAGMARK),          // </
                ExpectedStyle(9, ANYTAG),           // shiporder
                ExpectedStyle(1, TAGMARK)           // >
        )

        for (index in expectedStyles.indices) {
            val actual = result.getStyleSpan(index)
            val expected = expectedStyles[index]
            checkStyle(expected, actual)
        }

        assertEquals(expectedStyles.size, result.spanCount)
    }

    private fun checkStyle(expectedStyleSpan: ExpectedStyle, actualStyleSpan: StyleSpan<Collection<String>>) {
        if (expectedStyleSpan.style == NONE) {
            assertEquals(0, actualStyleSpan.style.size)
        } else {
            assertEquals(1, actualStyleSpan.style.size)
            assertEquals(expectedStyleSpan.style.name.toLowerCase(), actualStyleSpan.style.first())
        }
        assertEquals(expectedStyleSpan.length, actualStyleSpan.length)
    }

}
