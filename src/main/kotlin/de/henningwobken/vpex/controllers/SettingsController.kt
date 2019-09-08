package de.henningwobken.vpex.controllers

import de.henningwobken.vpex.model.Settings
import tornadofx.*
import java.io.File
import java.nio.file.Files
import java.util.*

class SettingsController() : Controller() {

    private val configFile = File(System.getProperty("user.home") + "/.vpex/config.properties")
    private lateinit var settings: Settings

    init {
        settings = loadSettings()
    }

    public fun getSettings(): Settings {
        return settings
    }

    public fun saveSettings(settings: Settings) {
        this.settings = settings
        val properties = Properties()
        properties.setProperty("openerBasePath", settings.openerBasePath)
        properties.setProperty("schemaBasePath", settings.schemaBasePath)
        properties.store(configFile.outputStream(), "")
    }

    private fun loadSettings(): Settings {
        return if (!configFile.exists()) {
            if (!configFile.parentFile.exists()) {
                configFile.parentFile.mkdir()
            }
            Files.write(configFile.toPath(), listOf("openerBasePath=", "schemaBasePath="))
            Settings("", "")
        } else {
            val properties = Properties()
            properties.load(configFile.inputStream())
            Settings(
                    properties.getProperty("openerBasePath", ""),
                    properties.getProperty("schemaBasePath", "")
            )
        }
    }
}