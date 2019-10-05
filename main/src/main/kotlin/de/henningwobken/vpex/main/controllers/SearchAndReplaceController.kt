package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.model.Find
import de.henningwobken.vpex.main.model.SearchDirection
import de.henningwobken.vpex.main.model.SearchTextMode
import tornadofx.*
import java.util.regex.Pattern

class SearchAndReplaceController : Controller() {

    private val regexPatternMap = mutableMapOf<String, Pattern>()

    /**
     * Finds the next occurence of searchText in the fullText.
     *
     * @param searchDirection In which direction to start searching
     * @param searchTextMode whether to interprete the searchterm as regex or plain
     * @param fullText text to search in
     * @param searchText text to search for or regex describing it
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
