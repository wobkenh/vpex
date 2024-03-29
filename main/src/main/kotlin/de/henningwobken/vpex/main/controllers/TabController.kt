package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.views.TabView
import javafx.scene.control.Tab
import mu.KotlinLogging
import tornadofx.*
import java.io.File

class TabController : Controller() {
    private val logger = KotlinLogging.logger {}
    private val lock = Object()
    private val tabs = mutableMapOf<Tab, TabView>()

    fun addTab(tab: Tab, tabView: TabView) {
        synchronized(lock) {
            tabs[tab] = tabView
            tabView.isDirty.onChange {
                toggleClass(tab, it)
            }
            toggleClass(tab, tabView.isDirty.get())
        }
    }

    private fun toggleClass(tab: Tab, isDirty: Boolean) {
        tab.toggleClass("tab-changed", isDirty)
        tab.toggleClass("tab-unchanged", !isDirty)
    }

    fun closeTab(tab: Tab) {
        logger.debug { "Closing Tab $tab @ TabController" }
        val tabView = getTabView(tab)
        synchronized(lock) {
            tabs.remove(tab)
            logger.debug { "Removed Tab $tab @ TabController" }
        }
        tabView.closeTab()
        logger.debug { "Closed $tab @ TabController" }
    }

    fun requestCloseTab(tab: Tab, callback: () -> Unit) {
        logger.debug { "Requesting to close Tab $tab @ TabController" }
        val tabView = getTabView(tab)
        tabView.requestCloseTab {
            synchronized(lock) {
                logger.debug { "Callback for tab $tab @ TabController" }
                callback()
            }
        }
    }

    fun getTabView(tab: Tab): TabView {
        synchronized(lock) {
            logger.debug { "Getting Tab $tab" }
            return tabs[tab]!!
        }
    }

    fun getTabView(file: File): TabView? {
        synchronized(lock) {
            return tabs.values.find {
                it.getFile() == file
            }
        }
    }

    fun getTab(tabView: TabView): Tab {
        synchronized(lock) {
            return tabs.entries.find { it.value == tabView }!!.key
        }
    }
}
