package de.henningwobken.vpex.main.controllers

import javafx.application.Platform
import javafx.beans.property.ReadOnlyLongProperty
import javafx.beans.property.SimpleLongProperty
import mu.KotlinLogging
import tornadofx.*
import kotlin.math.round

class MemoryMonitor : Controller() {
    private val logger = KotlinLogging.logger {}
    val maxMemory = round(Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0).toLong()
    private val allocatedMemoryProperty = SimpleLongProperty(0)
    val allocatedMemory: ReadOnlyLongProperty = allocatedMemoryProperty
    private val reservedMemoryProperty = SimpleLongProperty(0)
    val reservedMemory: ReadOnlyLongProperty = reservedMemoryProperty
    var isRunning = false
    private var stopMonitorThread = false

    fun start() {
        val thread = Thread({
            while (true) {
                if (stopMonitorThread) {
                    stopMonitorThread = false
                    logger.info { "Stopping memory monitor thread" }
                    break
                }
                val runtime = Runtime.getRuntime()
                val allocatedMemory = runtime.totalMemory() - runtime.freeMemory()
                val reservedMemory = runtime.totalMemory()
                logger.trace {
                    "Allocated: $allocatedMemory - Reserved: $reservedMemory - Max: $maxMemory"
                }
                Platform.runLater {
                    this.allocatedMemoryProperty.set(round(allocatedMemory / 1024.0 / 1024.0).toLong())
                    this.reservedMemoryProperty.set(round(reservedMemory / 1024.0 / 1024.0).toLong())
                }
                Thread.sleep(3000)
            }
            logger.info { "Stopped memory monitor thread" }
        }, "MemoryMonitor")
        thread.start()
        isRunning = true
    }

    fun stop() {
        logger.debug { "Setting flag to stop monitor" }
        stopMonitorThread = true
        isRunning = false
    }

}
