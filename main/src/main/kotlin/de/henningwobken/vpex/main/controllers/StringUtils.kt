package de.henningwobken.vpex.main.controllers

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
}