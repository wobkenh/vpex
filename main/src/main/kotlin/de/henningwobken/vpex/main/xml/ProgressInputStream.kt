package de.henningwobken.vpex.main.xml

import java.io.InputStream

abstract class ProgressInputStream(protected val inputStream: InputStream) : InputStream() {

    override fun read(): Int {
        return inputStream.read()
    }

    abstract override fun read(b: ByteArray, off: Int, len: Int): Int

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
