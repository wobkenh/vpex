package de.henningwobken.vpex.main.controllers

import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.util.concurrent.atomic.AtomicBoolean


class FileWatcher : Controller() {

    data class FileEntry(val file: File, val onChange: () -> Unit, var ignore: Boolean, var lastModified: Long)

    data class WatchEntry(val path: Path, val watchKey: WatchKey, val fileEntries: MutableList<FileEntry>)

    private val logger = KotlinLogging.logger {}
    private val lock = Object()
    private val stop = AtomicBoolean(false)

    private val fileEntries = mutableListOf<FileEntry>()
    private val watchEntries = mutableListOf<WatchEntry>()
    private val watchService = FileSystems.getDefault().newWatchService()

    /**
     * Signals the File Watcher to ignore file events (e.g. VPEX is modifying the file)
     */
    fun startIgnoring(file: File) {
        synchronized(lock) {
            val fileEntry = fileEntries.find { it.file == file }
            if (fileEntry != null) {
                fileEntry.ignore = true
            } else {
                logger.error { "Could not find file entry for file ${file.absolutePath}" }
            }
        }
    }

    /**
     * Stop ignoring file watcher events.
     * The new modification date of the file will be used to check that following file watcher events
     * do not refer to said modification
     * This may happen since the file watcher is only polled every so often
     * @param file file to stop ignoring events for
     */
    fun stopIgnoring(file: File) {
        synchronized(lock) {
            val fileEntry = fileEntries.find { it.file == file }
            if (fileEntry != null) {
                fileEntry.ignore = false
                fileEntry.lastModified = file.lastModified()
            } else {
                logger.error { "Could not find file entry for file ${file.absolutePath}" }
            }
        }
    }

    fun stopWatching(file: File) {
        logger.info("Removing FileWatcher for ${file.absolutePath}")
        synchronized(lock) {
            fileEntries.removeIf { it.file == file }
            val parentPath = file.parentFile.toPath()
            val watchEntry = watchEntries.find { it.path == parentPath }
            if (watchEntry != null) {
                watchEntry.fileEntries.removeIf { it.file == file }
                if (watchEntry.fileEntries.isEmpty()) {
                    logger.debug { "No more file entries for ${file.parentFile.absolutePath}. Removing WatchKey" }
                    watchEntry.watchKey.cancel()
                    watchEntries.remove(watchEntry)
                }
            } else {
                logger.error { "Tried to remove file ${file.absolutePath} from watcher but no watcher is registered." }
            }
        }
    }

    fun startWatching(file: File, onChange: () -> Unit) {
        logger.info("Adding FileWatcher for ${file.absolutePath}")
        synchronized(lock) {
            val fileEntry = FileEntry(file, onChange, false, file.lastModified())
            fileEntries.add(fileEntry)
            val parentPath = file.parentFile.toPath()
            val watchEntry = watchEntries.find { watchEntry -> watchEntry.path == parentPath }
            if (watchEntry != null) {
                logger.info("File Watcher already exists. Adding.")
                watchEntry.fileEntries.add(fileEntry)
            } else {
                logger.info("File Watcher does not exist. Creating WatchKey for ${file.parentFile.absolutePath}.")
                val watchKey = parentPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
                val newWatchEntry = WatchEntry(parentPath, watchKey, mutableListOf(fileEntry))
                watchEntries.add(newWatchEntry)
            }
        }
    }

    fun start() {
        logger.info("Starting FileWatcher")
        stop.set(false)
        Thread {
            while (!stop.get()) {
                for (watchEntry in watchEntries) {
                    for (event in watchEntry.watchKey.pollEvents()) {
                        val kind = event.kind()
                        val filename = event.context()
                        if (kind === StandardWatchEventKinds.OVERFLOW) {
                            continue
                        } else if (kind === StandardWatchEventKinds.ENTRY_MODIFY) {
                            synchronized(lock) {
                                val fileEntry = watchEntry.fileEntries.find { it.file.name == filename.toString() }
                                if (fileEntry != null) {
                                    if (!fileEntry.ignore) {
                                        if (fileEntry.file.lastModified() != fileEntry.lastModified) {
                                            fileEntry.onChange()
                                        } else {
                                            logger.debug("Ignoring watch event since last modification date is untouched")
                                        }
                                    }
                                }
                            }
                        }
                        if (!watchEntry.watchKey.isValid) {
                            break
                        }
                    }
                }
                Thread.sleep(50)
            }
            logger.info("Stopped FileWatcher")
        }.start()
    }

    fun stop() {
        logger.info("Stopping FileWatcher")
        stop.set(true)
    }

}
