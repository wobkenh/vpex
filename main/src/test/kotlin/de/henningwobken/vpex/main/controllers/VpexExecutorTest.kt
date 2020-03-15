package de.henningwobken.vpex.main.controllers

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VpexExecutorTest {

    @Test
    fun execute() {
        Thread.sleep(150)
        val executor = VpexExecutor()
        var stop = false
        executor.execute {
            while (!stop) {
                Thread.sleep(5)
            }
        }
        Thread.sleep(150)
        assertTrue(executor.isRunning.get())
        stop = true
        Thread.sleep(150)
        assertFalse(executor.isRunning.get())
        executor.shutdown()
    }

    @Test
    fun cancel() {
        Thread.sleep(150)
        val executor = VpexExecutor()
        executor.execute {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(5)
                } catch (e: Exception) {
                    break
                }
            }
        }
        assertTrue(executor.isRunning.get())
        executor.cancel()
        Thread.sleep(250)
        assertFalse(executor.isRunning.get())
        executor.shutdown()
    }

    @Test
    fun shutdown() {
        Thread.sleep(150) // may be running from other test
        assertFalse(isVpexExecutorRunning())
        val executor = VpexExecutor()

        Thread.sleep(150)

        assertTrue(isVpexExecutorRunning())

        executor.shutdown()
        Thread.sleep(150)

        assertFalse(isVpexExecutorRunning())
    }

    private fun isVpexExecutorRunning(): Boolean {
        return Thread.getAllStackTraces().keys.toString().contains("VpexExecutor")
    }
}
