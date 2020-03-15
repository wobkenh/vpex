package de.henningwobken.vpex.main.controllers

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class CurrentJarControllerTest {

    @Test
    fun getCurrentJar() {
        assertNotNull(CurrentJarController().currentJar)
    }

    @Test
    fun getCurrentPath() {
        assertNotNull(CurrentJarController().currentPath)
    }
}
