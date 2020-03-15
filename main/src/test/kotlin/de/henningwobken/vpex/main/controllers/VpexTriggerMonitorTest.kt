package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.VpexConstants
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

internal class VpexTriggerMonitorTest {

    @Test
    fun start() {
        val triggerMonitor = VpexTriggerMonitor()
        assertFalse(isVpexTriggerMonitorRunning())
        var receivedFile = false
        triggerMonitor.start {
            receivedFile = true
        }
        Thread.sleep(50)
        assertTrue(isVpexTriggerMonitorRunning())

        val file = File(VpexConstants.vpexHome, "vpex.receive.test")
        Files.write(file.toPath(), listOf(""))

        Thread.sleep(250)

        assertTrue(receivedFile)
        assertFalse(file.exists())

        triggerMonitor.shutdown()
        Thread.sleep(250)
        assertFalse(isVpexTriggerMonitorRunning())
    }

    private fun isVpexTriggerMonitorRunning(): Boolean {
        return Thread.getAllStackTraces().keys.toString().contains("VpexTriggerMonitor")
    }
}
