package de.henningwobken.vpex.main.xml

import java.io.InputStream

/**
 * Progress stream that informs the creator about the bytes read by every read operation.
 * The bytes read only refer to the single read operation.
 */
class DiffProgressInputStream(inputStream: InputStream, private val progressCallback: (bytesRead: Int) -> Unit) : ProgressInputStream(inputStream) {
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = inputStream.read(b, off, len)
        progressCallback(read)
        return read
    }
}
