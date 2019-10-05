package de.henningwobken.vpex.controller

import de.henningwobken.vpex.main.controllers.SearchAndReplaceController
import de.henningwobken.vpex.main.model.SearchDirection
import de.henningwobken.vpex.main.model.SearchTextMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SearchAndReplaceControllerTest {
    private val searchAndReplaceController = SearchAndReplaceController()
    private val fullText = """
        <?xml version="1.0" encoding="UTF-8"?>
        <shiporder xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="shiporder.xsd" orderid="889923">
          <orderperson>John Smith</orderperson>
          <shipto>
            <name>Ola Nordmann</name>
            <address>Langgt 23</address>
            <city>4000 Stavanger</city>
            <country>Norway</country>
          </shipto>
          <item>
            <title>Empire Burlesque</title>
            <note>Special Edition</note>
            <quantity>1</quantity>
            <price>10.90</price>
          </item>
          <item>
            <title>Hide your heart</title>
            <quantity>1</quantity>
            <price>9.90</price>
          </item>
        </shiporder>
    """.trimIndent()

    @Test
    fun `find next down normal`() {
        val find = searchAndReplaceController.findNext(fullText, "Special Edition", 0, SearchDirection.DOWN, SearchTextMode.NORMAL, false)
        find!!
        assertEquals(find.start, 411)
        assertEquals(find.end, 426)

        val findOffset = searchAndReplaceController.findNext(fullText, "Special Edition", 411, SearchDirection.DOWN, SearchTextMode.NORMAL, false)
        findOffset!!
        assertEquals(findOffset.start, 411)
        assertEquals(findOffset.end, 426)

        var noFind = searchAndReplaceController.findNext(fullText, "Special Edition", 412, SearchDirection.DOWN, SearchTextMode.NORMAL, false)
        assertNull(noFind)
        noFind = searchAndReplaceController.findNext(fullText, "special edition", 0, SearchDirection.DOWN, SearchTextMode.NORMAL, false)
        assertNull(noFind)
    }

    @Test
    fun `find next down normal ignore case`() {
        val find = searchAndReplaceController.findNext(fullText, "speCial eDition", 0, SearchDirection.DOWN, SearchTextMode.NORMAL, true)
        find!!
        assertEquals(find.start, 411)
        assertEquals(find.end, 426)
    }

    @Test
    fun `find next down extended`() {
        val find = searchAndReplaceController.findNext(fullText, "</note>\n    <quantity>", 0, SearchDirection.DOWN, SearchTextMode.EXTENDED, false)
        find!!
        assertEquals(find.start, 426)
        assertEquals(find.end, 448)

        val noFind = searchAndReplaceController.findNext(fullText, "</NOTE>\n    <QUANTITY>", 0, SearchDirection.DOWN, SearchTextMode.EXTENDED, false)
        assertNull(noFind)
    }

    @Test
    fun `find next down extended ignore case`() {
        val find = searchAndReplaceController.findNext(fullText, "</NOTE>\n    <QUANTITY>", 0, SearchDirection.DOWN, SearchTextMode.EXTENDED, true)
        find!!
        assertEquals(find.start, 426)
        assertEquals(find.end, 448)
    }

    @Test
    fun `find next down regex`() {
        val searchDirection = SearchDirection.DOWN
        val textMode = SearchTextMode.REGEX
        val ignoreCase = false

        val find = searchAndReplaceController.findNext(fullText, "Lan[gt]{3} [0-9]+", 0, searchDirection, textMode, ignoreCase)
        find!!
        assertEquals(find.start, 262)
        assertEquals(find.end, 271)

        val findOffset = searchAndReplaceController.findNext(fullText, "Lan[gt]{3} [0-9]+", 262, searchDirection, textMode, ignoreCase)
        findOffset!!
        assertEquals(findOffset.start, 262)
        assertEquals(findOffset.end, 271)

        val noFind = searchAndReplaceController.findNext(fullText, "Lan[gt]{3} [0-9]+", 263, searchDirection, textMode, ignoreCase)
        assertNull(noFind)
    }

    @Test
    fun `find next down regex ignore case`() {
        val searchDirection = SearchDirection.DOWN
        val textMode = SearchTextMode.REGEX
        val ignoreCase = true

        val find = searchAndReplaceController.findNext(fullText, "lan[GT]{3} [0-9]+", 0, searchDirection, textMode, ignoreCase)
        find!!
        assertEquals(find.start, 262)
        assertEquals(find.end, 271)
    }

}
