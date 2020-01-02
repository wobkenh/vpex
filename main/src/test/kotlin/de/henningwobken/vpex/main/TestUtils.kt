package de.henningwobken.vpex.main

import java.io.File

class TestUtils {
    companion object {
        fun loadResource(resource: String): File {
            return File(TestUtils::class.java.getResource(resource).toURI())
        }
    }
}
