package io.github.Earth1283.justChat.bukkit.integration

import io.github.Earth1283.justChat.bukkit.PlayerSessions
import io.github.Earth1283.justChat.core.format.ChatPipeline
import io.github.Earth1283.justChat.core.health.Integration
import io.github.Earth1283.justChat.core.health.IntegrationDetail
import io.github.Earth1283.justChat.core.health.IntegrationMonitor
import io.github.Earth1283.justChat.core.metadata.LiveSource
import io.github.Earth1283.justChat.core.metadata.MetadataResolver
import io.github.Earth1283.justChat.core.model.MetadataSource
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.event.server.ServiceRegisterEvent
import org.bukkit.event.server.ServiceUnregisterEvent

class IntegrationManager(
    private val monitor: IntegrationMonitor,
    private val resolver: MetadataResolver,
    private val pipeline: ChatPipeline,
    private val sessions: PlayerSessions,
) : Listener {

    fun refresh(pluginBeingDisabled: String? = null) {
        val luckPerms = detectLuckPerms(pluginBeingDisabled)
        val vault = detectVault(pluginBeingDisabled)
        resolver.setLiveSources(listOfNotNull(luckPerms, vault))
        pipeline.externalPlaceholders = detectPlaceholderApi(pluginBeingDisabled)
    }

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name in WATCHED_PLUGINS) refresh()
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin.name in WATCHED_PLUGINS) refresh(pluginBeingDisabled = event.plugin.name)
    }

    @EventHandler
    fun onServiceRegister(event: ServiceRegisterEvent) {
        if (isWatchedService(event.provider.service.name)) refresh()
    }

    @EventHandler
    fun onServiceUnregister(event: ServiceUnregisterEvent) {
        if (isWatchedService(event.provider.service.name)) refresh()
    }

    private fun detectLuckPerms(pluginBeingDisabled: String?): LiveSource? {
        if (!isRunning(LUCKPERMS, pluginBeingDisabled)) {
            monitor.unavailable(Integration.LUCKPERMS, IntegrationDetail.NOT_DETECTED)
            return null
        }
        val provider = attempt(Integration.LUCKPERMS) { LuckPermsAdapter.createProvider() }
        if (provider == null) {
            monitor.unavailable(Integration.LUCKPERMS, "installed, but its API is not registered yet")
            return null
        }
        monitor.healthy(Integration.LUCKPERMS)
        return LiveSource(MetadataSource.LUCKPERMS_API, Integration.LUCKPERMS, provider)
    }

    private fun detectVault(pluginBeingDisabled: String?): LiveSource? {
        val provider = attempt(Integration.VAULT) { VaultAdapter.createProvider(sessions) }
            ?.takeIf { pluginBeingDisabled != VAULT }
        if (provider != null) {
            monitor.healthy(Integration.VAULT)
            return LiveSource(MetadataSource.VAULT_API, Integration.VAULT, provider)
        }
        if (isRunning(VAULT, pluginBeingDisabled)) {
            monitor.unavailable(Integration.VAULT, IntegrationDetail.VAULT_WITHOUT_CHAT_PROVIDER)
        } else {
            monitor.unavailable(Integration.VAULT, IntegrationDetail.NOT_DETECTED)
        }
        return null
    }

    private fun detectPlaceholderApi(pluginBeingDisabled: String?): io.github.Earth1283.justChat.core.format.ExternalPlaceholders? {
        if (!isRunning(PLACEHOLDER_API, pluginBeingDisabled)) {
            monitor.unavailable(Integration.PLACEHOLDERAPI, IntegrationDetail.NOT_DETECTED)
            return null
        }
        val expander = attempt(Integration.PLACEHOLDERAPI) { PlaceholderApiAdapter.createExpander(monitor) }
        if (expander != null) monitor.healthy(Integration.PLACEHOLDERAPI)
        return expander
    }

    private fun <T> attempt(integration: Integration, create: () -> T?): T? = try {
        create()
    } catch (_: NoClassDefFoundError) {
        null
    } catch (t: Throwable) {
        monitor.failure(integration, t)
        null
    }

    private fun isRunning(plugin: String, pluginBeingDisabled: String?): Boolean =
        plugin != pluginBeingDisabled && Bukkit.getPluginManager().isPluginEnabled(plugin)

    private fun isWatchedService(serviceClassName: String) =
        serviceClassName.startsWith("net.milkbowl.vault.chat") || serviceClassName.startsWith("net.luckperms.api")

    private companion object {
        const val LUCKPERMS = "LuckPerms"
        const val VAULT = "Vault"
        const val PLACEHOLDER_API = "PlaceholderAPI"
        val WATCHED_PLUGINS = setOf(LUCKPERMS, VAULT, PLACEHOLDER_API)
    }
}
