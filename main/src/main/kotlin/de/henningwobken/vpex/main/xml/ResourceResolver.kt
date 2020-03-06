package de.henningwobken.vpex.main.xml

import com.sun.org.apache.xerces.internal.dom.DOMInputImpl
import de.henningwobken.vpex.main.controllers.SettingsController
import de.henningwobken.vpex.main.views.SchemaChooserFragment
import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.layout.Region
import javafx.stage.FileChooser
import javafx.stage.StageStyle
import mu.KotlinLogging
import org.w3c.dom.ls.LSInput
import org.w3c.dom.ls.LSResourceResolver
import tornadofx.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap


class ResourceResolver(private val settingsController: SettingsController) : LSResourceResolver {

    private val logger = KotlinLogging.logger {}
    private val basePathList = settingsController.getSettings().schemaBasePathList

    init {
        logger.debug { "Initializing Schema File Resource Resolver" }
    }

    private val schemaFileMap: ConcurrentMap<String, File> = ConcurrentHashMap<String, File>()

    override fun resolveResource(type: String?, namespaceURI: String?, publicId: String?, systemId: String?, baseURI: String?): LSInput {
        logger.info { "Resolving schema Type $type Namespace $namespaceURI publicid $publicId systemid $systemId base $baseURI" }

        val key = type + namespaceURI + publicId + systemId + baseURI

        val schemaFile: File = if (schemaFileMap[key] != null) {
            // This condition is used to fasten up the schema lookup
            // Since schema files wont be dereferenced, we do not need to synchronize to read
            // which fastens up the process a bit when a lot of files with the same schema are validated
            schemaFileMap[key]!!
        } else {
            synchronized(schemaFileMap) {
                // Checking the schema map again in synchronized block since schema might now be resolved, e.g:
                // Thread A does not find key in map and enters synchronized. It asks the user for a schema file
                // Thread B does need the same schema and also could not find the key in the map as the user has not yet chosen a file
                // It now waits for the user to chose a file since it can't enter the synchronized block while user is choosing.
                // Thread A is done, the file in now in the map
                // Thread B now can find the key in the map and does not need to ask the user again
                val schemaFileTmp = if (schemaFileMap[key] != null) {
                    schemaFileMap[key]!! // Was checked in condition
                } else if (systemId == null && namespaceURI == null) {
                    alertFileMissing(type, namespaceURI, publicId, systemId, baseURI)
                    chooseFile(systemId)
                } else {
                    val files = mutableListOf<File>()
                    for (basePath in basePathList) {
                        if (systemId != null && systemId.isNotEmpty()) {
                            files.addAll(File(basePath).walk().filter {
                                it.name.contains(systemId, true)
                            })
                        }
                        if (namespaceURI != null && namespaceURI.isNotEmpty()) {
                            val foundPaths = files.map { it.absolutePath }
                            val alternativeName = namespaceURI.split(":").last()
                            files.addAll(File(basePath).walk().filter {
                                it.name.contains(alternativeName, true)
                            }.filter { !foundPaths.contains(it.absolutePath) }) // Prevent duplicates
                        }
                    }
                    when (files.size) {
                        0 -> {
                            alertFileMissing(type, namespaceURI, publicId, systemId, baseURI)
                            val chosenFile = chooseFile(systemId)
                            chosenFile
                        }
                        1 -> files[0]
                        else -> {
                            val chosenFile = chooseFile(SchemaDescriptor(type, namespaceURI, publicId, systemId, baseURI), files)
                            chosenFile
                        }
                    }
                }
                schemaFileMap[key] = schemaFileTmp
                schemaFileTmp
            }
        }

        return DOMInputImpl(publicId, systemId, baseURI, schemaFile.inputStream(), "UTF-8")
    }

    private fun chooseFile(schemaDescriptor: SchemaDescriptor, files: List<File>): File {
        var isDone = false
        var file: File? = null
        Platform.runLater {
            val resultFragment = find<SchemaChooserFragment>()
            val stage = resultFragment.openWindow(stageStyle = StageStyle.UTILITY)
            if (stage != null) {
                stage.toFront()
            }
            resultFragment.chooseFile(files, schemaDescriptor) {
                file = it
                stage?.close()
                isDone = true
            }
        }
        while (!isDone) {
            Thread.sleep(100)
        }
        return requireNotNull(file)
    }

    private fun chooseFile(systemId: String?): File {
        var isDone = false
        var file: File? = null
        Platform.runLater {
            val fileChooser = FileChooser()
            fileChooser.title = "Choose Schema file $systemId"
            val initialDirectory = if (basePathList.isEmpty()) {
                settingsController.getOpenerBasePath()
            } else {
                basePathList[0]
            }
            fileChooser.initialDirectory = File(initialDirectory).absoluteFile
            file = fileChooser.showOpenDialog(FX.primaryStage) ?: throw RuntimeException("No file chosen. Abort.")
            isDone = true
        }
        while (!isDone) {
            Thread.sleep(100)
        }
        return requireNotNull(file)
    }

    private fun alertFileMissing(type: String?, namespaceURI: String?, publicId: String?, systemId: String?, baseURI: String?) {
        var isDone = false
        Platform.runLater {
            val alert = Alert(Alert.AlertType.WARNING, "The validator asked for the following schema, please locate it yourself:\n" +
                    "Type $type\n" +
                    "NamespaceURI $namespaceURI\n" +
                    "Public ID $publicId\n" +
                    "System ID $systemId\n" +
                    "Base $baseURI")
            alert.title = "Error resolving schema name/id"
            alert.dialogPane.minHeight = Region.USE_PREF_SIZE
            alert.showAndWait()
            isDone = true
        }
        while (!isDone) {
            Thread.sleep(100)
        }
    }

}
