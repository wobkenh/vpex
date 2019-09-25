package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.InternalResource
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
    private val logger = KotlinLogging.logger {}
    private val url: String = "https://simplex24.de/vpex"
    private val settingsController: SettingsController by inject()
    private val internalResourceController: InternalResourceController by inject()
    private val currentJar = initCurrentJar()
    private val newJar = File(System.getProperty("user.home") + "/.vpex/tmp.jar")
    private val updaterJar = File(System.getProperty("user.home") + "/.vpex/updater.jar")

    val currentVersion: String = initCurrentVersion()

    var availableVersions: List<String> = listOf()
        get() {
            if (field.isEmpty()) {
                field = loadAvailableVersions()
            }
            return field
        }

    fun updateAvailable(): Boolean {
        availableVersions = loadAvailableVersions()

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
            finishCallback: () -> Unit,
            targetVersion: String = ""
    ): ProgressListener {

        val version = if (targetVersion.isEmpty()) availableVersions.last() else targetVersion
        val progressListener = ProgressListener(progressCallback, finishCallback)

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
        return if (settingsController.getSettings().hasProxy()) {
            urlObject.openConnection(settingsController.getSettings().getProxy())
        } else {
            urlObject.openConnection()
        }
    }

    private fun loadAvailableVersions(): List<String> {
        val url = URL("$url/versions.txt")
        val urlConnection = if (settingsController.getSettings().hasProxy()) {
            url.openConnection(settingsController.getSettings().getProxy())
        } else {
            url.openConnection()
        }
        urlConnection.connect()
        return try {
            urlConnection.getInputStream().bufferedReader().use { it.readText() }
                    .split("\n").filter(String::isNotEmpty)
        } catch (e: Exception) {
            logger.error("Could not find versions.txt. Assuming temporary connection outage", e)
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

    private fun initCurrentJar(): File {
        val file = File(UpdateController::class.java.protectionDomain.codeSource.location.path)
        logger.info("Current location: ${file.absolutePath}")
        if (file.exists() && file.isDirectory) {
            val dummyFile = File(file, "dummy.jar")
            logger.info("In Dev mode. Replacing current jar with dummy at ${dummyFile.absolutePath}.")
            FileOutputStream(dummyFile).close()
            return dummyFile
        }
        return file
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
