package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.Settings
import de.henningwobken.vpex.main.model.SyntaxHighlightingColorScheme
import de.henningwobken.vpex.main.model.VpexConstants
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.control.Alert
import javafx.scene.layout.Region
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SettingsController : Controller() {
    private val logger = KotlinLogging.logger {}
    private val configFile = File(VpexConstants.vpexHome + "/config.properties")
    private val openerBasePathFile = File(VpexConstants.vpexHome + "/basepath")
    private var settings: Settings
    private var openerBasePath: String = "./"
    val settingsProperty = SimpleObjectProperty<Settings>()

    companion object {
        val DEFAULT_SETTINGS: Settings = Settings(
                schemaBasePathList = listOf("./"),
                wrapText = true,
                prettyPrintIndent = 4,
                locale = Locale.ENGLISH,
                pagination = true,
                pageSize = 500000,
                paginationThreshold = 30000000,
                autoUpdate = true,
                proxyHost = "",
                proxyPort = null,
                memoryIndicator = true,
                saveLock = false,
                diskPagination = true,
                diskPaginationThreshold = 500,
                trustStore = "",
                trustStorePassword = "",
                insecure = false,
                contextMenu = VpexConstants.isWindows,
                syntaxHighlighting = true,
                syntaxHighlightingColorScheme = SyntaxHighlightingColorScheme.DEFAULT,
                startMenu = VpexConstants.isWindows,
                desktopIcon = VpexConstants.isWindows,
                ignoreAutoUpdateError = false
        )
    }


    init {
        settings = loadSettings()
        settingsProperty.set(settings)
        loadOpenerBasePath()
    }

    fun getOpenerBasePath(): String {
        return this.openerBasePath
    }

    fun setOpenerBasePath(openerBasePath: String) {
        saveOpenerBasePath(openerBasePath)
    }

    private fun loadOpenerBasePath() {
        if (!openerBasePathFile.exists()) {
            logger.info("openerBasePath File does not exist")
            saveOpenerBasePath("./")
        } else if (openerBasePathFile.isDirectory) {
            showAlert(Alert.AlertType.WARNING, "Opener Path", "Opener path file "
                    + openerBasePathFile.absolutePath
                    + " is a directory, but needs to be a file. Please delete the directory."
            )
        } else {
            val lines = Files.readAllLines(openerBasePathFile.toPath())
            if (lines.isEmpty()) {
                logger.info("openerBasePath File is empty")
                saveOpenerBasePath("./")
            } else {
                this.openerBasePath = Files.readAllLines(openerBasePathFile.toPath())[0];
            }
        }
        val file = File(this.openerBasePath)
        if (!file.exists()) {
            logger.warn("OpenerBasePath {} does not exist. Resetting to default.", this.openerBasePath)
            saveOpenerBasePath("./")
        } else if (!file.isDirectory) {
            logger.warn("OpenerBasePath {} is not a directory. Resetting to default.", this.openerBasePath)
            saveOpenerBasePath("./")
        }
        logger.debug("openerBasePath is {}", openerBasePath)
    }

    private fun saveOpenerBasePath(openerBasePath: String) {
        if (openerBasePathFile.isDirectory) {
            logger.warn("Cant write openerBasePath to file because it is a directory")
        } else {
            logger.debug("Writing openerBasePath {} to file at {}", openerBasePath, openerBasePathFile.absolutePath)
            Files.write(openerBasePathFile.toPath(), listOf(openerBasePath))
            this.openerBasePath = openerBasePath
        }
    }

    fun getSettings(): Settings {
        return settings
    }

    fun saveSettings(settings: Settings) {
        this.settings = settings
        settingsProperty.set(settings)
        val properties = Properties()
        properties.setProperty("schemaBasePath", settings.schemaBasePathList.joinToString(","))
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
        properties.setProperty("insecure", settings.insecure.toString())
        properties.setProperty("contextMenu", settings.contextMenu.toString())
        properties.setProperty("syntaxHighlighting", settings.syntaxHighlighting.toString())
        properties.setProperty("syntaxHighlightingColorScheme", settings.syntaxHighlightingColorScheme.name)
        properties.setProperty("startMenu", settings.startMenu.toString())
        properties.setProperty("desktopIcon", settings.desktopIcon.toString())
        properties.setProperty("ignoreAutoUpdateError", settings.ignoreAutoUpdateError.toString())
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
                        parseSchemaBasePath(properties),
                        properties.getProperty("wrapText", DEFAULT_SETTINGS.wrapText.toString()) == "true",
                        properties.getProperty("prettyPrintIndent", DEFAULT_SETTINGS.prettyPrintIndent.toString()).toInt(),
                        Locale.forLanguageTag(properties.getProperty("locale", DEFAULT_SETTINGS.locale.toLanguageTag())),
                        properties.getProperty("pagination", DEFAULT_SETTINGS.pagination.toString()) == "true",
                        properties.getProperty("pageSize", DEFAULT_SETTINGS.pageSize.toString()).toInt(),
                        properties.getProperty("paginationThreshold", DEFAULT_SETTINGS.paginationThreshold.toString()).toInt(),
                        properties.getProperty("autoUpdate", DEFAULT_SETTINGS.autoUpdate.toString()) == "true",
                        properties.getProperty("proxyHost", DEFAULT_SETTINGS.proxyHost),
                        properties.getProperty("proxyPort", DEFAULT_SETTINGS.proxyPort.toString()).toIntOrNull(),
                        properties.getProperty("memoryIndicator", DEFAULT_SETTINGS.memoryIndicator.toString()) == "true",
                        properties.getProperty("saveLock", DEFAULT_SETTINGS.saveLock.toString()) == "true",
                        properties.getProperty("diskPagination", DEFAULT_SETTINGS.diskPagination.toString()) == "true",
                        properties.getProperty("diskPaginationThreshold", DEFAULT_SETTINGS.diskPaginationThreshold.toString()).toInt(),
                        properties.getProperty("trustStore", DEFAULT_SETTINGS.trustStore),
                        properties.getProperty("trustStorePassword", DEFAULT_SETTINGS.trustStorePassword),
                        properties.getProperty("insecure", DEFAULT_SETTINGS.insecure.toString()) == "true",
                        properties.getProperty("contextMenu", DEFAULT_SETTINGS.contextMenu.toString()) == "true",
                        properties.getProperty("syntaxHighlighting", DEFAULT_SETTINGS.syntaxHighlighting.toString()) == "true",
                        SyntaxHighlightingColorScheme.valueOf(properties.getProperty("syntaxHighlightingColorScheme", DEFAULT_SETTINGS.syntaxHighlightingColorScheme.name)),
                        properties.getProperty("startMenu", DEFAULT_SETTINGS.startMenu.toString()) == "true",
                        properties.getProperty("desktopIcon", DEFAULT_SETTINGS.desktopIcon.toString()) == "true",
                        properties.getProperty("ignoreAutoUpdateError", DEFAULT_SETTINGS.ignoreAutoUpdateError.toString()) == "true"
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

    private fun parseSchemaBasePath(properties: Properties): List<String> {
        return properties.getProperty("schemaBasePath", "./")
                .split(",")
                .filter { it.isNotEmpty() }
    }

    private fun applySettings(settings: Settings) {
        if (settings.trustStore.isNotEmpty()) {
            System.setProperty("javax.net.ssl.trustStore", settings.trustStore)
            System.setProperty("javax.net.ssl.trustStorePassword", settings.trustStorePassword)
        }
        if (settings.insecure) {
            disableSSLSecurity()
        }
    }

    fun disableSSLSecurity() {
        logger.warn("Disabling SSL security")
        val dummyTrustManager = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {
            }

            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {
            }

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate>? {
                return null
            }
        })
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, dummyTrustManager, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
    }

    private fun getDefaultSettings(): Settings = DEFAULT_SETTINGS

    private fun validateSettings(settings: Settings) {
        for (schemaBasePath in settings.schemaBasePathList) {
            val schemaBaseFile = File(schemaBasePath).absoluteFile
            if (!schemaBaseFile.exists() || !schemaBaseFile.isDirectory) {
                val errorMessage = "Schema base path ${schemaBaseFile.absolutePath} is not a directory or does not exist. Please replace it in the settings."
                showAlert(Alert.AlertType.ERROR, "Directory does not exist", errorMessage)
            }
        }
        if (settings.trustStore.isNotEmpty()) {
            val trustStoreFile = File(settings.trustStore).absoluteFile
            if (!trustStoreFile.exists() || !trustStoreFile.isFile) {
                val errorMessage = "Trust store location ${trustStoreFile.absolutePath} is not a file or does not exist. Please replace it in the settings."
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
