package de.henningwobken.vpex.main.model

import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*

class Settings(
        val openerBasePath: String,
        val schemaBasePath: String,
        val wrapText: Boolean,
        val prettyPrintIndent: Int,
        val locale: Locale,
        val pagination: Boolean,
        val pageSize: Int,
        val paginationThreshold: Int,
        val autoUpdate: Boolean,
        val proxyHost: String,
        val proxyPort: Int?,
        val memoryIndicator: Boolean,
        val saveLock: Boolean
) {
    fun hasProxy(): Boolean = proxyHost.isNotBlank()

    fun getProxy(): Proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort!!))
}