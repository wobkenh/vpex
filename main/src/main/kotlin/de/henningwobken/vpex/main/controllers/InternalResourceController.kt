package de.henningwobken.vpex.main.controllers

import afester.javafx.svg.SvgLoader
import de.henningwobken.vpex.main.Vpex
import de.henningwobken.vpex.main.model.InternalResource
import javafx.scene.Group
import javafx.scene.image.Image
import tornadofx.*
import java.io.InputStream
import java.net.URI

class InternalResourceController : Controller() {

    fun getAsSvg(resource: InternalResource): Group {
        return SvgLoader().loadSvg(Vpex::class.java.classLoader.getResourceAsStream(resource.filename))
    }

    fun getAsSvg(resource: InternalResource, color: String): Group {
        return SvgLoader().loadSvg(getAsString(resource).replace(Regex("fill=\"#[0-9A-F]{6}\""), "fill=\"$color\"").byteInputStream())
    }

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

    fun getAsStrings(resource: InternalResource): List<String> {
        val inputStream = Vpex::class.java.classLoader.getResourceAsStream(resource.filename) ?: return listOf()
        val bufferedReader = inputStream.bufferedReader()
        val content = mutableListOf<String>()
        bufferedReader.use {
            var line = it.readLine()
            while (line != null) {
                content.add(line)
                line = it.readLine()
            }
        }
        return content
    }

    fun getAsInputStream(resource: InternalResource): InputStream {
        return Vpex::class.java.classLoader.getResourceAsStream(resource.filename)!!
    }

    fun getAsResource(resource: InternalResource): String {
        return (Vpex::class.java.classLoader.getResource(resource.filename)
                ?: throw RuntimeException("Could not find internal Resource " + resource.filename)).toExternalForm()
    }

    fun getAsURI(resource: InternalResource): URI {
        return (Vpex::class.java.classLoader.getResource(resource.filename)
                ?: throw RuntimeException("Could not find internal Resource " + resource.filename)).toURI()
    }

}
