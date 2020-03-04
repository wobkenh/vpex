package de.henningwobken.vpex.main.controllers

import org.fxmisc.richtext.model.StyleSpans
import org.fxmisc.richtext.model.StyleSpansBuilder
import tornadofx.*
import java.util.regex.Pattern


class XmlSyntaxHighlightingController : Controller() {

    private val XML_TAG = Pattern.compile("(?<ELEMENT>(</?\\h*)(\\w+)([^<>]*)(\\h*/?>))"
            + "|(?<COMMENT><!--[^<>]+-->)")
    private val ATTRIBUTES = Pattern.compile("(\\w+\\h*)(=)(\\h*\"[^\"]+\")")
    private val GROUP_OPEN_BRACKET = 2
    private val GROUP_ELEMENT_NAME = 3
    private val GROUP_ATTRIBUTES_SECTION = 4
    private val GROUP_CLOSE_BRACKET = 5
    private val GROUP_ATTRIBUTE_NAME = 1
    private val GROUP_EQUAL_SYMBOL = 2
    private val GROUP_ATTRIBUTE_VALUE = 3

    fun computeHighlighting(text: String): StyleSpans<Collection<String>> {
        val matcher = XML_TAG.matcher(text)
        var lastKwEnd = 0
        val spansBuilder = StyleSpansBuilder<Collection<String>>()
        while (matcher.find()) {
            spansBuilder.add(emptyList(), matcher.start() - lastKwEnd)
            if (matcher.group("COMMENT") != null) {
                spansBuilder.add(setOf("comment"), matcher.end() - matcher.start())
            } else {
                if (matcher.group("ELEMENT") != null) {
                    val attributesText = matcher.group(GROUP_ATTRIBUTES_SECTION)
                    spansBuilder.add(setOf("tagmark"), matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET))
                    spansBuilder.add(setOf("anytag"), matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET))
                    if (attributesText.isNotEmpty()) {
                        lastKwEnd = 0
                        val amatcher = ATTRIBUTES.matcher(attributesText)
                        while (amatcher.find()) {
                            spansBuilder.add(emptyList(), amatcher.start() - lastKwEnd)
                            spansBuilder.add(setOf("attribute"), amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME))
                            spansBuilder.add(setOf("tagmark"), amatcher.end(GROUP_EQUAL_SYMBOL) - amatcher.end(GROUP_ATTRIBUTE_NAME))
                            spansBuilder.add(setOf("avalue"), amatcher.end(GROUP_ATTRIBUTE_VALUE) - amatcher.end(GROUP_EQUAL_SYMBOL))
                            lastKwEnd = amatcher.end()
                        }
                        if (attributesText.length > lastKwEnd) spansBuilder.add(emptyList(), attributesText.length - lastKwEnd)
                    }
                    lastKwEnd = matcher.end(GROUP_ATTRIBUTES_SECTION)
                    spansBuilder.add(setOf("tagmark"), matcher.end(GROUP_CLOSE_BRACKET) - lastKwEnd)
                }
            }
            lastKwEnd = matcher.end()
        }
        spansBuilder.add(emptyList(), text.length - lastKwEnd)
        return spansBuilder.create()
    }
}
