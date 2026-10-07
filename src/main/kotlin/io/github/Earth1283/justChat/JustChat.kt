package io.github.Earth1283.justChat

import io.github.Earth1283.justChat.bukkit.ChatListener
import io.github.Earth1283.justChat.bukkit.ComponentSender
import io.github.Earth1283.justChat.bukkit.MessageService
import io.github.Earth1283.justChat.bukkit.MessageStyles
import io.github.Earth1283.justChat.bukkit.PlayerSessions
import io.github.Earth1283.justChat.bukkit.PlayerStateListener
import io.github.Earth1283.justChat.bukkit.PrivateMessaging
import io.github.Earth1283.justChat.bukkit.ServerPlatform
import io.github.Earth1283.justChat.bukkit.StartupTimings
import io.github.Earth1283.justChat.bukkit.YamlSettings
import io.github.Earth1283.justChat.bukkit.command.IgnoreCommand
import io.github.Earth1283.justChat.bukkit.command.IgnoreListCommand
import io.github.Earth1283.justChat.bukkit.command.JustChatCommand
import io.github.Earth1283.justChat.bukkit.command.MessageCommand
import io.github.Earth1283.justChat.bukkit.command.ReplyCommand
import io.github.Earth1283.justChat.bukkit.command.UnignoreCommand
import io.github.Earth1283.justChat.bukkit.integration.IntegrationManager
import io.github.Earth1283.justChat.core.config.ConfigManager
import io.github.Earth1283.justChat.core.config.ReloadOutcome
import io.github.Earth1283.justChat.core.diagnostics.Diagnostics
import io.github.Earth1283.justChat.core.format.ChatPipeline
import io.github.Earth1283.justChat.core.health.Integration
import io.github.Earth1283.justChat.core.health.IntegrationDetail
import io.github.Earth1283.justChat.core.health.IntegrationMonitor
import io.github.Earth1283.justChat.core.metadata.MemoryMetadataCache
import io.github.Earth1283.justChat.core.metadata.MetadataResolver
import io.github.Earth1283.justChat.core.persistence.PersistenceManager
import io.github.Earth1283.justChat.core.social.ChatColorPreferences
import io.github.Earth1283.justChat.core.social.IgnoreService
import io.github.Earth1283.justChat.core.social.ReplyTracker
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class JustChat : JavaPlugin() {
    private val configManager = ConfigManager()
    private val monitor = IntegrationMonitor { logger.warning(it) }
    private val sessions = PlayerSessions()
    private val memoryCache = MemoryMetadataCache()
    private val replies = ReplyTracker()

    private lateinit var persistence: PersistenceManager
    private lateinit var resolver: MetadataResolver

    private class Services(
        val pipeline: ChatPipeline,
        val componentSender: ComponentSender,
        val ignores: IgnoreService,
        val preferences: ChatColorPreferences,
        val messages: MessageService,
        val styles: MessageStyles,
        val integrations: IntegrationManager,
        val diagnostics: Diagnostics,
    )

    override fun onEnable() {
        val timings = StartupTimings()
        timings.measure("Configuration") { loadConfiguration() }
        val services = timings.measure("Services") { createServices() }
        timings.measure("SQLite") { applyPersistenceSetting() }
        timings.measure("Integrations") { registerListeners(services) }
        timings.measure("Commands") { registerCommands(services) }
        logStartup(timings)
        services.componentSender.warmUpAsync()
    }

    override fun onDisable() {
        if (::persistence.isInitialized) persistence.shutdown()
    }

    private fun createServices(): Services {
        persistence = PersistenceManager(
            databaseFile = dataFolder.toPath().resolve("justchat.db"),
            monitor = monitor,
            onStoreOpened = ::onSqliteOpened,
        )
        resolver = MetadataResolver(
            cache = memoryCache,
            defaults = { configManager.current.config.metadataDefaults },
            sink = { uuid, name, metadata, source -> persistence.queueMetadata(uuid, name, metadata, source) },
            monitor = monitor,
        )
        val pipeline = ChatPipeline(configManager, resolver)
        val componentSender = ComponentSender(ServerPlatform.dataVersion())
        val preferences = ChatColorPreferences(persistence)
        return Services(
            pipeline = pipeline,
            componentSender = componentSender,
            ignores = IgnoreService(persistence),
            preferences = preferences,
            messages = MessageService(configManager, componentSender),
            styles = MessageStyles(configManager, preferences),
            integrations = IntegrationManager(monitor, resolver, pipeline, sessions),
            diagnostics = Diagnostics(ServerPlatform.name, configManager, pipeline, resolver, memoryCache, persistence, monitor),
        )
    }

    private fun registerListeners(services: Services) {
        services.integrations.refresh()
        val plugins = server.pluginManager
        plugins.registerEvents(services.integrations, this)
        plugins.registerEvents(
            ChatListener(configManager, services.pipeline, sessions, services.styles, services.ignores, services.componentSender, logger),
            this,
        )
        plugins.registerEvents(
            PlayerStateListener(persistence, memoryCache, resolver, services.ignores, services.preferences, replies, sessions),
            this,
        )
        adoptPlayersAlreadyOnline()
    }

    private fun registerCommands(services: Services) {
        val privateMessaging = PrivateMessaging(
            configManager, services.pipeline, sessions, services.styles, services.ignores, replies, services.componentSender, services.messages,
        )
        val messageCommand = MessageCommand(privateMessaging, services.messages)
        listOf("msg", "tell", "w").forEach { name ->
            getCommand(name)?.apply { setExecutor(messageCommand); tabCompleter = messageCommand }
        }
        getCommand("reply")?.setExecutor(ReplyCommand(privateMessaging, replies, services.messages))

        val ignoreCommand = IgnoreCommand(configManager, services.ignores, services.messages)
        getCommand("ignore")?.apply { setExecutor(ignoreCommand); tabCompleter = ignoreCommand }
        val unignoreCommand = UnignoreCommand(configManager, services.ignores, services.messages)
        getCommand("unignore")?.apply { setExecutor(unignoreCommand); tabCompleter = unignoreCommand }
        getCommand("ignorelist")?.setExecutor(IgnoreListCommand(configManager, services.ignores, services.messages))

        val justChatCommand = JustChatCommand(
            description.version,
            configManager,
            services.diagnostics,
            services.preferences,
            services.messages,
            services.componentSender,
            ::reloadConfiguration,
        )
        getCommand("justchat")?.apply { setExecutor(justChatCommand); tabCompleter = justChatCommand }
    }

    private fun configFile() = File(dataFolder, "config.yml")

    private fun loadConfiguration() {
        saveDefaultConfig()
        logConfigProblems(configManager.reload { YamlSettings.read(configFile()) })
    }

    private fun reloadConfiguration(): ReloadOutcome {
        val outcome = configManager.reload { YamlSettings.read(configFile()) }
        logConfigProblems(outcome)
        applyPersistenceSetting()
        return outcome
    }

    private fun logConfigProblems(outcome: ReloadOutcome) {
        outcome.status.errors.forEach { logger.severe("config.yml: $it") }
        outcome.status.warnings.forEach { logger.warning("config.yml: $it") }
        if (!outcome.applied) logger.warning("Keeping the previous valid configuration. Run /justchat doctor for details.")
    }

    private fun applyPersistenceSetting() {
        if (configManager.current.config.sqliteEnabled) {
            persistence.enable()
        } else {
            persistence.disable()
            monitor.unavailable(Integration.SQLITE, IntegrationDetail.DISABLED_IN_CONFIG)
        }
    }

    private fun onSqliteOpened(openedInMillis: Long, hasLiveHistory: Boolean) {
        if (hasLiveHistory) resolver.liveEverSeen = true
        if (configManager.current.config.debug) logger.info("SQLite opened in ${openedInMillis}ms (asynchronously).")
    }

    private fun adoptPlayersAlreadyOnline() {
        for (player in Bukkit.getOnlinePlayers()) {
            memoryCache.pin(player.uniqueId)
            runCatching { sessions.enter(player) }
        }
    }

    private fun logStartup(timings: StartupTimings) {
        if (configManager.current.config.debug) {
            logger.info("Startup timings:")
            timings.breakdown().forEach { logger.info("  $it") }
        } else {
            logger.info("Enabled in %.1fms on %s.".format(timings.totalMillis(), ServerPlatform.name))
        }
    }
}
