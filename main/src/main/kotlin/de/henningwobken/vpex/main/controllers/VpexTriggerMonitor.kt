package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.InternalResource
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.nio.file.Files

class VpexTriggerMonitor : Controller() {

    private val internalResourceController by inject<InternalResourceController>()
    private val currentJarController by inject<CurrentJarController>()
    private val logger = KotlinLogging.logger {}
    private lateinit var onFilepathReceived: (path: String) -> Unit
    private val userHome = System.getProperty("user.home")
    private val statusFile = File("$userHome/.vpex/vpex.running")
    private val vpexHome = File("$userHome/.vpex/")
    private val receiveScriptFile = File("$userHome/.vpex/receive.vbs")
    private var shutdown = false

    fun start(onFilepathReceived: (path: String) -> Unit) {
        this.onFilepathReceived = onFilepathReceived
        if (!receiveScriptFile.exists()) {
            createReceiveScriptFile()
        } else {
            val firstLine = receiveScriptFile.useLines { it.firstOrNull() }
            if (firstLine == null || !firstLine.contains(currentJarController.currentPath)) {
                createReceiveScriptFile()
            }
        }

        val otherProcessCount = if (!statusFile.exists()) {
            statusFile.parentFile.mkdirs()
            0
        } else {
            Files.readAllLines(statusFile.toPath()).first().toLong()
        }
        logger.info("Starting as process nr ${otherProcessCount + 1}")
        Files.write(statusFile.toPath(), listOf((otherProcessCount + 1).toString()))
        // TODO: Share this with FileWatcher/MemoryMonitor/...
        Thread {
            while (true) {
                if (shutdown) {
                    break
                }
                vpexHome.listFiles()
                        ?.filter { it.name.startsWith("vpex.receive") }
                        ?.forEach { receiveFile ->
                            if (receiveFile.exists() && receiveFile.isFile) {
                                val path = Files.readAllLines(receiveFile.toPath()).first().trim('\"', '\r', '\n', ' ')
                                Files.delete(receiveFile.toPath())
                                logger.info("Triggered by receive file. Opening path '$path'")
                                onFilepathReceived(path)
                            }
                        }

                Thread.sleep(200)
            }
            logger.info("VpexTriggerMonitor shut down")
        }.start()
    }

    private fun createReceiveScriptFile() {
        logger.info("Created Receive Script File at ${receiveScriptFile.absolutePath}")
        val receiveScript = internalResourceController.getAsStrings(InternalResource.RECEIVE_SCRIPT)
                .map { line -> line.replace("<VPEX_PATH>", currentJarController.currentPath) }
        Files.write(receiveScriptFile.toPath(), receiveScript)
    }

    fun shutdown() {
        shutdown = true
        val processCount = Files.readAllLines(statusFile.toPath()).first().toLong()
        if (processCount > 1) {
            Files.write(statusFile.toPath(), listOf((processCount - 1).toString()))
        } else {
            Files.delete(statusFile.toPath())
        }
        logger.info("Shutting down with ${processCount - 1} processes left")
    }
}
