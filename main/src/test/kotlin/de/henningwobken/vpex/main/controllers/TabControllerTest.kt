package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.views.TabView
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.control.Tab
import jdk.nashorn.internal.ir.annotations.Ignore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import tornadofx.*

internal class TabControllerTest {

    @Test
    @Ignore // TODO: FIX
    fun addTab() {
        val tabController = TabController()
        val tab = Mockito.mock(Tab::class.java)
        val tabView = Mockito.mock(TabView::class.java)
        Mockito.`when`(tabView.isDirty).thenReturn(SimpleBooleanProperty(false))
        val tabClassList = mutableListOf<String>()
        Mockito.`when`(tab.toggleClass(anyString(), eq(true))).thenReturn(tab)
//        then { invocation ->
//            tabClassList.add(invocation.arguments[0].toString())
//            tab
//        }
        Mockito.`when`(tab.toggleClass(anyString(), eq(false))).then { invocation ->
            tabClassList.remove(invocation.arguments[0].toString())
        }
        tabController.addTab(tab, tabView)
        assertEquals(tabView, tabController.getTabView(tab))
        assertEquals(tab, tabController.getTab(tabView))
        assertEquals(tabClassList, listOf("tab-unchanged"))
    }

    @Test
    @Ignore // TODO: FIX
    fun closeTab() {
        val tabController = TabController()
        val tab = Mockito.mock(Tab::class.java)
        val tabView = Mockito.mock(TabView::class.java)
        Mockito.`when`(tabView.isDirty).thenReturn(SimpleBooleanProperty())
        tabController.addTab(tab, tabView)
        tabController.closeTab(tab)
        assertNull(tabController.getTabView(tab))
    }

    @Test
    fun requestCloseTab() {
    }

    @Test
    fun getTabView() {
    }

    @Test
    fun testGetTabView() {
    }

    @Test
    fun getTab() {
    }
}
