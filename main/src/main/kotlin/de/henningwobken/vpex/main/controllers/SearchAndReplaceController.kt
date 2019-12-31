package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.Find
import de.henningwobken.vpex.main.model.SearchDirection
import de.henningwobken.vpex.main.model.SearchTextMode
import tornadofx.*
import java.io.File
import java.io.RandomAccessFile
import java.util.regex.Pattern

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
     * @param byteOffset offset in characters
     * @param pageSize size of a page in characters
     * @param pageStartingByteIndexes a list indicating at which byte index which page starts
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
                         charOffset: Long,
                         pageSize: Int,
                         pageStartingByteIndexes: List<Long>,
                         searchDirection: SearchDirection = SearchDirection.DOWN,
                         searchTextMode: SearchTextMode = SearchTextMode.NORMAL,
                         ignoreCase: Boolean = false): Find? {
        // We can't load full text into memory
        // therefore, we have to go page by page
        // this means that page breaks might hide/split search results
        // to counter this, a pageOverlap is introduced which will cause the searches to overlap
        // TODO: SearchDirection
        // TODO: Reimplement page overlap
        // TODO: Unified Service Method?
        val accessFile = RandomAccessFile(file, "r")
        var tmpFind: Find? = null
        var pageIndex = (charOffset / pageSize).toInt()
        val bufferSize = getBufferSize(pageStartingByteIndexes)
        val buffer = ByteArray(bufferSize)
        while (true) {
            if (pageIndex >= pageStartingByteIndexes.size) {
                break
            }
            val startByteIndex = pageStartingByteIndexes[pageIndex]
            accessFile.seek(startByteIndex)
            val read = accessFile.read(buffer)
            if (read == -1) {
                break
            }
            val string = String(buffer, 0, read)
            val pageCharOffset = pageIndex * pageSize.toLong()
            val offset = if (pageCharOffset < charOffset) {
                (charOffset % pageSize).toInt()
            } else 0
            tmpFind = findNext(string, searchText, offset, searchDirection, searchTextMode, ignoreCase)
            if (tmpFind != null) {
                return Find(tmpFind.start + pageCharOffset, tmpFind.end + pageCharOffset)
            }
            pageIndex++
        }
        return null
    }

    private fun getBufferSize(pageStartingByteIndexes: List<Long>): Int {
        var bufferSize = 0
        var previousByteIndex = 0L
        for (startingByteIndex in pageStartingByteIndexes) {
            val size = (startingByteIndex - previousByteIndex).toInt()
            if (size > bufferSize) {
                bufferSize = size
            }
            previousByteIndex = startingByteIndex
        }
        return bufferSize
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
