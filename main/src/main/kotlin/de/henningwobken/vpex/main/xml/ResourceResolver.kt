package de.henningwobken.vpex.main.xml

import com.sun.org.apache.xerces.internal.dom.DOMInputImpl
import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.layout.Region
import javafx.stage.FileChooser
import org.w3c.dom.ls.LSInput
import org.w3c.dom.ls.LSResourceResolver
import tornadofx.*
import java.io.File
import java.io.InputStream


class ResourceResolver(private val basePath: String) : LSResourceResolver {

    override fun resolveResource(type: String?, namespaceURI: String?, publicId: String?, systemId: String?, baseURI: String?): LSInput {
        println("Resolving schema Type $type Namespace $namespaceURI publicid $publicId systemid $systemId base $baseURI")
        val inputStream: InputStream
        if (systemId == null) {
            alertFileMissing(type, namespaceURI, publicId, systemId, baseURI)
            val file = chooseFile(systemId)
            inputStream = file.inputStream()
        } else {
            var file = File(basePath).walk().find {
                it.name == "$systemId.xsd" || it.name == systemId
            }
            if (file == null && namespaceURI != null && namespaceURI.isNotEmpty()) {
                val alternativeName = namespaceURI.split(":").last()
                file = File(basePath).walk().find {
                    it.name == "$alternativeName.xsd" || it.name == alternativeName
                }
            }
            inputStream = if (file == null) {
                alertFileMissing(type, namespaceURI, publicId, systemId, baseURI)
                val chosenFile = chooseFile(systemId)
                chosenFile.inputStream()
            } else {
                file.inputStream()
            }
        }
        return DOMInputImpl(publicId, systemId, baseURI, inputStream, "UTF-8")
    }

    private fun chooseFile(systemId: String?): File {
        var isDone = false
        var file: File? = null
        Platform.runLater {
            val fileChooser = FileChooser()
            fileChooser.title = "Choose Schema file $systemId"
            fileChooser.initialDirectory = File(basePath).absoluteFile
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
