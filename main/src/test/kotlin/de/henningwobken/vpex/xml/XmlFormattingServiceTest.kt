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
        xmlFormattingService.uglyPrint(input, output)
        assertEquals("", output.toString())
    }

    @Test
    fun `test ugly printing all tags`() {
        val inputXml = loadResource("all_states.xml.pretty")
        val outputXml = loadResource("all_states.xml.ugly")
        val input = StringReader(inputXml)
        val output = StringWriter()
        xmlFormattingService.uglyPrint(input, output)
        assertEquals(outputXml, output.toString())
    }

    @Test
    fun `test ugly printing no indent`() {
        val inputXml = loadResource("no_indent.xml.pretty")
        val outputXml = loadResource("no_indent.xml.ugly")
        val input = StringReader(inputXml)
        val output = StringWriter()
        xmlFormattingService.uglyPrint(input, output)
        assertEquals(outputXml, output.toString())
    }

    private fun loadResource(resource: String): String {
        return XmlFormattingServiceTest::class.java.getResource("/ugly_print/$resource").readText()
    }
}

