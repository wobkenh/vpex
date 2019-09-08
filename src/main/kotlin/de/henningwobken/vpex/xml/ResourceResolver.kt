package de.henningwobken.vpex.xml

import com.sun.org.apache.xerces.internal.dom.DOMInputImpl
import org.w3c.dom.ls.LSInput
import org.w3c.dom.ls.LSResourceResolver
import java.io.File
import java.util.*


class ResourceResolver(private val basePath: String) : LSResourceResolver {

    override fun resolveResource(type: String, namespaceURI: String, publicId: String, systemId: String, baseURI: String): LSInput {
        println("Type $type Namespace $namespaceURI publicid $publicId systemid $systemId base $baseURI")
        val resourceAsStream = File(buildPath(systemId)).inputStream()
        Objects.requireNonNull<Any>(resourceAsStream, String.format("Could not find the specified xsd file: %s", systemId))
        return DOMInputImpl(publicId, systemId, baseURI, resourceAsStream, "UTF-8")
    }

    private fun buildPath(systemId: String): String {
        return String.format("%s/%s", basePath, systemId)
    }
}