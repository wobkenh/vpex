package de.henningwobken.vpex.main.xml

import de.henningwobken.vpex.main.model.XmlCharState
import tornadofx.*
import java.io.Reader
import java.io.Writer
import java.lang.Integer.max

class XmlFormattingService : Controller() {
    fun format(input: Reader, output: Writer, withNewLines: Boolean = false, indentSize: Int = 0) {
        val bufferSize = 8192

        // --------------------
        //  Writing variables
        // --------------------

        val writeBuffer = CharArray(bufferSize)
        var writeIndex = 0

        // This function can be used to write a character to the result
        // It uses a buffer and flushes when the buffer is full
        val writeChar = { char: Char ->
            writeBuffer[writeIndex++] = char
            if (writeIndex == bufferSize) {
                output.write(writeBuffer)
                writeIndex = 0
            }
        }
        // Helper methods for writing into the buffer
        val writeCharTimes = { char: Char, times: Int ->
            for (i in 0 until times) {
                writeChar(char)
            }
        }
        val writeIndent = { indent: Int ->
            if (withNewLines) {
                writeChar('\n')
            }
            writeCharTimes(' ', indent)
        }
        val writeString = { string: String ->
            string.forEach { writeChar(it) }
        }

        // --------------------
        //  Reading variables
        // --------------------

        val readBuffer = CharArray(bufferSize)
        val nextReadBuffer = CharArray(bufferSize)
        var nextReadBufferSize = 0
        var nextReadBufferFilled = false

        // Use this function to get a char that is beyond the current index
        // Handles buffer access
        val getChar = { index: Int ->
            if (index < bufferSize) {
                readBuffer[index]
            } else {
                // Sometimes, we need to look at subsequent characters
                // This means we might have to fill the next buffer
                // but we can't throw away the current one since it might not be finished yet
                if (!nextReadBufferFilled) {
                    nextReadBufferSize = input.read(nextReadBuffer)
                    nextReadBufferFilled = true
                }
                nextReadBuffer[index - bufferSize]
            }
        }

        // --------------------
        //  Other State variables
        // --------------------

        var dataText = ""
        var state: XmlCharState = XmlCharState.BETWEEN
        var ignoreCharCount = 0
        var hasMore = true
        var indent = 0


        // --------------------
        //  Iteration
        //
        // This basically is a small finite state machine
        // We always have a state which states in which kind of text we are, e.g. opening tag, comment, data, ...
        // While iterating character after character of the string,
        // we switch between the states when certain conditions are met
        // Depending on the state, we decide if we want to keep the character or not
        // reading input / writing output is abstracted by the methods declared above
        // we still have to do a little bit of buffering though
        // --------------------

        while (hasMore) {
            val charsRead = if (nextReadBufferFilled) {
                nextReadBuffer.copyInto(readBuffer)
                nextReadBufferFilled = false
                nextReadBufferSize
            } else {
                input.read(readBuffer)
            }

            hasMore = charsRead >= 0

            for (index in 0 until charsRead) {
                if (ignoreCharCount > 0) {
                    ignoreCharCount--
                    continue
                }

                val char = readBuffer[index]
                when (state) {
                    XmlCharState.BETWEEN -> {
                        // By only appending '<', we effectively remove everything else between a closing and an opening tag
                        if (char == '<') {
                            state = when (getChar(index + 1)) {
                                '?' -> XmlCharState.XML_TAG
                                '/' -> {
                                    indent = max(0, indent - indentSize)
                                    writeIndent(indent)
                                    XmlCharState.CLOSING_TAG
                                }
                                '!' -> if (getChar(index + 2) == '[') XmlCharState.CDATA else {
                                    writeIndent(indent)
                                    // comments do not change indentation
                                    XmlCharState.COMMENT
                                }
                                else -> {
                                    if (withNewLines) {
                                        // we dont want to have a new line on the first opening tag
                                        if (writeIndex > 0) {
                                            writeChar('\n')
                                        }
                                    }
                                    writeCharTimes(' ', indent)
                                    indent += indentSize
                                    XmlCharState.OPENING_TAG
                                }
                            }
                            writeChar('<')
                        }
                    }
                    XmlCharState.OPENING_TAG -> {
                        writeChar(char)
                        if (char == '>') {
                            state = XmlCharState.DATA
                        }
                    }
                    XmlCharState.CLOSING_TAG -> {
                        writeChar(char)
                        if (char == '>') {
                            state = XmlCharState.BETWEEN
                        }
                    }
                    XmlCharState.DATA -> {
                        // After an opening tag, there might be either data or another tag.
                        // If it is another tag, we need to remove it
                        // If it is data, we need to keep it
                        // Therefore, build a tmp string as long as you are in data and add when you know what you are
                        dataText += char
                        if (char == '<') {
                            state = if (getChar(index + 1) == '/') {
                                // This was really data
                                writeString(dataText)
                                indent = max(0, indent - indentSize)
                                XmlCharState.CLOSING_TAG
                            } else {
                                val tmpState = if (getChar(index + 1) == '!') {
                                    if (getChar(index + 2) == '[') XmlCharState.CDATA else {
                                        writeIndent(indent)
                                        XmlCharState.COMMENT
                                    }
                                } else {
                                    writeIndent(indent)
                                    indent += indentSize
                                    XmlCharState.OPENING_TAG
                                }
                                writeChar('<') // Write char needs to happen after newline + indent
                                tmpState
                            }
                            dataText = ""
                        }
                    }
                    XmlCharState.XML_TAG -> {
                        writeChar(char)
                        if (char == '>') {
                            state = XmlCharState.BETWEEN
                        }
                    }
                    XmlCharState.COMMENT -> {
                        writeChar(char)
                        if (char == '-' && getChar(index + 1) == '-' && getChar(index + 2) == '>') {
                            writeChar('-')
                            writeChar('>')
                            ignoreCharCount = 2
                            state = XmlCharState.BETWEEN
                        }
                    }
                    XmlCharState.CDATA -> {
                        writeChar(char)
                        if (char == ']' && getChar(index + 1) == ']' && getChar(index + 2) == '>') {
                            writeChar(']')
                            writeChar('>')
                            ignoreCharCount = 2
                            state = XmlCharState.DATA
                        }
                    }
                }
            }
        }

        if (withNewLines) {
            // EOF Line
            writeChar('\n')
        }

        // flush the buffer
        output.write(writeBuffer, 0, writeIndex)

    }
}
