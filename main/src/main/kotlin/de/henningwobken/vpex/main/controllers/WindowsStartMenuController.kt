package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.InternalResource
import de.henningwobken.vpex.main.model.VpexConstants
import mslinks.ShellLink
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.nio.file.Files

class WindowsStartMenuController : Controller() {

    private val logger = KotlinLogging.logger {}
    private val currentJarController: CurrentJarController by inject()
    private val internalResourceController: InternalResourceController by inject()

    fun addVpexEntry() {
        if (VpexConstants.isWindows) {
            // Copy Icon
            val iconFile = File("${VpexConstants.userHome}/.vpex/vpex_icon.ico")
            if (!iconFile.exists()) {
                iconFile.parentFile.mkdirs()
                Files.copy(internalResourceController.getAsInputStream(InternalResource.ICON_32), iconFile.toPath())
            } else {
                logger.debug("Icon file exists already")
            }

            // Check if Icon is up to date and exit if it is
            val vpexLinkPath = "Microsoft\\Windows\\Start Menu\\Programs\\Vpex.lnk"
            val shortcutFile = File(System.getenv("APPDATA") + "\\$vpexLinkPath")
            logger.debug { "Using Appdatapath: ${shortcutFile.absolutePath}" }
            if (shortcutFile.exists()) {
                logger.debug { "Vpex Shortcut exists. Checking Destination" }
                val shellLink = ShellLink(shortcutFile)
                val target = shellLink.resolveTarget()
                val currentPath = currentJarController.currentPath
                if (target == currentPath) {
                    logger.debug { "Vpex Shortcut path is up to date" }
                    return
                } else {
                    logger.debug { "Vpex Path in Shortcut differs from current path. Updating:" }
                    logger.debug { "Old: $target" }
                    logger.debug { "New: $currentPath" }
                }
            }

            // Create entry
            logger.info { "Creating Windows Startmenu Entry." }
            val shellLink = ShellLink.createLink(currentJarController.currentPath)
                    .setName("Vpex")
                    .setIconLocation(iconFile.absolutePath)
            shellLink.saveTo(shortcutFile.absolutePath)
        } else {
            logger.warn { "Trying to create Windows Startmenu on non-windows system." }
        }
    }

    fun removeVpexEntry() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
