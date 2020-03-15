package de.henningwobken.vpex.main.controllers

import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import mu.KotlinLogging
import tornadofx.*

class VpexExecutor : Controller() {
    private val isRunningProperty = SimpleBooleanProperty(false)
    val isRunning: ReadOnlyBooleanProperty = isRunningProperty
    private val logger = KotlinLogging.logger {}
    private val lock = Object()
    private var currentTask: Thread? = null
    private var shouldCancel = false
    private var shouldShutdown = false

    init {
        Thread({
            while (true) {
                synchronized(lock) {
                    if (shouldShutdown) {
                        currentTask?.interrupt()
                        logger.debug("Shutting down Vpex Executor (Thread Pool and Monitor Thread)")
                        return@Thread
                    }
                    val thread = currentTask
                    if (thread == null) {
                        if (shouldCancel) {
                            logger.error("Cancelling was set, but there was no Task to cancel")
                            shouldCancel = false
                        }
                        return@synchronized
                    }
                    if (shouldCancel) {
                        logger.debug("Cancelling current Task")
                        if (thread.isInterrupted || !thread.isAlive) {
                            logger.error("Task was already done or cancelled")
                            shouldCancel = false
                            return@synchronized
                        }
                        thread.interrupt()
                        shouldCancel = false
                    }
                    if (!thread.isAlive) {
                        logger.info("Removing Task as it is done or was cancelled")
                        currentTask = null
                        isRunningProperty.set(false)
                    }
                }
                Thread.sleep(100)
            }
        }, "VpexExecutor").start()
    }

    fun execute(runnable: () -> Unit) {
        synchronized(lock) {
            isRunningProperty.set(true)
            val thread = Thread(runnable)
            logger.info("Setting current Task")
            thread.start()
            currentTask = thread
        }
    }

    fun cancel() {
        synchronized(lock) {
            if (isRunningProperty.get()) {
                logger.info("Flagging current task for cancellation")
                shouldCancel = true
            } else {
                logger.warn("Task could not be cancelled as there is none")
            }
        }
    }

    fun shutdown() {
        synchronized(lock) {
            logger.info("Flagging vpex executor for shutdown")
            shouldShutdown = true
        }
    }
}
