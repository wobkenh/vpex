package de.henningwobken.vpex.main.model

import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*

class Settings(
        val schemaBasePathList: List<String>,
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
        val saveLock: Boolean,
        val diskPagination: Boolean,
        val diskPaginationThreshold: Int,
        val trustStore: String,
        val trustStorePassword: String,
        val insecure: Boolean,
        val contextMenu: Boolean,
        val syntaxHighlighting: Boolean
) {
    fun hasProxy(): Boolean = proxyHost.isNotBlank()

    fun getProxy(): Proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort!!))
}
