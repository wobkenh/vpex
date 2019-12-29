package de.henningwobken.vpex.main.other

import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import mu.KotlinLogging
import java.util.concurrent.Executors
import java.util.concurrent.Future

class VpexExecutor {
    private val isRunningProperty = SimpleBooleanProperty(false)
    val isRunning: ReadOnlyBooleanProperty = isRunningProperty
    private val logger = KotlinLogging.logger {}
    private val threadPool = Executors.newSingleThreadExecutor()
    private val lock = Object()
    private var currentTask: Future<*>? = null
    private var shouldCancel = false
    private var shouldShutdown = false

    init {
        threadPool.submit()
        Thread {
            while (true) {
                synchronized(lock) {
                    if (shouldShutdown) {
                        logger.debug("Shutting down Vpex Executor (Thread Pool and Monitor Thread)")
                        threadPool.shutdownNow()
                        return@Thread
                    }
                    val future = currentTask
                    if (future == null) {
                        if (shouldCancel) {
                            logger.error("Cancelling was set, but there was no Task to cancel")
                            shouldCancel = false
                        }
                        return@synchronized
                    }
                    if (shouldCancel) {
                        logger.debug("Cancelling current Task")
                        if (future.isCancelled || future.isDone) {
                            logger.error("Task was already done or cancelled")
                            shouldCancel = false
                            return@synchronized
                        }
                        if (!future.cancel(true)) {
                            logger.warn("Task could not be cancelled. Maybe it was already finished?")
                        }
                        shouldCancel = false
                    }
                    if (future.isDone) {
                        logger.info("Removing Task as it is done or cancelled")
                        currentTask = null
                        isRunningProperty.set(false)
                    }
                }
                Thread.sleep(100)
            }
        }.start()
    }

    fun execute(runnable: () -> Unit) {
        synchronized(lock) {
            isRunningProperty.set(true)
            val future = threadPool.submit(runnable)
            logger.info("Setting current Task")
            currentTask = future
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
