package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.Find
import de.henningwobken.vpex.main.model.SearchDirection
import de.henningwobken.vpex.main.model.SearchTextMode
import tornadofx.*
import java.awt.event.KeyEvent
import java.io.File
import java.io.RandomAccessFile
import java.lang.Character.UnicodeBlock
import java.lang.Integer.min
import java.util.regex.Pattern
import kotlin.math.max

class SearchAndReplaceController : Controller() {

    private val regexPatternMap = mutableMapOf<String, Pattern>()


    /**
     * Finds all of the occurences of searchText in the fullText
     *
     * @param fullText text to search in
     * @param searchText text to search for or regex describing it
     * @param searchTextMode whether to interpret the search term as regex or plain
     * @param ignoreCase whether to do a case sensitive or case insensitive search
     * @return list of all finds
     */
    fun findAll(fullText: String,
                searchText: String,
                searchTextMode: SearchTextMode = SearchTextMode.NORMAL,
                ignoreCase: Boolean = false): List<Find> {
        val allFinds = mutableListOf<Find>()
        if (searchTextMode == SearchTextMode.REGEX) {
            val patternString = if (ignoreCase) "(?i)$searchText" else searchText
            val pattern = regexPatternMap.getOrPut(patternString) { Pattern.compile(patternString) }
            val matcher = pattern.matcher(fullText)
            while (matcher.find()) {
                allFinds.add(Find(matcher.start().toLong(), matcher.end().toLong()))
            }
        } else {
            var index = fullText.indexOf(searchText, 0, ignoreCase)
            while (index < fullText.length && index >= 0) {
                allFinds.add(Find(index.toLong(), (index + searchText.length).toLong()))
                index = fullText.indexOf(searchText, index + 1, ignoreCase)
            }
        }
        return allFinds
    }

    /**
     * Finds the next occurrence of searchText in the file.
     *
     * @param file file to read form
     * @param searchDirection In which direction to start searching
     * @param searchTextMode whether to interpret the search term as regex or plain
     * @param searchText text to search for or regex describing it
     * @param ignoreCase whether to do a case sensitive or case insensitive search
     * @return Find object with start and end index of find.
     *         The start and end index relate to the bytes of the file that was given.
     *         Be careful:
     *         Characters can be multiple bytes (e.g. umlauts in UTF-8), so byte index != character index
     */
    fun findNextFromDisk(file: File,
                         searchText: String,
                         offset: Int,
                         pageSize: Int,
                         searchDirection: SearchDirection = SearchDirection.DOWN,
                         searchTextMode: SearchTextMode = SearchTextMode.NORMAL,
                         ignoreCase: Boolean = false): Find? {
        // We can't load full text into memory
        // therefore, we have to go page by page
        // this means that page breaks might hide/split search results
        // to counter this, a pageOverlap is introduced which will cause the searches to overlap
        val pageOverlap = max(100, searchText.length)
        // We dont want page overlap on our first search. Add it here so it gets substracted in the iteration
        var fileOffset = (offset).toLong() + pageOverlap
        val accessFile = RandomAccessFile(file, "r")
        val buffer = ByteArray(pageSize)
        var tmpFind: Find? = null
        while (true) {
            accessFile.seek(fileOffset - pageOverlap)
            val read = accessFile.read(buffer)
            if (read == -1) {
                break
            }
            val string = String(buffer, 0, read)
            tmpFind = findNext(string, searchText, 0, searchDirection, searchTextMode, ignoreCase)
            if (tmpFind != null) {
                // If the file is unicode, one byte != one character
                // Since we search through the file in pages of byte arrays, there is no way to know
                // what character number we are at right now.
                // Therefore, convert the char indices to byte indices
                // If this solution is causing performance problems, refer to the following SO Thread:
                // https://stackoverflow.com/questions/27651543/character-index-to-and-from-byte-index
                val cursorPosition = fileOffset - pageOverlap

                // Bytes before the find
                // The first character might be broken due to the page break breaking a two-byte character (umlauts) apart
                // This broken character gets translated into a 3-byte-character when transforming the string back to byte array
                // Therefore, we simply start at 1 and substract this one character later
                // If the finding started from the first character, then this does not matter
                val startIndex = if (isPrintableChar(string.first())) 0 else min(1, tmpFind.start.toInt())
                val prefixByteLength = string.substring(startIndex, tmpFind.start.toInt()).toByteArray().size + startIndex
                // Bytes of the find
                val findByteLength = string.substring(tmpFind.start.toInt(), tmpFind.end.toInt()).toByteArray().size
                tmpFind = Find(prefixByteLength + cursorPosition, prefixByteLength + findByteLength + cursorPosition)
                break
            }
            fileOffset += read
        }
        return tmpFind
    }

