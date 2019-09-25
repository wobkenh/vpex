package de.henningwobken.vpex.updater.views

import de.henningwobken.vpex.updater.VpexUpdater
import de.henningwobken.vpex.updater.newJar
import de.henningwobken.vpex.updater.oldJar
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.image.Image
import javafx.scene.layout.Priority
import tornadofx.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

class UpdaterView : View("Updating VPEX") {

    private val statusTextProperty = SimpleStringProperty("Starting...")

    override val root: Parent = vbox {
        vgrow = Priority.ALWAYS
        hgrow = Priority.ALWAYS
        isFillWidth = true
        alignment = Pos.CENTER

        imageview(Image(VpexUpdater::class.java.classLoader.getResourceAsStream("vpex_logo.png"))) {
            isPreserveRatio = true
            fitHeight = 150.0
            maxHeight = 150.0
        }
        label(statusTextProperty)
        label()
        progressbar {
            maxWidth = Double.MAX_VALUE
        }
    }

    fun copy(parameters: List<String>) {

        statusTextProperty.set("Checking files...")
        if (parameters.size != 2) {
            statusTextProperty.set("Error. Please restart VPEX.")
            throw RuntimeException("Wrong number of arguments supplied. Need 2. Found ${parameters.size}.")
        }
        newJar = File(parameters[0])
        oldJar = File(parameters[1])

        if (!oldJar.exists() || !oldJar.isFile) {
            statusTextProperty.set("Error. Please restart VPEX.")
            throw RuntimeException("Old Jar @ ${oldJar.absolutePath} does not exist or is not a file.")
        }
        if (!newJar.exists() || !newJar.isFile) {
            statusTextProperty.set("Error. Please restart VPEX.")
            throw RuntimeException("New Jar @ ${newJar.absolutePath} does not exist or is not a file.")
        }
        println("Old Jar: $oldJar")
        println("New Jar: $newJar")
        println("Waiting for the old vpex instance to shut down...")

        // TODO: Insert args into service
        //      Read from service in view
        val thread = Thread {
            Thread.sleep(3000)
            Platform.runLater {
                try {
                    statusTextProperty.set("Moving jar...")
                    Files.move(newJar.toPath(), oldJar.toPath(), StandardCopyOption.REPLACE_EXISTING)

                    statusTextProperty.set("Restarting VPEX...")
                    ProcessBuilder("java", "-jar", oldJar.absolutePath).start()

                    exitProcess(0)
                } catch (e: Exception) {
                    e.printStackTrace()
                    error("Error moving files.", "The new version could not be copied to old location. Please restart the update.").showAndWait()
                    exitProcess(1)
                }
            }
        }.start()
        // TODO: Detect via process? (We got the old jar name...)
    }
}