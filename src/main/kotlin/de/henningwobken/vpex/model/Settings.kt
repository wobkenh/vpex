package de.henningwobken.vpex.model

import java.util.*

class Settings(
        val openerBasePath: String,
        val schemaBasePath: String,
        val wrapText: Boolean,
        val prettyPrintIndent: Int,
        val locale: Locale,
        val pagination: Boolean,
        val pageSize: Int,
        val paginationThreshold: Int
)