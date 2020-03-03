package de.henningwobken.vpex.main.views

import de.henningwobken.vpex.main.Styles
import de.henningwobken.vpex.main.controllers.SettingsController
import de.henningwobken.vpex.main.controllers.UpdateController
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.control.ProgressIndicator
import javafx.scene.text.TextAlignment
import mu.KotlinLogging
import tornadofx.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class ProxySettingsFragment : Fragment("Auto Update Check Failed") {
    private val settingsController by inject<SettingsController>()

    var retryCallback: (() -> Unit)? = null
    private val logger = KotlinLogging.logger {}
    private val hostProperty = SimpleStringProperty("")
    private val portProperty = SimpleStringProperty("")
    private val hasProxyProperty = SimpleBooleanProperty(false)
    private val insecureProperty = SimpleBooleanProperty(false)
    private val isPortValid = portProperty.booleanBinding { !it.isNullOrBlank() && it.isInt() }
    private val connectionTestResult = SimpleStringProperty("")
    private val isLoading = SimpleBooleanProperty(false)

    init {
        val settings = settingsController.getSettings()
        hostProperty.set(settings.proxyHost)
        portProperty.set(settings.proxyPort?.toString() ?: "")
        insecureProperty.set(settings.insecure)
        hasProxyProperty.set(settings.hasProxy())
    }

    override val root = borderpane {
        prefWidth = 650.0
        center = vbox(20) {
            paddingAll = 10.0
            alignment = Pos.CENTER
            label("Vpex could not establish a connection to the update server. If you are sitting behind a company proxy, you may set the proxy settings below.") {
                isWrapText = true
                textAlignment = TextAlignment.CENTER
            }
            label("You may also choose to deactivate auto update or to ignore this error.")
            hbox(20) {
                alignment = Pos.CENTER
                checkbox("Enable Proxy", hasProxyProperty)
                checkbox("Disable SSL Verification", insecureProperty) {
                    disableWhen { hasProxyProperty.not() }
                }
            }
            hbox(20) {
                alignment = Pos.CENTER
                textfield(hostProperty) {
                    maxWidth = 200.0
                    disableWhen { hasProxyProperty.not() }
                    setOnAction {
                        if (isPortValid.get()) {
                            testConnection()
                        }
                    }
                }
                label(":")
                textfield(portProperty) {
                    maxWidth = 100.0
                    disableWhen { hasProxyProperty.not() }
                    filterInput { it.controlNewText.isInt() }
                    setOnAction {
                        if (isPortValid.get()) {
                            testConnection()
                        }
                    }
                }
            }
            hbox(20) {
                hbox {
                    paddingVertical = 60
                    label("") {
                        prefWidth = 100.0
                    }
                    button("Test Connection") {
                        minWidth = prefWidth
                        disableWhen { isPortValid.not().and(hasProxyProperty) }
                    }.action {
                        testConnection()
                    }
                }
                vbox {
                    prefWidth = 300.0
                    alignment = Pos.CENTER_LEFT
                    progressindicator {
                        progress = ProgressIndicator.INDETERMINATE_PROGRESS
                        alignment = Pos.CENTER_LEFT
                        removeWhen { isLoading.not() }
                    }
                    label("Port must be numeric") {
                        addClass(Styles.errorText)
                        removeWhen { isPortValid.or(hasProxyProperty.not()) }
                    }
                    label(connectionTestResult) {
                        isWrapText = true
                        removeWhen { connectionTestResult.eq("") }
                        connectionTestResult.onChange {
                            if (it != null && it.startsWith("ERROR")) {
                                removeClass(Styles.successText)
                                addClass(Styles.errorText)
                            } else {
                                removeClass(Styles.errorText)
                                addClass(Styles.successText)
                            }
                        }
                        hasProxyProperty.onChange {
                            connectionTestResult.set("")
                        }
                    }
                }
            }
        }

        bottom = hbox(20) {
            paddingAll = 10.0
            button("Deactivate Auto-Update") {}.action {
                logger.info { "Deactivating auto update" }
                val settings = settingsController.getSettings().copy(
                        autoUpdate = false
                )
                settingsController.saveSettings(settings)
                close()
            }
            button("Ignore forever") {}.action {
                logger.info { "Ignoring connection error forever" }
                val settings = settingsController.getSettings().copy(
                        ignoreAutoUpdateError = true
                )
                settingsController.saveSettings(settings)
                close()
            }
            button("Ignore once") {}.action {
                logger.info { "Ignoring connection error once" }
                close()
            }
            label {
                ViewHelper.fillHorizontal(this)
            }
            button("Set Proxy") {
                disableWhen { hasProxyProperty.and(isPortValid.not()) }
                val setText = { hasProxy: Boolean ->
                    text = if (hasProxy) {
                        "Set Proxy"
                    } else {
                        "Set no Proxy"
                    }
                }
                hasProxyProperty.onChange(setText)
                setText(hasProxyProperty.get())
            }.action {
                val host = hostProperty.get()
                val port = portProperty.get()
                val insecure = insecureProperty.get()
                logger.info { "Setting proxy to $host:$port with ssl validation ${!insecure}" }
                val settings = if (hasProxyProperty.get()) {
                    settingsController.getSettings().copy(
                            proxyHost = host,
                            proxyPort = port.toInt(),
                            insecure = insecure
                    )
                } else {
                    settingsController.getSettings().copy(
                            proxyHost = "",
                            proxyPort = null,
                            insecure = false
                    )
                }
                settingsController.saveSettings(settings)
                close()
                val callback = retryCallback
                if (callback != null) {
                    callback()
                }
            }
        }
    }

    private fun testConnection() {
        connectionTestResult.set("")
        isLoading.set(true)
        Thread {
            try {
                if (insecureProperty.get()) {
                    settingsController.disableSSLSecurity()
                }
                val urlObject = URL("${UpdateController.url}/versions.txt")
                val connection = if (hasProxyProperty.get()) {
                    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(hostProperty.get(), portProperty.get().toInt()))
                    urlObject.openConnection(proxy)
                } else {
                    urlObject.openConnection()
                }
                connection.connectTimeout = 5000
                connection.getInputStream().bufferedReader().use { it.readText() }
                Platform.runLater {
                    connectionTestResult.set("Success!")
                    isLoading.set(false)
                }
            } catch (e: Exception) {
                Platform.runLater {
                    connectionTestResult.set("ERROR: $e")
                    isLoading.set(false)
                }
            }
        }.start()
    }
}
