package de.henningwobken.vpex.main.xml

import java.io.InputStream

/**
 * Progress stream that informs the creator about the total bytes read on every read operation.
 * The bytes read is the sum of all bytes read by all read operations on this stream.
 */
class TotalProgressInputStream(inputStream: InputStream, private val progressCallback: (bytesRead: Long) -> Unit) : ProgressInputStream(inputStream) {
    private var bytesRead = 0L

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        bytesRead += len
        progressCallback(bytesRead)
        return inputStream.read(b, off, len)
    }
}
