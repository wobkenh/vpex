package de.henningwobken.vpex.controllers

import de.henningwobken.vpex.model.Settings
import tornadofx.*
import java.io.File
import java.nio.file.Files
import java.util.*

class SettingsController : Controller() {

    private val configFile = File(System.getProperty("user.home") + "/.vpex/config.properties")
    private var settings: Settings

    init {
        settings = loadSettings()
    }

    fun getSettings(): Settings {
        return settings
    }

    fun saveSettings(settings: Settings) {
        this.settings = settings
        val properties = Properties()
        properties.setProperty("openerBasePath", settings.openerBasePath)
        properties.setProperty("schemaBasePath", settings.schemaBasePath)
        properties.setProperty("wrapText", settings.wrapText.toString())
        properties.setProperty("prettyPrintIndent", settings.prettyPrintIndent.toString())
        properties.setProperty("locale", settings.locale.toLanguageTag())
        properties.setProperty("pagination", settings.pagination.toString())
        properties.setProperty("pageSize", settings.pageSize.toString())
        properties.setProperty("paginationThreshold", settings.paginationThreshold.toString())
        properties.store(configFile.outputStream(), "")
    }

    private fun loadSettings(): Settings {
        return if (!configFile.exists()) {
            if (!configFile.parentFile.exists()) {
                configFile.parentFile.mkdir()
            }
            Files.write(configFile.toPath(), listOf(
                    "openerBasePath=",
                    "schemaBasePath=",
                    "wrapText=true",
                    "prettyPrintIndent=4",
                    "locale=en",
                    "pagination=true",
                    "pageSize=1000000",
                    "paginationThreshold=30000000"
            ))
            Settings(
                    "./",
                    "./",
                    true,
                    4,
                    Locale.ENGLISH,
                    true,
                    1000000,
                    30000000
            )
        } else {
            val properties = Properties()
            properties.load(configFile.inputStream())
            try {
                Settings(
                        properties.getProperty("openerBasePath", "./"),
                        properties.getProperty("schemaBasePath", "./"),
                        properties.getProperty("wrapText", "true") == "true",
                        properties.getProperty("prettyPrintIndent", "4").toInt(),
                        Locale.forLanguageTag(properties.getProperty("locale", "en")),
                        properties.getProperty("pagination", "true") == "true",
                        properties.getProperty("pageSize", "1000000").toInt(),
                        properties.getProperty("paginationThreshold", "30000000").toInt()
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