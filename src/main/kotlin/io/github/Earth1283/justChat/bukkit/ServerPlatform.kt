package io.github.Earth1283.justChat.bukkit

import org.bukkit.Bukkit

object ServerPlatform {
    val name: String by lazy { detect() }

    @Suppress("DEPRECATION")
    fun dataVersion(): Int = Bukkit.getUnsafe().dataVersion

    private fun detect(): String = when {
        classExists("io.papermc.paper.threadedregions.RegionizedServer") -> "Folia"
        Bukkit.getName().equals("Paper", ignoreCase = true) -> "Paper"
        Bukkit.getName().equals("CraftBukkit", ignoreCase = true) -> "Spigot"
        else -> Bukkit.getName()
    }

    private fun classExists(name: String): Boolean = try {
        Class.forName(name, false, ServerPlatform::class.java.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}
