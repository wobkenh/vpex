package de.henningwobken.vpex.main.other

import mu.KotlinLogging
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class FileWatcher(private val file: File, private val onChange: () -> Unit) : Thread() {
    private val logger = KotlinLogging.logger {}
    private val lock = Object()
    private val stop = AtomicBoolean(false)

    private var ignore = false
    private var lastModified = 0L

    /**
     * Signals the File Watcher to ignore file events (e.g. VPEX is modifying the file)
     */
    fun startIgnoring() {
        synchronized(lock) {
            ignore = true
        }
    }

    /**
     * Stop ignoring file watcher events.
     * The new modification date of the file will be used to check that following file watcher events
     * do not refer to said modification
     * This may happen since the file watcher is only polled every so often
     * @param newModificationDate the modification Date of the file after the operation
     */
    fun stopIgnoring(newModificationDate: Long) {
        synchronized(lock) {
            ignore = false
            lastModified = newModificationDate
        }
    }

    fun stopThread() {
        logger.info("Stopping FileWatcher")
        stop.set(true)
    }

    override fun run() {
        try {
            FileSystems.getDefault().newWatchService().use { watcher ->
                val path = file.toPath().parent
                path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY)
                logger.info("Registering file watcher on {}", path.toString())
                while (!stop.get()) {
                    val key: WatchKey?
                    try {
                        key = watcher.poll(25, TimeUnit.MILLISECONDS)
                    } catch (e: InterruptedException) {
                        return
                    }

                    if (key == null) {
                        yield()
                        continue
                    }

                    for (event in key.pollEvents()) {
                        val kind = event.kind()
                        val filename = event.context()
                        if (kind === StandardWatchEventKinds.OVERFLOW) {
                            yield()
                            continue
                        } else if (kind === StandardWatchEventKinds.ENTRY_MODIFY && filename.toString() == file.name) {
                            synchronized(lock) {
                                if (!ignore) {
                                    if (file.lastModified() != lastModified) {
                                        onChange()
                                    } else {
                                        logger.debug("Ignoring watch event since last modification date is untouched")
                                    }
                                }
                            }
                        }
                        val valid = key.reset()
                        if (!valid) {
                            break
                        }
                    }
                    yield()
                }
                logger.info("FileWatcher stopped")
            }
        } catch (e: Throwable) {
            logger.warn { "Failure to watch file directory: ${e.message}" }
        }

    }
}
