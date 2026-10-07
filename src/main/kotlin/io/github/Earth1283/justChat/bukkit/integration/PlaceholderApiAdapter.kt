package io.github.Earth1283.justChat.bukkit.integration

import io.github.Earth1283.justChat.core.format.ExternalPlaceholders
import io.github.Earth1283.justChat.core.health.Integration
import io.github.Earth1283.justChat.core.health.IntegrationMonitor
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.OfflinePlayer

internal object PlaceholderApiAdapter {
    fun createExpander(monitor: IntegrationMonitor): ExternalPlaceholders = PlaceholderApiExpander(monitor)
}

private class PlaceholderApiExpander(private val monitor: IntegrationMonitor) : ExternalPlaceholders {
    @Volatile
    private var pausedUntilNanos = 0L

    override fun expand(subject: Any?, placeholder: String): String? {
        val player = subject as? OfflinePlayer ?: return null
        if (pausedUntilNanos != 0L && System.nanoTime() < pausedUntilNanos) return null
        return try {
            PlaceholderAPI.setPlaceholders(player, placeholder).also { monitor.success(Integration.PLACEHOLDERAPI) }
        } catch (t: Throwable) {
            monitor.failure(Integration.PLACEHOLDERAPI, t)
            pausedUntilNanos = System.nanoTime() + PAUSE_AFTER_FAILURE_NANOS
            null
        }
    }

    private companion object {
        const val PAUSE_AFTER_FAILURE_NANOS = 5_000_000_000L
    }
}