    private fun isPrintableChar(c: Char): Boolean {
        // see https://stackoverflow.com/questions/220547/printable-char-in-java
        val block = UnicodeBlock.of(c)
        return !Character.isISOControl(c) && c != KeyEvent.CHAR_UNDEFINED && block != null && block !== UnicodeBlock.SPECIALS
    }

    /**
     * Finds the next occurence of searchText in the fullText.
     *
     * @param searchDirection In which direction to start searching
     * @param searchTextMode whether to interpret the search term as regex or plain
     * @param fullText text to search in
     * @param searchText text to search for or regex describing it
     * @param ignoreCase whether to do a case sensitive or case insensitive search
     * @return Find object with start and end index of find.
     *         The start and end index relate to the full text that was given.
     */
    fun findNext(fullText: String,
                 searchText: String,
                 offset: Int,
                 searchDirection: SearchDirection = SearchDirection.DOWN,
                 searchTextMode: SearchTextMode = SearchTextMode.NORMAL,
                 ignoreCase: Boolean = false): Find? {
        return if (searchDirection == SearchDirection.UP) {
            if (searchTextMode == SearchTextMode.REGEX) {

                // UP REGEX

                val patternString = if (ignoreCase) "(?i)$searchText" else searchText
                val pattern = regexPatternMap.getOrPut(patternString) { Pattern.compile(patternString) }
                // End index of substring is exclusive, so no -1
                val matcher = pattern.matcher(fullText)
                var regexStartIndex = -1
                var regexEndIndex = -1
                // TODO: This goes through all the matches. This is inefficient.
                while (true) {
                    try {
                        val found = matcher.find()
                        if (!found || matcher.end() > offset) {
                            break
                        }
                        regexStartIndex = matcher.start()
                        regexEndIndex = matcher.end()
                    } catch (e: Exception) {
                        break
                    }
                }
                if (regexStartIndex >= 0) Find(regexStartIndex.toLong(), regexEndIndex.toLong()) else null
            } else {

                // UP NORMAL/EXTENDED

                // - 1 to exclude current search result
                val startIndex = fullText.lastIndexOf(searchText, offset, ignoreCase)
                if (startIndex >= 0) {
                    Find(startIndex.toLong(), startIndex + searchText.length.toLong())
                } else null
            }
        } else {
            if (searchTextMode == SearchTextMode.REGEX) {

                // DOWN REGEX

                val patternString = if (ignoreCase) "(?i)$searchText" else searchText
                val pattern = regexPatternMap.getOrPut(patternString) { Pattern.compile(patternString) }
                val matcher = pattern.matcher(fullText)
                if (matcher.find(offset)) {
                    Find(matcher.start().toLong(), matcher.end().toLong())
                } else null
            } else {

                // DOWN NORMAL/EXTENDED

                val startIndex = fullText.indexOf(searchText, offset, ignoreCase)
                if (startIndex >= 0) {
                    Find(startIndex.toLong(), (startIndex + searchText.length).toLong())
                } else null
            }
        }
    }
}
