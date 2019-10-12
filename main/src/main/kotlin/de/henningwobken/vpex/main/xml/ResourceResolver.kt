package de.henningwobken.vpex.main.xml

import com.sun.org.apache.xerces.internal.dom.DOMInputImpl
import de.henningwobken.vpex.main.views.SchemaChooserFragment
import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.layout.Region
import javafx.stage.FileChooser
import javafx.stage.StageStyle
import org.w3c.dom.ls.LSInput
import org.w3c.dom.ls.LSResourceResolver
import tornadofx.*
import java.io.File
import java.io.InputStream


class ResourceResolver(private val basePathList: List<String>) : LSResourceResolver {

    override fun resolveResource(type: String?, namespaceURI: String?, publicId: String?, systemId: String?, baseURI: String?): LSInput {
        println("Resolving schema Type $type Namespace $namespaceURI publicid $publicId systemid $systemId base $baseURI")
        val inputStream: InputStream
        if (systemId == null && namespaceURI == null) {
            alertFileMissing(type, namespaceURI, publicId, systemId, baseURI)
            val file = chooseFile(systemId)
            inputStream = file.inputStream()
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
            inputStream = when {
                files.size == 0 -> {
                    alertFileMissing(type, namespaceURI, publicId, systemId, baseURI)
                    val chosenFile = chooseFile(systemId)
                    chosenFile.inputStream()
                }
                files.size == 1 -> files[0].inputStream()
                else -> {
                    val chosenFile = chooseFile(SchemaDescriptor(type, namespaceURI, publicId, systemId, baseURI), files)
                    chosenFile.inputStream()
                }
            }
        }
        return DOMInputImpl(publicId, systemId, baseURI, inputStream, "UTF-8")
    }

    private fun chooseFile(schemaDescriptor: SchemaDescriptor, files: List<File>): File {
        var isDone = false
        var file: File? = null
        Platform.runLater {
            val resultFragment = find<SchemaChooserFragment>()
            val stage = resultFragment.openWindow(stageStyle = StageStyle.UTILITY)
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
            fileChooser.initialDirectory = File(basePathList[0]).absoluteFile
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
