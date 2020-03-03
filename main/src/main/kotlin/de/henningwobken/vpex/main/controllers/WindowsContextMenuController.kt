package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.InternalResource
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.nio.file.Files

class WindowsContextMenuController : Controller() {

    private val logger = KotlinLogging.logger {}
    private val windowsRegistryController by inject<WindowsRegistryController>()
    private val internalResourceController by inject<InternalResourceController>()
    private val vpexLocation = "hkcu\\Software\\Classes\\*\\shell\\vpex"
    private val userHome = System.getProperty("user.home")
    private val wscriptPath = "C:\\Windows\\System32\\wscript.exe"
    private val command = "\\\"$wscriptPath\\\" \\\"$userHome\\.vpex\\receive.vbs\\\" \\\"%1\\\""
    private val commandUnescaped = "\"$wscriptPath\" \"$userHome\\.vpex\\receive.vbs\" \"%1\""


    fun addVpexContextMenuEntry() {
        if (windowsRegistryController.isWindows()) {
            logger.debug("Is Windows. Checking for Vpex entry")
            logger.debug("Checking for '*' classes entry")
            val hasVpexEntry = windowsRegistryController.readRegistry(vpexLocation) != null

            if (hasVpexEntry) {
                logger.debug("Vpex entry exists. Verifying.")
                val commandInRegistry = windowsRegistryController.readRegistryValue("$vpexLocation\\command", "")
                if (commandInRegistry == null || commandInRegistry != commandUnescaped) {
                    logger.debug("Registry Entry is outdated. Updating.")
                    logger.debug("Old entry: <$commandInRegistry>")
                    logger.debug("New entry: <$commandUnescaped>")
                    createRegistryEntry()
                } else {
                    logger.debug("Vpex registry entry is valid.")
                }
            } else {
                createRegistryEntry()
            }
        }
    }

    fun removeVpexContextMenuEntry() {
        if (windowsRegistryController.isWindows()) {
            // Registry Entries
            if (windowsRegistryController.deleteRegistryLocation(vpexLocation)) {
                logger.info("Vpex context menu entry successfully deleted from windows registry")
            } else {
                logger.error("Could not delete windows registry entry for vpex context menu entry")
            }
        }
    }

    private fun createRegistryEntry() {
        // Copy Icon
        val iconFile = File("$userHome/.vpex/vpex_icon.ico")
        if (!iconFile.exists()) {
            iconFile.parentFile.mkdirs()
            Files.copy(internalResourceController.getAsInputStream(InternalResource.ICON_32), iconFile.toPath())
        } else {
            logger.debug("Icon file exists already")
        }

        // Registry Entries
        if (windowsRegistryController.writeRegistryValue("$vpexLocation\\command", "", command)) {
            logger.info("Vpex context menu entry successfully created in windows registry")
        } else {
            logger.error("Could not add windows registry entry for vpex context menu entry")
        }
        if (windowsRegistryController.writeRegistryValue(vpexLocation, "", "Open with Vpex")) {
            logger.info("Vpex context menu entry label successfully changed in windows registry")
        } else {
            logger.error("Could not change windows registry entry for vpex context menu entry label")
        }
        if (windowsRegistryController.writeRegistryValue(vpexLocation, "Icon", iconFile.absolutePath)) {
            logger.info("Vpex context menu entry icon successfully set in windows registry")
        } else {
            logger.error("Could not change windows registry entry for vpex context menu entry icon")
        }
    }
}
