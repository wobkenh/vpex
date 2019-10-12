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
    private val stop = AtomicBoolean(false)
    /**
     * Signals the File Watcher to ignore file events (e.g. VPEX is modifying the file)
     */
    val ignore = AtomicBoolean(false)

    fun stopThread() {
        stop.set(true)
    }

    override fun run() {
        try {
            FileSystems.getDefault().newWatchService().use { watcher ->
                val path = file.toPath().parent
                path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY)
                logger.info("Registering file watcher")
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
                            if (!ignore.get()) {
                                onChange()
                            }
                        }
                        val valid = key.reset()
                        if (!valid) {
                            break
                        }
                    }
                    yield()
                }
                logger.info("Stopping FileWatcher")
            }
        } catch (e: Throwable) {
            logger.warn { "Failure to watch file directory: ${e.message}" }
        }

    }
}
