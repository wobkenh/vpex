package de.henningwobken.vpex.controllers

import de.henningwobken.vpex.model.Settings
import tornadofx.*
import java.io.File
import java.nio.file.Files
import java.util.*

class SettingsController() : Controller() {

    private val configFile = File(System.getProperty("user.home") + "/.vpex/config.properties")
    private var settings: Settings

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
        properties.setProperty("wrapText", settings.wrapText.toString())
        properties.setProperty("prettyPrintIndent", settings.prettyPrintIndent.toString())
        properties.setProperty("locale", settings.locale.toLanguageTag())
        properties.store(configFile.outputStream(), "")
    }

    private fun loadSettings(): Settings {
        return if (!configFile.exists()) {
            if (!configFile.parentFile.exists()) {
                configFile.parentFile.mkdir()
            }
            Files.write(configFile.toPath(), listOf("openerBasePath=", "schemaBasePath=", "wrapText=true", "prettyPrintIndent=4"))
            Settings("./", "./", true, 4, Locale.ENGLISH)
        } else {
            val properties = Properties()
            properties.load(configFile.inputStream())
            try {
                Settings(
                        properties.getProperty("openerBasePath", "./"),
                        properties.getProperty("schemaBasePath", "./"),
                        properties.getProperty("wrapText", "true") == "true",
                        properties.getProperty("prettyPrintIndent", "4").toInt(),
                        Locale.forLanguageTag(properties.getProperty("locale", "en"))
                )
            } catch (e: Exception) {
                println("Error while parsing settings.")
                e.printStackTrace()
                println("Deleting old config file.")
                configFile.delete()
                loadSettings()
            }
        }
    }
}