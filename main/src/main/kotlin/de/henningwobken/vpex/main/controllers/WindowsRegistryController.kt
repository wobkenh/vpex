package de.henningwobken.vpex.main.controllers

import mu.KotlinLogging
import tornadofx.*
import java.io.BufferedReader


class WindowsRegistryController : Controller() {

    private val logger = KotlinLogging.logger {}

    data class RegistryResult(val path: String, val entries: List<RegistryEntry>, val subPaths: List<String>)

    data class RegistryEntry(val name: String, val type: String, val value: String)

    enum class RegistryType {
        REG_SZ, REG_MULTI_SZ, REG_DWORD_BIG_ENDIAN, REG_DWORD, REG_BINARY, REG_DWORD_LITTLE_ENDIAN, REG_LINK,
        REG_FULL_RESOURCE_DESCRIPTOR, REG_EXPAND_SZ
    }

    fun isWindows(): Boolean {
        return System.getProperty("os.name").startsWith("Windows")
    }

    fun writeRegistryLocation(location: String): Boolean {
        return writeRegistry(location, null, null, null)
    }

    fun writeRegistryValue(location: String, key: String, value: String): Boolean {
        return this.writeRegistry(location, key, value, RegistryType.REG_SZ)
    }

    private fun writeRegistry(location: String, key: String?, value: String?, type: RegistryType?): Boolean {
        if (!isWindows()) {
            logger.warn { "Cannot write to registry since im not on a windows system" }
            return false
        }
        return try {
            var query = "reg add \"$location\""
            if (key != null && value != null && type != null) {
                query += " /v \"$key\" /t $type /d \"$value\" /f"
            } else if (key != null || value != null || type != null) {
                logger.warn { "Ignoring key since one of the parameters was null (key $key)/(value $value)/(type $type)" }
            }
            logger.debug { "Writing value '$value' at $location ($key) to windows registry with query '$query'" }
            executeQuery(query)
        } catch (e: Exception) {
            logger.error("Error reading $location ($key) from registry", e)
            false
        }
    }

    fun deleteRegistryLocation(location: String): Boolean {
        if (!isWindows()) {
            logger.warn { "Cannot write to registry since im not on a windows system" }
            return false
        }
        return try {
            val query = "reg delete \"$location\" /f"
            logger.debug { "Deleting location '$location' in windows registry with query '$query'" }
            executeQuery(query)
        } catch (e: Exception) {
            logger.error("Error deleting $location from registry", e)
            false
        }
    }

    private fun executeQuery(query: String): Boolean {
        val process = Runtime.getRuntime().exec(query)
        process.waitFor()

        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val error = process.errorStream.bufferedReader().use(BufferedReader::readText)

        if (error.isNotEmpty()) {
            logger.error("Error executing '$query': $error")
            return false
        }

        logger.trace("Whole output:")
        logger.trace(output)
        logger.trace("Whole output end")
        return true
    }

    fun readRegistryValue(location: String, key: String): String? {
        val result = readRegistry(location, key)
        if (result != null && result.entries.isNotEmpty()) {
            val value = result.entries.first().value
            logger.debug("Value for $location ($key) is $value")
            return value
        }
        return null
    }

    // TODO: Test Reading of Registry with sample output
    //  Inject Runtime by DI
    /**
     * Reads from the windows registry
     * see https://stackoverflow.com/questions/62289/read-write-to-windows-registry-using-java
     *
     * @param location path in the registry
     * @param key registry key
     * @return registry value or null if not found
     */
    fun readRegistry(location: String, key: String? = null): RegistryResult? {
        if (!isWindows()) {
            logger.warn { "Cannot read from registry since im not on a windows system" }
            return null
        }
        return try {
            var query = "reg query \"$location\""
            if (key != null) {
                query += " /v \"$key\""
            }
            logger.debug { "Reading key at $location ($key) from windows registry with query '$query'" }
            val process = Runtime.getRuntime().exec(query)
            process.waitFor()

            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val error = process.errorStream.bufferedReader().use(BufferedReader::readText)

            if (error.isNotEmpty()) {
                logger.error("Error executing '$query': $error")
                return null
            }

            // The query
            // reg query "hkcr\.xml" /v ""
            // outputs its result in the following format (angel brackets used to symbolize delimiters):
            // HKEY_CLASSES_ROOT\.xml<\n>
            // <\s\s\s\s>(Default)<\s\s\s\s>REG_SZ<\s\s\s\>sxmlfile

            // Parse out the value
            val lines = output.split("\r\n")
            if (lines.isEmpty()) {
                logger.error("Result was empty")
                return null
            }
            logger.trace("Whole output:")
            logger.trace(lines.toString())
            logger.trace("Whole output end")
            val path = lines[1]
            val entryLines = lines.drop(2).toList()
            val entries = mutableListOf<RegistryEntry>()
            for (line in entryLines) {
                if (line.isEmpty()) {
                    break
                }
                val fields = line.split("\t", "    ")
                logger.trace("Single Line:")
                logger.trace(fields.toString())
                logger.trace("Single Line end")
                entries.add(RegistryEntry(fields[1], fields[2], fields[3]))
            }
            val paths = lines.drop(2 + entries.size + 1).toList().filter { it.isNotEmpty() }
            RegistryResult(path, entries, paths)
        } catch (e: Exception) {
            logger.error("Error reading $location ($key) from registry", e)
            null
        }
    }

}
