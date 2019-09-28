package de.henningwobken.vpex.main.xml

import java.io.Reader

class ProgressReader(private val reader: Reader, private val progressCallback: (charsRead: Int) -> Unit) : Reader() {

    private var charsRead = 0

    override fun close() {
        reader.close()
    }

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        charsRead += len
        progressCallback(charsRead)
        return reader.read(cbuf, off, len)
    }
}