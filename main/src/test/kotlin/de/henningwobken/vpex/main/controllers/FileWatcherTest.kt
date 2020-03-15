package de.henningwobken.vpex.main.controllers

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

internal class FileWatcherTest {

    @Test
    fun startIgnoring() {
        val fileWatcher = FileWatcher()
        fileWatcher.start()
        val file = File.createTempFile("vpex", "test")
        file.setLastModified(System.currentTimeMillis() / 1000 - 1000)
        var changeWasDetected = false
        fileWatcher.startWatching(file) {
            changeWasDetected = true
        }
        assertFalse(changeWasDetected)
        fileWatcher.startIgnoring(file)
        Files.write(file.toPath(), listOf("test"))
        Thread.sleep(100)
        assertFalse(changeWasDetected)
        fileWatcher.stop()
    }

    @Test
    fun stopIgnoring() {
        val fileWatcher = FileWatcher()
        fileWatcher.start()
        val file = File.createTempFile("vpex", "test")
        file.setLastModified(System.currentTimeMillis() / 1000 - 1000)
        var changeWasDetected = false
        fileWatcher.startWatching(file) {
            changeWasDetected = true
        }
        assertFalse(changeWasDetected)
        fileWatcher.startIgnoring(file)
        fileWatcher.stopIgnoring(file)
        Files.write(file.toPath(), listOf("test"))
        Thread.sleep(100)
        assertTrue(changeWasDetected)
        fileWatcher.stop()
    }

    @Test
    fun stopWatching() {
        val fileWatcher = FileWatcher()
        fileWatcher.start()
        val file = File.createTempFile("vpex", "test")
        file.setLastModified(System.currentTimeMillis() / 1000 - 1000)
        var changeWasDetected = false
        fileWatcher.startWatching(file) {
            changeWasDetected = true
        }
        assertFalse(changeWasDetected)
        fileWatcher.stopWatching(file)
        Files.write(file.toPath(), listOf("test"))
        Thread.sleep(100)
        assertFalse(changeWasDetected)
        fileWatcher.stop()
    }

    @Test
    fun startWatching() {
        val fileWatcher = FileWatcher()
        fileWatcher.start()
        val file = File.createTempFile("vpex", "test")
        file.setLastModified(System.currentTimeMillis() / 1000 - 1000)
        var changeWasDetected = false
        fileWatcher.startWatching(file) {
            changeWasDetected = true
        }
        assertFalse(changeWasDetected)
        Files.write(file.toPath(), listOf("test"))
        Thread.sleep(100)
        assertTrue(changeWasDetected)
        fileWatcher.stop()
    }


    @Test
    fun startWatchingMultiple() {
        val fileWatcher = FileWatcher()
        fileWatcher.start()
        val fileA = File.createTempFile("vpex", "test")
        fileA.setLastModified(System.currentTimeMillis() / 1000 - 1000)
        val fileB = File.createTempFile("vpex", "test")
        fileB.setLastModified(System.currentTimeMillis() / 1000 - 1000)
        var changeWasDetectedA = false
        var changeWasDetectedB = false
        fileWatcher.startWatching(fileA) {
            changeWasDetectedA = true
        }
        fileWatcher.startWatching(fileB) {
            changeWasDetectedB = true
        }
        assertFalse(changeWasDetectedA)
        assertFalse(changeWasDetectedB)
        Files.write(fileA.toPath(), listOf("test"))
        Thread.sleep(100)
        assertTrue(changeWasDetectedA)
        assertFalse(changeWasDetectedB)
        Files.write(fileB.toPath(), listOf("test"))
        Thread.sleep(100)
        assertTrue(changeWasDetectedA)
        assertTrue(changeWasDetectedB)
        fileWatcher.stop()
    }

    @Test
    fun startAndStop() {
        Thread.sleep(100) // File Watcher may still be running from other test
        assertFalse(isFileWatcherRunning())
        val fileWatcher = FileWatcher()
        fileWatcher.start()
        Thread.sleep(100)
        assertTrue(isFileWatcherRunning())
        fileWatcher.stop()
        Thread.sleep(100)
        assertFalse(isFileWatcherRunning())
    }

    private fun isFileWatcherRunning(): Boolean {
        return Thread.getAllStackTraces().keys.toString().contains("FileWatcher")
    }
}
