package de.henningwobken.vpex.main.controllers

data class ExpectedStyle(val length: Int, val style: ExpectedClass)
data class ExpectedStyles(val length: Int, val styles: List<ExpectedClass>) {
    constructor(length: Int, vararg styles: ExpectedClass) : this(length, styles.toList())
}
