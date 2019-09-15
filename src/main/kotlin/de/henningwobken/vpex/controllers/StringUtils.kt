package de.henningwobken.vpex.controllers

import tornadofx.*

class StringUtils : Controller() {

    // TODO: Unittest
    fun countLinesInString(string: String): Int {
        var lines = 1
        var carriageReturnFlag = false
        for (index in string.indices) {
            val char = string[index]
            if (char == '\n') {
                if (carriageReturnFlag) {
                    carriageReturnFlag = false
                } else {
                    lines++
                }
            } else if (char == '\r') {
                carriageReturnFlag = true
                lines++
            } else {
                carriageReturnFlag = false
            }
        }
        return lines
    }

    fun lastIndexOfIgnoreCase(haystack: String, needle: String, offset: Int): Int {
        if (needle.isEmpty() || haystack.isEmpty()) {
            // Fallback to legacy behavior.
            return haystack.indexOf(needle)
        }

        for (i in offset downTo 0) {
            // Early out, if possible.
            if ((haystack.length - (i + 1)) + needle.length > haystack.length) {
                return -1
            }

            // Attempt to match substring starting at position i of haystack.
            var needleIndex = needle.length - 1
            var haystackIndex = i
            while (haystackIndex >= 0 && needleIndex >= 0) {
                val c = Character.toLowerCase(haystack[haystackIndex])
                val c2 = Character.toLowerCase(needle[needleIndex])
                if (c != c2) {
                    break
                }
                needleIndex--
                haystackIndex--
            }
            // Walked all the way to the end of the needle, return the start
            // position that this was found.
            if (needleIndex == -1) {
                return haystackIndex + 1
            }
        }

        return -1
    }

    fun indexOfIgnoreCase(haystack: String, needle: String, offset: Int): Int {
        if (needle.isEmpty() || haystack.isEmpty()) {
            // Fallback to legacy behavior.
            return haystack.indexOf(needle)
        }

        for (i in offset until haystack.length) {
            // Early out, if possible.
            if (i + needle.length > haystack.length) {
                return -1
            }

            // Attempt to match substring starting at position i of haystack.
            var needleIndex = 0
            var haystackIndex = i
            while (haystackIndex < haystack.length && needleIndex < needle.length) {
                val c = Character.toLowerCase(haystack[haystackIndex])
                val c2 = Character.toLowerCase(needle[needleIndex])
                if (c != c2) {
                    break
                }
                needleIndex++
                haystackIndex++
            }
            // Walked all the way to the end of the needle, return the start
            // position that this was found.
            if (needleIndex == needle.length) {
                return i
            }
        }

        return -1
    }
}