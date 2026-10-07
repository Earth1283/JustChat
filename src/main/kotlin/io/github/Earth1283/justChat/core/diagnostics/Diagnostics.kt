package io.github.Earth1283.justChat.core.diagnostics

import io.github.Earth1283.justChat.core.config.ConfigManager
import io.github.Earth1283.justChat.core.config.ConfigState
import io.github.Earth1283.justChat.core.format.ChatPipeline
import io.github.Earth1283.justChat.core.health.Integration
import io.github.Earth1283.justChat.core.health.IntegrationDetail
import io.github.Earth1283.justChat.core.health.IntegrationMonitor
import io.github.Earth1283.justChat.core.metadata.MemoryMetadataCache
import io.github.Earth1283.justChat.core.metadata.MetadataResolver
import io.github.Earth1283.justChat.core.model.CapabilityHealth
import io.github.Earth1283.justChat.core.model.IntegrationHealth
import io.github.Earth1283.justChat.core.model.MetadataSource
import io.github.Earth1283.justChat.core.persistence.PersistenceManager

enum class Tone { GOOD, NEUTRAL, WARN, BAD }

class StatusRow(
    val label: String,
    val value: String,
    val tone: Tone,
    val labelHelp: String,
    val valueHelp: String,
)

enum class FindingLevel { OK, INFO, WARN, ERROR }

class Finding(
    val level: FindingLevel,
    val title: String,
    val impact: String,
    val suggestedAction: String?,
)

