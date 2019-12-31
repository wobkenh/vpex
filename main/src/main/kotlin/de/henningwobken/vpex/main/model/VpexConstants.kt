package de.henningwobken.vpex.main.model


class VpexConstants {
    companion object {
        val isWindows = System.getProperty("os.name").startsWith("Windows")
        val vpexHome = System.getProperty("user.home") + "/.vpex"
    }
}
