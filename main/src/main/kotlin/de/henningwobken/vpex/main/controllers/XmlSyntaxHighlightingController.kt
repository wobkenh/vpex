package de.henningwobken.vpex.main.controllers

import org.fxmisc.richtext.model.StyleSpans
import org.fxmisc.richtext.model.StyleSpansBuilder
import tornadofx.*
import java.util.regex.Pattern


class XmlSyntaxHighlightingController : Controller() {

    companion object {
        // h => horizontal whitespace
        // w => any letters
        private val XML_TAG = Pattern.compile(
                "(?<ELEMENT>(<[/?]?\\h*)(\\w+:)?(\\w+)([^<>?]*)(\\h*[/?]?>))|(?<COMMENT><!--[^<>]+-->)|(?<CDATA>(<!\\[CDATA\\[)(.*)(]]>))",
                Pattern.DOTALL
        )
        private val ATTRIBUTES = Pattern.compile("(\\w+:)?(\\w+\\h*)(=)(\\h*\"[^\"]+\")")

        // Elements
        private const val GROUP_OPEN_BRACKET = 2
        private const val GROUP_ELEMENT_NAMESPACE = 3
        private const val GROUP_ELEMENT_NAME = 4
        private const val GROUP_ATTRIBUTES_SECTION = 5
        private const val GROUP_CLOSE_BRACKET = 6

        // Attributes
        private const val GROUP_ATTRIBUTE_NAMESPACE = 1
        private const val GROUP_ATTRIBUTE_NAME = 2
        private const val GROUP_EQUAL_SYMBOL = 3
        private const val GROUP_ATTRIBUTE_VALUE = 4

        // CDATA
        private const val GROUP_CDATA_OPENING_TAG = 9
        private const val GROUP_CDATA_DATA = 10
        private const val GROUP_CDATA_CLOSING_TAG = 11
    }

    fun computeHighlighting(text: String): StyleSpans<Collection<String>> {
        val matcher = XML_TAG.matcher(text)
        var lastKwEnd = 0
        val spansBuilder = StyleSpansBuilder<Collection<String>>()
        while (matcher.find()) {
            spansBuilder.add(mutableListOf(), matcher.start() - lastKwEnd)
            when {
                matcher.group("COMMENT") != null -> {
                    spansBuilder.add(mutableListOf("comment"), matcher.end() - matcher.start())
                }
                matcher.group("CDATA") != null -> {
                    spansBuilder.add(mutableListOf("cdatatag"), matcher.end(GROUP_CDATA_OPENING_TAG) - matcher.start(GROUP_CDATA_OPENING_TAG))
                    spansBuilder.add(mutableListOf("cdatadata"), matcher.end(GROUP_CDATA_DATA) - matcher.start(GROUP_CDATA_DATA))
                    spansBuilder.add(mutableListOf("cdatatag"), matcher.end(GROUP_CDATA_CLOSING_TAG) - matcher.start(GROUP_CDATA_CLOSING_TAG))
                }
                matcher.group("ELEMENT") != null -> {
                    val attributesText = matcher.group(GROUP_ATTRIBUTES_SECTION)
                    spansBuilder.add(mutableListOf("tagmark"), matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET))
                    if (matcher.end(GROUP_ELEMENT_NAMESPACE) >= 0) {
                        spansBuilder.add(mutableListOf("anynamespace"), matcher.end(GROUP_ELEMENT_NAMESPACE) - matcher.end(GROUP_OPEN_BRACKET))
                        spansBuilder.add(mutableListOf("anytag"), matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_ELEMENT_NAMESPACE))
                    } else {
                        // No Namspace prefix
                        spansBuilder.add(mutableListOf("anytag"), matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET))
                    }
                    if (attributesText.isNotEmpty()) {
                        lastKwEnd = 0
                        val amatcher = ATTRIBUTES.matcher(attributesText)
                        while (amatcher.find()) {
                            spansBuilder.add(mutableListOf(), amatcher.start() - lastKwEnd)
                            if (matcher.end(GROUP_ATTRIBUTE_NAMESPACE) >= 0) {
                                spansBuilder.add(mutableListOf("attributenamespace"), amatcher.end(GROUP_ATTRIBUTE_NAMESPACE) - amatcher.start(GROUP_ATTRIBUTE_NAMESPACE))
                            }
                            spansBuilder.add(mutableListOf("attribute"), amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME))
                            spansBuilder.add(mutableListOf("tagmark"), amatcher.end(GROUP_EQUAL_SYMBOL) - amatcher.end(GROUP_ATTRIBUTE_NAME))
                            spansBuilder.add(mutableListOf("avalue"), amatcher.end(GROUP_ATTRIBUTE_VALUE) - amatcher.end(GROUP_EQUAL_SYMBOL))
                            lastKwEnd = amatcher.end()
                        }
                        if (attributesText.length > lastKwEnd) {
                            spansBuilder.add(mutableListOf(), attributesText.length - lastKwEnd)
                        }
                    }
                    lastKwEnd = matcher.end(GROUP_ATTRIBUTES_SECTION)
                    spansBuilder.add(mutableListOf("tagmark"), matcher.end(GROUP_CLOSE_BRACKET) - lastKwEnd)
                }
            }

            lastKwEnd = matcher.end()
        }
        spansBuilder.add(mutableListOf(), text.length - lastKwEnd)
        return spansBuilder.create()
    }
}