class Diagnostics(
    private val platformName: String,
    private val configManager: ConfigManager,
    private val pipeline: ChatPipeline,
    private val resolver: MetadataResolver,
    private val memoryCache: MemoryMetadataCache,
    private val persistence: PersistenceManager,
    private val monitor: IntegrationMonitor,
) {
    fun statusRows(): List<StatusRow> = listOf(
        platformRow(),
        capabilityRow(
            "Chat pipeline",
            pipeline.health(),
            "Whether public chat is being formatted by your configured format.",
            mapOf(
                CapabilityHealth.HEALTHY to "Messages are rendered with the configured format.",
                CapabilityHealth.DEGRADED to "Chat still works, but the emergency formatter was used or the configuration is not fully applied.",
                CapabilityHealth.FAILED to "Chat formatting is failing; messages fall back to the server's default format.",
            ),
        ),
        configurationRow(),
        capabilityRow(
            "Metadata capability",
            resolver.capabilityHealth(),
            "Whether prefixes, suffixes and groups can be resolved.",
            mapOf(
                CapabilityHealth.HEALTHY to "Served live by a permissions integration, or by configured defaults because no live source is expected.",
                CapabilityHealth.DEGRADED to "Served from cached or default data because live sources are unavailable. Values may be stale.",
                CapabilityHealth.FAILED to "The metadata resolver itself is failing.",
            ),
        ),
        StatusRow(
            "Metadata source",
            resolver.lastSource.label,
            if (resolver.lastSource.isLive || resolver.lastSource == MetadataSource.DEFAULT) Tone.GOOD else Tone.WARN,
            "Where the most recent chat message got its prefix, suffix and group from.",
            sourceCounts(),
        ),
        integrationRow(Integration.LUCKPERMS, "Whether the LuckPerms API can be reached for live prefixes, suffixes and groups."),
        integrationRow(Integration.VAULT, "Whether a Vault-compatible chat provider is registered for live prefixes, suffixes and groups."),
        integrationRow(Integration.PLACEHOLDERAPI, "Whether %placeholders% from PlaceholderAPI can be expanded in chat formats."),
        StatusRow(
            "Memory cache",
            IntegrationHealth.HEALTHY.name,
            Tone.GOOD,
            "Bounded in-memory copy of each player's last known metadata. Online players are pinned.",
            "${memoryCache.size} entries, ${memoryCache.pinnedCount} pinned (online players).",
        ),
        integrationRow(Integration.SQLITE, "Whether last-known metadata, ignore lists and color preferences survive restarts."),
    )

    fun findings(): List<Finding> {
        val found = ArrayList<Finding>()
        configFindings(found)
        metadataFindings(found)
        placeholderFindings(found)
        sqliteFindings(found)
        if (pipeline.emergencyRenderCount > 0) {
            found += Finding(
                FindingLevel.WARN,
                "Emergency formatter was used ${pipeline.emergencyRenderCount} time(s)",
                "Some messages were rendered with the built-in fallback format instead of yours.",
                "Check the console for errors and review chat.format and the private-messages formats in config.yml.",
            )
        }
        if (found.isEmpty()) {
            found += Finding(FindingLevel.OK, "No problems detected", "Everything JustChat depends on is working.", null)
        }
        return found
    }

    private fun platformRow() = StatusRow(
        "Platform",
        platformName,
        Tone.NEUTRAL,
        "The server software JustChat is running on.",
        "Detected from the running server implementation.",
    )

    private fun capabilityRow(
        label: String,
        health: CapabilityHealth,
        labelHelp: String,
        valueHelpByHealth: Map<CapabilityHealth, String>,
    ) = StatusRow(
        label,
        health.name,
        when (health) {
            CapabilityHealth.HEALTHY -> Tone.GOOD
            CapabilityHealth.DEGRADED -> Tone.WARN
            CapabilityHealth.FAILED -> Tone.BAD
        },
        labelHelp,
        valueHelpByHealth.getValue(health),
    )

    private fun configurationRow(): StatusRow {
        val status = configManager.status
        val (value, tone, help) = when (status.state) {
            ConfigState.LOADED -> Triple("LOADED", Tone.GOOD, "config.yml was read and validated successfully.")
            ConfigState.RETAINED_PREVIOUS -> Triple("RETAINED PREVIOUS", Tone.WARN, "The last reload was invalid, so the previous valid configuration is still in use.")
            ConfigState.DEFAULTS_ONLY -> Triple("BUILT-IN DEFAULTS", Tone.WARN, "config.yml is missing, unreadable or invalid, so built-in defaults are in use.")
        }
        return StatusRow("Configuration", value, tone, "Which configuration is currently applied.", help)
    }

    private fun integrationRow(integration: Integration, labelHelp: String): StatusRow {
        val state = monitor.state(integration)
        val tone = when (state.health) {
            IntegrationHealth.HEALTHY -> Tone.GOOD
            IntegrationHealth.UNAVAILABLE -> Tone.NEUTRAL
            IntegrationHealth.FAILED -> Tone.BAD
        }
        val help = when (state.health) {
            IntegrationHealth.HEALTHY -> "Available and responding."
            IntegrationHealth.UNAVAILABLE -> "Not in use: ${state.detail ?: "unavailable"}."
            IntegrationHealth.FAILED -> "Present but failing: ${state.detail ?: "unknown error"}."
        }
        return StatusRow(integration.label, state.health.name, tone, labelHelp, help)
    }

    private fun sourceCounts(): String {
        val counts = MetadataSource.entries.joinToString(", ") { "${it.label}: ${resolver.hits(it)}" }
        return "Resolutions since startup. $counts."
    }

    private fun configFindings(out: MutableList<Finding>) {
        val status = configManager.status
        when (status.state) {
            ConfigState.RETAINED_PREVIOUS -> out += Finding(
                FindingLevel.ERROR,
                "Last config reload was rejected",
                "The previous valid configuration is still in use. Problems: ${status.errors.joinToString("; ")}",
                "Fix the listed problems in config.yml, then run /justchat reload.",
            )
            ConfigState.DEFAULTS_ONLY -> out += Finding(
                FindingLevel.ERROR,
                "config.yml could not be applied",
                "Built-in defaults are in use. ${status.errors.joinToString("; ")}".trim(),
                "Fix config.yml (or delete it to regenerate the default), then run /justchat reload.",
            )
            ConfigState.LOADED -> Unit
        }
        if (status.warnings.isNotEmpty()) {
            out += Finding(
                FindingLevel.WARN,
                "config.yml has unrecognised settings",
                status.warnings.joinToString(" "),
                "Check for typos or settings from an older version.",
            )
        }
    }

    private fun metadataFindings(out: MutableList<Finding>) {
        val luckPerms = monitor.state(Integration.LUCKPERMS)
        val vault = monitor.state(Integration.VAULT)
        listOf(Integration.LUCKPERMS to luckPerms, Integration.VAULT to vault).forEach { (integration, state) ->
            if (state.health == IntegrationHealth.FAILED) {
                out += Finding(
                    FindingLevel.WARN,
                    "${integration.label} is failing",
                    "JustChat skips it and uses the next metadata source. Error: ${state.detail}.",
                    "Check the console for errors from ${integration.label}'s provider plugin.",
                )
            }
        }
        if (vault.detail == IntegrationDetail.VAULT_WITHOUT_CHAT_PROVIDER) {
            out += Finding(
                FindingLevel.WARN,
                "Vault is installed but has no chat provider",
                "Vault cannot supply prefixes or suffixes.",
                "Install a permissions plugin that registers a Vault chat provider, or use LuckPerms directly.",
            )
        }
        val noLiveSource = luckPerms.health != IntegrationHealth.HEALTHY && vault.health != IntegrationHealth.HEALTHY
        if (noLiveSource && resolver.liveEverSeen) {
            out += Finding(
                FindingLevel.WARN,
                "No live metadata source is available",
                "Prefixes and suffixes come from the continuity cache (possibly stale) or configured defaults. Chat is unaffected.",
                "Check whether LuckPerms or your Vault chat provider loaded successfully.",
            )
        } else if (noLiveSource) {
            out += Finding(
                FindingLevel.INFO,
                "No permissions plugin detected",
                "Prefix, suffix and group come from the metadata.default-* settings.",
                "Optional: install LuckPerms, or any plugin that provides the Vault chat API, for per-group prefixes.",
            )
        }
    }

    private fun placeholderFindings(out: MutableList<Finding>) {
        val state = monitor.state(Integration.PLACEHOLDERAPI)
        val formatsNeedIt = configManager.current.usesExternalPlaceholders
        when {
            state.health == IntegrationHealth.FAILED -> out += Finding(
                FindingLevel.WARN,
                "PlaceholderAPI is failing",
                "External placeholders are handled by the unresolved-placeholder policy. Built-in placeholders and chat are unaffected. Error: ${state.detail}.",
                "Check the console for errors from PlaceholderAPI or its expansions.",
            )
            state.health == IntegrationHealth.UNAVAILABLE && formatsNeedIt -> out += Finding(
                FindingLevel.WARN,
                "PlaceholderAPI unavailable",
                "External placeholders cannot expand. Core formatting is unaffected.",
                "Check whether your placeholder provider loaded successfully.",
            )
        }
    }

    private fun sqliteFindings(out: MutableList<Finding>) {
        val state = monitor.state(Integration.SQLITE)
        when {
            state.health == IntegrationHealth.FAILED -> out += Finding(
                FindingLevel.ERROR,
                "SQLite cache is failing",
                "Metadata continuity, ignore lists and color preferences are kept in memory only and will be lost on restart. ${persistence.pendingWriteCount()} write(s) are waiting. Error: ${state.detail}.",
                "Check free disk space and permissions for the JustChat data folder. JustChat retries automatically.",
            )
            state.detail == IntegrationDetail.DISABLED_IN_CONFIG -> out += Finding(
                FindingLevel.INFO,
                "SQLite persistence is disabled",
                "Ignore lists, color preferences and the metadata continuity cache are lost on restart.",
                "Set continuity.sqlite to true in config.yml to persist them.",
            )
        }
    }
}
