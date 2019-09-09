package de.henningwobken.vpex.controllers

import de.henningwobken.vpex.Vpex
import de.henningwobken.vpex.model.InternalResource
import javafx.scene.image.Image
import tornadofx.*

class InternalResourceController : Controller() {
    fun getAsImage(resource: InternalResource): Image {
        return Image(Vpex::class.java.classLoader.getResourceAsStream(resource.filename))
    }

    fun getAsString(resource: InternalResource): String {
        val inputStream = Vpex::class.java.classLoader.getResourceAsStream(resource.filename) ?: return ""
        val bufferedReader = inputStream.bufferedReader()
        val content = StringBuilder()
        bufferedReader.use {
            var line = it.readLine()
            while (line != null) {
                content.append(line)
                line = it.readLine()
            }
        }
        return content.toString()
    }

}