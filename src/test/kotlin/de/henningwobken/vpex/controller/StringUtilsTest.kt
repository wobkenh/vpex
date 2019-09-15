package de.henningwobken.vpex.controller

import de.henningwobken.vpex.controllers.StringUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StringUtilsTest {

    private val stringUtils = StringUtils()


    @Test
    fun testIndexOfIgnoreCase() {
        assertEquals(stringUtils.indexOfIgnoreCase("A", "A", 0), 0)
        assertEquals(stringUtils.indexOfIgnoreCase("a", "A", 0), 0)
        assertEquals(stringUtils.indexOfIgnoreCase("A", "a", 0), 0)
        assertEquals(stringUtils.indexOfIgnoreCase("a", "a", 0), 0)

        assertEquals(stringUtils.indexOfIgnoreCase("a", "ba", 0), -1)
        assertEquals(stringUtils.indexOfIgnoreCase("ba", "a", 0), 1)

        assertEquals(stringUtils.indexOfIgnoreCase("Royal Blue", " Royal Blue", 0), -1)
        assertEquals(stringUtils.indexOfIgnoreCase(" Royal Blue", "Royal Blue", 0), 1)
        assertEquals(stringUtils.indexOfIgnoreCase("Royal Blue", "royal", 0), 0)
        assertEquals(stringUtils.indexOfIgnoreCase("Royal Blue", "oyal", 0), 1)
        assertEquals(stringUtils.indexOfIgnoreCase("Royal Blue", "al", 0), 3)
        assertEquals(stringUtils.indexOfIgnoreCase("", "royal", 0), -1)
        assertEquals(stringUtils.indexOfIgnoreCase("Royal Blue", "", 0), 0)
        assertEquals(stringUtils.indexOfIgnoreCase("Royal Blue", "BLUE", 0), 6)
        assertEquals(stringUtils.indexOfIgnoreCase("Royal Blue", "BIGLONGSTRING", 0), -1)
        assertEquals(stringUtils.indexOfIgnoreCase("Royal Blue", "Royal Blue LONGSTRING", 0), -1)
        assertEquals(stringUtils.indexOfIgnoreCase("Royal Blue", "Royal Blue LONGSTRING", 0), -1)
        assertEquals(stringUtils.indexOfIgnoreCase("Royal Blue Royal Blue", "Royal Blue", 0), 0)
    }

    @Test
    fun testLastIndexOfIgnoreCase() {
        assertEquals(stringUtils.lastIndexOfIgnoreCase("A", "A", 0), 0)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("a", "A", 0), 0)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("A", "a", 0), 0)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("a", "a", 0), 0)

        assertEquals(stringUtils.lastIndexOfIgnoreCase("a", "ba", 0), -1)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("ba", "a", 0), 1)

        assertEquals(stringUtils.lastIndexOfIgnoreCase("Royal Blue", " Royal Blue", 0), -1)
        assertEquals(stringUtils.lastIndexOfIgnoreCase(" Royal Blue", "Royal Blue", 0), 1)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("Royal Blue", "royal", 0), 0)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("Royal Blue", "oyal", 0), 1)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("Royal Blue", "al", 0), 3)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("", "royal", 0), -1)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("Royal Blue", "", 0), 0)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("Royal Blue", "BLUE", 0), 6)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("Royal Blue", "BIGLONGSTRING", 0), -1)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("Royal Blue", "Royal Blue LONGSTRING", 0), -1)
        assertEquals(stringUtils.lastIndexOfIgnoreCase("Royal Blue Royal Blue", "Royal Blue", 0), 11)
    }

}