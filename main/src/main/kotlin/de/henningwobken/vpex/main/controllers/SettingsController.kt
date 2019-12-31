package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.Settings
import de.henningwobken.vpex.main.model.VpexConstants
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
    private val contextMenuDefault = VpexConstants.isWindows


    init {
        settings = loadSettings()
        loadOpenerBasePath()
    }

    fun getOpenerBasePath(): String {
        return this.openerBasePath;
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
                        properties.getProperty("schemaBasePath", "./").split(","),
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
                        properties.getProperty("trustStorePassword", ""),
                        properties.getProperty("insecure", "false") == "true",
                        properties.getProperty("contextMenu", contextMenuDefault.toString()) == "true"

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
        if (settings.insecure) {
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
            val sc = SSLContext.getInstance("SSL")
            sc.init(null, dummyTrustManager, java.security.SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory);
        }
    }

    private fun getDefaultSettings(): Settings =
            Settings(
                    schemaBasePathList = listOf("./"),
                    wrapText = true,
                    prettyPrintIndent = 4,
                    locale = Locale.ENGLISH,
                    pagination = true,
                    pageSize = 1000000,
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
                    contextMenu = contextMenuDefault
            )

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
