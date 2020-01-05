package de.henningwobken.vpex.main.controllers

import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.io.FileOutputStream

class CurrentJarController : Controller() {

    private val logger = KotlinLogging.logger {}

    val currentJar = initCurrentJar()
    val currentPath = currentJar.absolutePath

    private fun initCurrentJar(): File {
        val file = File(UpdateController::class.java.protectionDomain.codeSource.location.path)
        logger.info("Current location: ${file.absolutePath}")
        if (file.exists() && file.isDirectory) {
            val dummyFile = File(file, "dummy.jar")
            logger.info("In Dev mode. Replacing current jar with dummy at ${dummyFile.absolutePath}.")
            FileOutputStream(dummyFile).close()
            return dummyFile
        }
        return file
    }
}
