package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.InternalResource
import de.henningwobken.vpex.main.views.ProxySettingsFragment
import javafx.application.Platform
import javafx.scene.control.ButtonType
import javafx.stage.StageStyle
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.net.URLConnection
import kotlin.system.exitProcess

class UpdateController : Controller() {

    companion object {
        const val url: String = "https://simplex24.de/vpex"
    }

    private val logger = KotlinLogging.logger {}
    private val settingsController: SettingsController by inject()
    private val internalResourceController: InternalResourceController by inject()
    private val currentJarController: CurrentJarController by inject()
    private val currentJar = currentJarController.currentJar
    private val newJar = File(System.getProperty("user.home") + "/.vpex/tmp.jar")
    private val updaterJar = File(System.getProperty("user.home") + "/.vpex/updater.jar")

    val currentVersion: String = initCurrentVersion()

    var availableVersions: List<String> = listOf()
        get() {
            logger.debug { "Getting available Versions" }
            if (field.isEmpty()) {
                field = loadAvailableVersions()
            }
            return field
        }

    fun updateAvailable(): Boolean {
        if (availableVersions.isEmpty()) {
            return false
        }

        val currentVersion = currentVersion.split(".")
        val currentMajorVersion = currentVersion[0].toInt()
        val currentMinorVersion = currentVersion[1].toInt()

        val newestVersion = availableVersions.last().split(".")
        val newestMajorVersion = newestVersion[0].toInt()
        val newestMinorVersion = newestVersion[1].toInt()

        return when {
            newestMajorVersion > currentMajorVersion -> true
            newestMajorVersion == currentMajorVersion -> currentMinorVersion < newestMinorVersion
            else -> false
        }
    }

    fun downloadUpdate(
            progressCallback: (progress: Int, max: Int) -> Unit,
            finishCallback: (version: String) -> Unit,
            targetVersion: String = ""
    ): ProgressListener {

        val version = if (targetVersion.isEmpty()) availableVersions.last() else targetVersion
        val progressListener = ProgressListener(progressCallback, finishCallback = {
            finishCallback(version)
        })

        Thread {
            if (newJar.exists()) {
                newJar.delete()
            }

            if (!updaterJar.exists()) {
                val updaterUrlConnection = getUrlConnection("$url/updater.jar")
                updaterUrlConnection.connect()
                readFile(updaterUrlConnection.getInputStream(), FileOutputStream(updaterJar), progressListener, updaterUrlConnection.contentLength)
                updaterUrlConnection.getInputStream().close()
            }

            val urlConnection = getUrlConnection("$url/vpex$version.jar")
            urlConnection.connect()
            readFile(urlConnection.getInputStream(), FileOutputStream(newJar), progressListener, urlConnection.contentLength)
            urlConnection.getInputStream().close()

            progressListener.finish()
        }.start()

        return progressListener
    }

    fun applyUpdate() {
        ProcessBuilder("java", "-jar", updaterJar.absolutePath, newJar.absolutePath, currentJar.absolutePath).start()
        exitProcess(0)
    }

    /**
     * Controls the whole update workflow and informs the UI about changes
     * If the connections fails, a dialog will appear to ask the user if he is behind a proxy.
     * From there, the updateRoutine might be called again if the user chooses to
     */
    fun updateRoutine(
            downloadStartedCallback: () -> Unit,
            downloadProgressCallback: (progress: Int, max: Int) -> Unit,
            downloadFinishedCallback: () -> Unit,
            noUpdateCallback: () -> Unit
    ) {
        logger.info { "Checking for updates" }
        val availableVersions = loadAvailableVersions {
            updateRoutine(downloadStartedCallback, downloadProgressCallback, downloadFinishedCallback, noUpdateCallback)
        }
        if (availableVersions.isEmpty()) {
            noUpdateCallback()
            return
        }
        this.availableVersions = availableVersions
        if (updateAvailable()) {
            logger.info { "Update available. Downloading." }
            downloadStartedCallback()
            downloadUpdate(progressCallback = downloadProgressCallback, finishCallback = { version ->
                logger.info { "Download for version $version finished." }
                downloadFinishedCallback()
                Platform.runLater {
                    confirm("New Version", "New version $version has been downloaded. Restart?", ButtonType.OK, ButtonType.CANCEL, actionFn = {
                        applyUpdate()
                    })
                }
            })
        } else {
            logger.info { "Up to date." }
            noUpdateCallback()
        }
    }

    private fun readFile(inputStream: InputStream, outputStream: OutputStream, progressListener: ProgressListener, max: Int) {
        val dataBuffer = ByteArray(1024)
        var bytesRead = 0
        var bytesReadTotal = 0
        while (bytesRead != -1) {
            bytesRead = inputStream.read(dataBuffer, 0, 1024)
            if (bytesRead > 0) {
                outputStream.write(dataBuffer, 0, bytesRead)
                bytesReadTotal += bytesRead
                progressListener.progress(bytesReadTotal, max)
            }
        }
        outputStream.flush()
    }

    private fun getUrlConnection(url: String): URLConnection {
        val urlObject = URL(url)
        val connection = if (settingsController.getSettings().hasProxy()) {
            urlObject.openConnection(settingsController.getSettings().getProxy())
        } else {
            urlObject.openConnection()
        }
        connection.connectTimeout = 10000
        return connection
    }

    private fun loadAvailableVersions(onErrorRetryCallback: (() -> Unit)? = null): List<String> {
        val urlConnection = getUrlConnection("$url/versions.txt")
        return try {
            logger.info { "Trying to connect" }
            urlConnection.connect()
            urlConnection.getInputStream().bufferedReader().use { it.readText() }
                    .split("\n").filter(String::isNotEmpty)
        } catch (e: Exception) {
            if (settingsController.getSettings().ignoreAutoUpdateError) {
                return listOf()
            }
            if (onErrorRetryCallback != null) {
                Platform.runLater {
                    val resultFragment = find<ProxySettingsFragment>()
                    resultFragment.retryCallback = onErrorRetryCallback
                    val stage = resultFragment.openWindow(stageStyle = StageStyle.UTILITY)
                    stage?.requestFocus()
                }
            }
            listOf()
        }
    }

    private fun initCurrentVersion(): String {
        val version = internalResourceController.getAsString(InternalResource.VERSION)
        if (version == "\${project.version}") { // Local
            return "0.0"
        }
        return version
    }

    class ProgressListener(
            private val progressCallback: (progress: Int, max: Int) -> Unit,
            private val finishCallback: () -> Unit
    ) {
        fun progress(progress: Int, max: Int) {
            progressCallback(progress, max)
        }

        fun finish() {
            finishCallback()
        }
    }
}
