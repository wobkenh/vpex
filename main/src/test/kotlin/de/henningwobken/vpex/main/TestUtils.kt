package de.henningwobken.vpex.main

import de.henningwobken.vpex.main.controllers.ExpectedClass
import de.henningwobken.vpex.main.controllers.ExpectedStyles
import org.fxmisc.richtext.CodeArea
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.Mockito
import java.io.File

class TestUtils {
    companion object {
        fun loadResource(resource: String): File {
            return File(TestUtils::class.java.getResource(resource).toURI())
        }

        private fun toCamelCase(string: String): String? {
            val camelCaseString = string.split("_").map { toProperCase(it) }.joinToString("")
            return camelCaseString[0].toLowerCase() + camelCaseString.substring(1)
        }

        private fun toProperCase(string: String): String? {
            return string.substring(0, 1).toUpperCase() +
                    string.substring(1).toLowerCase()
        }

        fun checkStyle(expectedStyles: List<ExpectedStyles>, codeArea: CodeArea) {
            val styleSpans = codeArea.getStyleSpans(0, codeArea.text.length)

            for (index in expectedStyles.indices) {
                val expectedStyle = expectedStyles[index]
                val actualStyle = styleSpans.getStyleSpan(index)
                assertEquals(expectedStyle.length, actualStyle.length, "Length of Style differs. Expected $expectedStyle but found $actualStyle")

                val styleDifferenceMessage = "Failed at style no $index: expected ${expectedStyle.styles} but found ${actualStyle.style}"
                if (expectedStyle.styles == listOf(ExpectedClass.NONE)) {
                    assertEquals(0, actualStyle.style.size, styleDifferenceMessage)
                } else {
                    assertEquals(expectedStyle.styles.size, actualStyle.style.size, styleDifferenceMessage)
                    val actualStyleClasses = actualStyle.style.toMutableList()
                    for (styleClassIndex in expectedStyle.styles.indices) {
                        val expectedStyleClass = toCamelCase(expectedStyle.styles[styleClassIndex].name)
                        // We do not care about the order as javafx determines importance according to the
                        // order in the stylesheet
                        assertEquals(1, actualStyleClasses.filter { it == expectedStyleClass }.size, styleDifferenceMessage)
                    }
                }
            }

            assertEquals(expectedStyles.size, styleSpans.spanCount)
        }

        fun <T> nullsafeAny(): T {
            Mockito.any<T>()
            return uninitialized()
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> uninitialized(): T = null as T

    }
}
