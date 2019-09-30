package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.Settings
import javafx.scene.control.Alert
import javafx.scene.layout.Region
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.util.*

class SettingsController : Controller() {
    private val logger = KotlinLogging.logger {}
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
        properties.setProperty("autoUpdate", settings.autoUpdate.toString())
        properties.setProperty("proxyHost", settings.proxyHost)
        properties.setProperty("proxyPort", settings.proxyPort.toString())
        properties.setProperty("memoryIndicator", settings.memoryIndicator.toString())
        properties.setProperty("saveLock", settings.saveLock.toString())
        properties.setProperty("diskPagination", settings.diskPagination.toString())
        properties.setProperty("diskPaginationThreshold", settings.diskPaginationThreshold.toString())
        properties.setProperty("trustStore", settings.trustStore)
        properties.setProperty("trustStorePassword", settings.trustStorePassword)
        properties.store(configFile.outputStream(), "")
        applySettings(settings)
    }

    private fun loadSettings(): Settings {
        val settings = if (!configFile.exists()) {
            if (!configFile.parentFile.exists()) {
                configFile.parentFile.mkdir()
            }
            val defaultSettings = getDefaultSettings()
            saveSettings(defaultSettings)
            defaultSettings
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
                        properties.getProperty("paginationThreshold", "30000000").toInt(),
                        properties.getProperty("autoUpdate", "false") == "true",
                        properties.getProperty("proxyHost", ""),
                        properties.getProperty("proxyPort", "").toIntOrNull(),
                        properties.getProperty("memoryIndicator", "false") == "true",
                        properties.getProperty("saveLock", "false") == "true",
                        properties.getProperty("diskPagination", "false") == "true",
                        properties.getProperty("diskPaginationThreshold", "500").toInt(),
                        properties.getProperty("trustStore", ""),
                        properties.getProperty("trustStorePassword", "")

                )
            } catch (e: Exception) {
                logger.error { "Error while parsing settings." }
                e.printStackTrace()
                logger.error { "Deleting old config file." }
                val errorMessage = "There was an error loading the config file: " +
                        e.message +
                        "\nI deleted the old config file and replaced it with a new one with default settings."
                showAlert(Alert.AlertType.ERROR, "Error loading config", errorMessage)
                configFile.delete()
                loadSettings()
            }
        }
        validateSettings(settings)
        applySettings(settings)
        return settings
    }

    private fun applySettings(settings: Settings) {
        if (settings.trustStore.isNotEmpty()) {
            System.setProperty("javax.net.ssl.trustStore", settings.trustStore);
            System.setProperty("javax.net.ssl.trustStorePassword", settings.trustStorePassword);
        }
    }

    private fun getDefaultSettings(): Settings =
            Settings(
                    "./",
                    "./",
                    true,
                    4,
                    Locale.ENGLISH,
                    true,
                    1000000,
                    30000000,
                    false,
                    "",
                    null,
                    false,
                    false,
                    true,
                    500,
                    "",
                    ""
            )


    private fun validateSettings(settings: Settings) {
        val openerBaseFile = File(settings.openerBasePath).absoluteFile
        if (!openerBaseFile.exists() || !openerBaseFile.isDirectory) {
            val errorMessage = "Opener base path ${openerBaseFile.absolutePath} is not a directory or does not exist. Please replace it in the settings."
            showAlert(Alert.AlertType.ERROR, "Directory does not exist", errorMessage)
        }
        val schemaBaseFile = File(settings.schemaBasePath).absoluteFile
        if (!schemaBaseFile.exists() || !schemaBaseFile.isDirectory) {
            val errorMessage = "Schema base path ${schemaBaseFile.absolutePath} is not a directory or does not exist. Please replace it in the settings."
            showAlert(Alert.AlertType.ERROR, "Directory does not exist", errorMessage)
        }
        if (settings.trustStore.isNotEmpty()) {
            val trustStoreFile = File(settings.schemaBasePath).absoluteFile
            if (!trustStoreFile.exists() || !schemaBaseFile.isFile) {
                val errorMessage = "Trust store location ${schemaBaseFile.absolutePath} is not a file or does not exist. Please replace it in the settings."
                showAlert(Alert.AlertType.ERROR, "File does not exist", errorMessage)
            }
        }
    }

    private fun showAlert(alertType: Alert.AlertType, title: String, message: String) {
        logger.error { message }
        val alert = Alert(alertType, message)
        alert.title = title
        alert.dialogPane.minHeight = Region.USE_PREF_SIZE
        alert.showAndWait()
    }
}
