package io.github.Earth1283.justChat.core.config

import io.github.Earth1283.justChat.bukkit.YamlSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BundledConfigTest {
    private fun bundled(name: String) = assertNotNull(javaClass.classLoader.getResourceAsStream(name)).reader()

    @Test
    fun `the shipped config file parses to exactly the built-in defaults`() {
        val result = ConfigParser.parse(YamlSettings.read(bundled("config.yml")))
        assertTrue(result.errors.isEmpty(), result.errors.toString())
        assertTrue(result.warnings.isEmpty(), result.warnings.toString())
        assertEquals(JustChatConfig.DEFAULT, result.config)
    }

    @Test
    fun `the shipped config file compiles into templates`() {
        val manager = ConfigManager()
        assertTrue(manager.reload { YamlSettings.read(bundled("config.yml")) }.applied)
    }

    @Test
    fun `plugin descriptor declares only soft dependencies`() {
        val yaml = org.bukkit.configuration.file.YamlConfiguration().also { it.load(bundled("plugin.yml")) }
        assertTrue(yaml.getStringList("depend").isEmpty())
        assertEquals(listOf("LuckPerms", "Vault", "PlaceholderAPI"), yaml.getStringList("softdepend"))
        assertEquals(true, yaml.getBoolean("folia-supported"))
    }
}
