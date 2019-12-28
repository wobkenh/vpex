package de.henningwobken.vpex.xml

import de.henningwobken.vpex.main.xml.XmlFormattingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.io.StringWriter

class XmlFormattingServiceTest {

    private val xmlFormattingService = XmlFormattingService()

    @Test
    fun `test ugly printing empty string`() {
        val xml = ""
        val input = StringReader(xml)
        val output = StringWriter()
        xmlFormattingService.format(input, output)
        assertEquals("", output.toString())
    }

    @Test
    fun `test ugly printing all tags`() {
        val inputXml = loadResourceUgly("all_states.xml.pretty")
        val outputXml = loadResourceUgly("all_states.xml.ugly")
        val input = StringReader(inputXml)
        val output = StringWriter()
        xmlFormattingService.format(input, output)
        assertEquals(outputXml, output.toString())
    }

    @Test
    fun `test ugly printing no indent`() {
        val inputXml = loadResourceUgly("no_indent.xml.pretty")
        val outputXml = loadResourceUgly("no_indent.xml.ugly")
        val input = StringReader(inputXml)
        val output = StringWriter()
        xmlFormattingService.format(input, output)
        assertEquals(outputXml, output.toString())
    }

    @Test
    fun `test pretty printing empty string`() {
        val xml = ""
        val input = StringReader(xml)
        val output = StringWriter()
        xmlFormattingService.format(input, output, withNewLines = true, indentSize = 4)
        assertEquals("", output.toString())
    }

    @Test
    fun `test pretty printing all tags`() {
        val inputXml = loadResourcePretty("all_states.xml.ugly")
        val outputXml = loadResourcePretty("all_states.xml.pretty")
        val input = StringReader(inputXml)
        val output = StringWriter()
        xmlFormattingService.format(input, output, withNewLines = true, indentSize = 4)
        assertEquals(outputXml, output.toString())
    }

    @Test
    fun `test pretty printing no indent`() {
        val inputXml = loadResourcePretty("no_indent.xml.ugly")
        val outputXml = loadResourcePretty("no_indent.xml.pretty")
        val input = StringReader(inputXml)
        val output = StringWriter()
        xmlFormattingService.format(input, output, withNewLines = true, indentSize = 0)
        assertEquals(outputXml, output.toString())
    }

    private fun loadResourceUgly(resource: String): String {
        return XmlFormattingServiceTest::class.java.getResource("/ugly_print/$resource").readText()
    }

    private fun loadResourcePretty(resource: String): String {
        return XmlFormattingServiceTest::class.java.getResource("/pretty_print/$resource").readText()
    }
}

