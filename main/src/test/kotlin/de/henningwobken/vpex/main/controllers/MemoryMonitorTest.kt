package de.henningwobken.vpex.main.controllers

import javafx.embed.swing.JFXPanel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.CountDownLatch
import javax.swing.SwingUtilities

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class MemoryMonitorTest {

    @BeforeAll
    fun init() {
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            JFXPanel() // initializes JavaFX environment
            latch.countDown()
        }
        latch.await()
    }

    @Test
    fun startAndStop() {
        val memoryMonitor = MemoryMonitor()

        assertFalse(isMemoryMonitorRunning())
        assertFalse(memoryMonitor.isRunning)
        assertEquals(0, memoryMonitor.allocatedMemory.get())
        assertTrue(memoryMonitor.maxMemory > 0)
        assertEquals(0, memoryMonitor.reservedMemory.get())

        memoryMonitor.start()

        Thread.sleep(100)

        assertTrue(isMemoryMonitorRunning())
        assertTrue(memoryMonitor.isRunning)
        assertTrue(memoryMonitor.allocatedMemory.get() > 0)
        assertTrue(memoryMonitor.maxMemory > 0)
        assertTrue(memoryMonitor.reservedMemory.get() > 0)

        memoryMonitor.stop()

        Thread.sleep(3000) // It can take up to 3 sec until the memory monitor stops

        assertFalse(isMemoryMonitorRunning())
        assertFalse(memoryMonitor.isRunning)
    }

    private fun isMemoryMonitorRunning(): Boolean {
        return Thread.getAllStackTraces().keys.toString().contains("MemoryMonitor")
    }

}
