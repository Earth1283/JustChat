package io.github.Earth1283.justChat.bukkit

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.Reader

object YamlSettings {
    fun read(file: File): Map<String, Any?> = flatten(YamlConfiguration().also { it.load(file) })

    fun read(reader: Reader): Map<String, Any?> = flatten(YamlConfiguration().also { it.load(reader) })

    private fun flatten(yaml: YamlConfiguration): Map<String, Any?> =
        yaml.getValues(true).filterValues { it !is ConfigurationSection }
}
