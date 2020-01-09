package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.InternalResource
import de.henningwobken.vpex.main.model.VpexConstants
import mslinks.ShellLink
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.nio.file.Files

class WindowsLinkController : Controller() {

    private val logger = KotlinLogging.logger {}
    private val currentJarController: CurrentJarController by inject()
    private val internalResourceController: InternalResourceController by inject()
    private val vpexLinkPath = "Microsoft\\Windows\\Start Menu\\Programs\\Vpex.lnk"
    private val iconFile = File("${VpexConstants.userHome}/.vpex/vpex_icon.ico")
    private val startMenuFile = File(System.getenv("APPDATA") + "\\$vpexLinkPath")
    private val desktopIconFile = File("${VpexConstants.userHome}\\Desktop\\Vpex.lnk")

    fun addVpexStartMenuEntry() {
        if (VpexConstants.isWindows) {
            checkIconFile()

            // Check if Icon is up to date and exit if it is
            logger.debug { "Using Appdatapath: ${startMenuFile.absolutePath}" }
            if (hasCorrectPath(startMenuFile, "Start Menu")) {
                return
            }

            // Create entry
            logger.info { "Creating Windows Startmenu Entry." }
            createLink(startMenuFile)
        } else {
            logger.warn { "Trying to create Windows Startmenu on non-windows system." }
        }
    }

    fun addVpexDesktopIcon() {
        if (VpexConstants.isWindows) {
            checkIconFile()

            // Check if Icon is up to date and exit if it is
            if (hasCorrectPath(desktopIconFile, "Desktop Icon")) {
                return
            }

            // Create entry
            logger.info { "Creating Windows Startmenu Entry." }
            createLink(desktopIconFile)

        } else {
            logger.warn { "Trying to create Windows Desktop Icon on non-windows system." }
        }
    }

    fun removeVpexStartMenuEntry() {
        deleteFile(startMenuFile, "Start Menu Entry")
    }

    fun removeVpexDesktopIcon() {
        deleteFile(desktopIconFile, "Desktop Icon")
    }

    private fun deleteFile(file: File, name: String) {
        if (file.exists()) {
            if (file.delete()) {
                logger.debug { "Removed $name." }
            } else {
                logger.warn { "Could not remove $name." }
            }

        } else {
            logger.debug { "Not removing $name as it does not exist." }
        }
    }

    private fun createLink(file: File) {
        val shellLink = ShellLink.createLink(currentJarController.currentPath)
                .setName("Vpex")
                .setIconLocation(iconFile.absolutePath)
        shellLink.saveTo(file.absolutePath)
    }

    private fun hasCorrectPath(file: File, name: String): Boolean {
        if (startMenuFile.exists()) {
            logger.debug { "Vpex $name exists. Checking Destination" }
            val shellLink = ShellLink(file)
            val target = shellLink.resolveTarget()
            val currentPath = currentJarController.currentPath
            return if (target == currentPath) {
                logger.debug { "Vpex $name path is up to date" }
                true
            } else {
                logger.debug { "Vpex Path in $name differs from current path. Updating:" }
                logger.debug { "Old: $target" }
                logger.debug { "New: $currentPath" }
                false
            }
        } else {
            return false
        }
    }

    private fun checkIconFile() {
        // Copy Icon
        if (!iconFile.exists()) {
            iconFile.parentFile.mkdirs()
            Files.copy(internalResourceController.getAsInputStream(InternalResource.ICON_32), iconFile.toPath())
        } else {
            logger.debug("Icon file exists already")
        }
    }

}
