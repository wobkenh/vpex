package de.henningwobken.vpex.main.model

enum class XmlCharState {
    OPENING_TAG, CLOSING_TAG, DATA, BETWEEN, XML_TAG, CDATA, COMMENT
}