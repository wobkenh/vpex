package de.henningwobken.vpex.main.xml

import java.io.InputStream

class ProgressInputStream(private val inputStream: InputStream, private val progressCallback: (bytesRead: Int) -> Unit) : InputStream() {

    private var bytesRead = 0

    override fun read(): Int {
        return inputStream.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        bytesRead += len
        progressCallback(bytesRead)
        return inputStream.read(b, off, len)
    }

    override fun skip(n: Long): Long {
        return inputStream.skip(n)
    }

    override fun available(): Int {
        return inputStream.available()
    }

    override fun reset() {
        inputStream.reset()
    }

    override fun close() {
        inputStream.close()
    }

    override fun mark(readlimit: Int) {
        inputStream.mark(readlimit)
    }

    override fun markSupported(): Boolean {
        return inputStream.markSupported()
    }
}