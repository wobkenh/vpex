package de.henningwobken.vpex.xml

import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException

class XmlErrorHandler : ErrorHandler {
    override fun warning(exception: SAXParseException?) {
        exception?.printStackTrace()
    }

    override fun error(exception: SAXParseException?) {
        exception?.printStackTrace()
    }

    override fun fatalError(exception: SAXParseException?) {
        exception?.printStackTrace()
    }
}