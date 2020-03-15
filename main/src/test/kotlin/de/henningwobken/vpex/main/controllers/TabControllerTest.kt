package de.henningwobken.vpex.main.controllers

import de.henningwobken.vpex.main.TestUtils
import de.henningwobken.vpex.main.views.TabView
import javafx.beans.property.SimpleBooleanProperty
import javafx.embed.swing.JFXPanel
import javafx.scene.control.Tab
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import java.io.File
import java.util.concurrent.CountDownLatch
import javax.swing.SwingUtilities

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TabControllerTest {

    @BeforeAll
    fun init() {
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            JFXPanel() // initializes JavaFX environment
            latch.countDown()
        }
        latch.await()
    }

    @Test
    fun addTab() {
        val tabController = TabController()
        val tab = Tab()
        val tabView = Mockito.mock(TabView::class.java)
        val file = File("")
        val isDirtyProperty = SimpleBooleanProperty(false)
        Mockito.`when`(tabView.isDirty).thenReturn(isDirtyProperty)
        Mockito.`when`(tabView.getFile()).thenReturn(file)

        tabController.addTab(tab, tabView)

        assertEquals(tabView, tabController.getTabView(tab))
        assertEquals(tabView, tabController.getTabView(file))
        assertEquals(tab, tabController.getTab(tabView))
        assertEquals(listOf("tab", "tab-unchanged"), tab.styleClass)

        isDirtyProperty.set(true)
        assertEquals(listOf("tab", "tab-changed"), tab.styleClass)
    }

    @Test
    fun closeTab() {
        val tabController = TabController()
        val tab = Tab()
        val tabView = Mockito.mock(TabView::class.java)
        Mockito.`when`(tabView.isDirty).thenReturn(SimpleBooleanProperty())
        tabController.addTab(tab, tabView)
        tabController.closeTab(tab)
        assertThrows(NullPointerException::class.java) {
            tabController.getTabView(tab)
        }
    }

    @Test
    fun requestCloseTab() {
        val tabController = TabController()
        val tab = Tab()
        val tabView = Mockito.mock(TabView::class.java)
        Mockito.`when`(tabView.isDirty).thenReturn(SimpleBooleanProperty())
        var callbackWasCalled = false
        val callback = {
            callbackWasCalled = true
        }
        Mockito.`when`(tabView.requestCloseTab(TestUtils.nullsafeAny())).then { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as () -> Unit)()
        }
        tabController.addTab(tab, tabView)
        tabController.requestCloseTab(tab, callback)
        assertTrue(callbackWasCalled)
    }


}
