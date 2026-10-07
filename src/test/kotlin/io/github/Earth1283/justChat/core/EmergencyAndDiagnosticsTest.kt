package io.github.Earth1283.justChat.core

import io.github.Earth1283.justChat.core.config.ConfigManager
import io.github.Earth1283.justChat.core.config.MetadataDefaults
import io.github.Earth1283.justChat.core.diagnostics.Diagnostics
import io.github.Earth1283.justChat.core.diagnostics.FindingLevel
import io.github.Earth1283.justChat.core.format.ChatPipeline
import io.github.Earth1283.justChat.core.format.Emergency
import io.github.Earth1283.justChat.core.format.MessageStyle
import io.github.Earth1283.justChat.core.format.PlayerSnapshot
import io.github.Earth1283.justChat.core.health.Integration
import io.github.Earth1283.justChat.core.health.IntegrationDetail
import io.github.Earth1283.justChat.core.health.IntegrationMonitor
import io.github.Earth1283.justChat.core.metadata.MemoryMetadataCache
import io.github.Earth1283.justChat.core.metadata.MetadataResolver
import io.github.Earth1283.justChat.core.model.IntegrationHealth
import io.github.Earth1283.justChat.core.persistence.PersistenceManager
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmergencyAndDiagnosticsTest {
    private val monitor = IntegrationMonitor()
    private val configManager = ConfigManager().also { it.reload { emptyMap() } }
    private val memoryCache = MemoryMetadataCache()
    private val persistence = PersistenceManager(Files.createTempDirectory("justchat-diag").resolve("db"), monitor)
    private val resolver = MetadataResolver(memoryCache, { MetadataDefaults("", "", "default") }, { _, _, _, _ -> }, monitor)
    private val pipeline = ChatPipeline(configManager, resolver)
    private val diagnostics = Diagnostics("Paper", configManager, pipeline, resolver, memoryCache, persistence, monitor)

    private fun levels() = diagnostics.findings().map { it.level }

    @Test
    fun `emergency formatter renders the compiled-in format`() {
        assertEquals("Steve » hello", Emergency.render(context()).plain())
    }

    @Test
    fun `emergency private format names both sides`() {
        val ctx = context(sender = party("Alice"), receiver = party("Bob"))
        assertEquals("[You → Bob] hello", Emergency.renderPrivate(ctx, outgoing = true).plain())
        assertEquals("[Alice → You] hello", Emergency.renderPrivate(ctx, outgoing = false).plain())
    }

    @Test
    fun `the pipeline formats a public message with the default config`() {
        val player = PlayerSnapshot(UUID.randomUUID(), "Steve", "Steve", "world", null)
        val rendered = pipeline.renderPublic(player, "hi", MessageStyle(false, false, null))
        assertEquals("Steve » hi", rendered.plain())
    }

    @Test
    fun `an empty server reports only an informational finding`() {
        monitor.unavailable(Integration.SQLITE, IntegrationDetail.NOT_DETECTED)
        assertEquals(listOf(FindingLevel.INFO), levels())
    }

    @Test
    fun `a failing sqlite store is an error and says what is lost`() {
        monitor.set(Integration.SQLITE, IntegrationHealth.FAILED, "disk full")
        val finding = diagnostics.findings().first { it.level == FindingLevel.ERROR }
        assertTrue(finding.impact.contains("disk full"))
        assertTrue(finding.impact.contains("lost on restart"))
    }

    @Test
    fun `vault without a chat provider is called out`() {
        monitor.unavailable(Integration.VAULT, IntegrationDetail.VAULT_WITHOUT_CHAT_PROVIDER)
        assertTrue(diagnostics.findings().any { it.level == FindingLevel.WARN && it.title.contains("no chat provider") })
    }

    @Test
    fun `placeholderapi missing is only a warning when formats use it`() {
        monitor.unavailable(Integration.PLACEHOLDERAPI, IntegrationDetail.NOT_DETECTED)
        assertTrue(diagnostics.findings().none { it.title.contains("PlaceholderAPI") })
        configManager.reload { mapOf("chat.format" to "%player_ping% <message>") }
        assertTrue(diagnostics.findings().any { it.level == FindingLevel.WARN && it.title == "PlaceholderAPI unavailable" })
    }

    @Test
    fun `a rejected reload is reported with its reasons`() {
        configManager.reload { mapOf("chat.format" to "nothing useful") }
        val finding = diagnostics.findings().first { it.level == FindingLevel.ERROR }
        assertTrue(finding.impact.contains("<message>"))
    }

    @Test
    fun `status rows always include every integration and cache`() {
        val labels = diagnostics.statusRows().map { it.label }
        listOf("Platform", "Chat pipeline", "Metadata source", "LuckPerms API", "Vault API", "PlaceholderAPI", "Memory cache", "SQLite cache")
            .forEach { assertTrue(it in labels, it) }
    }

    @Test
    fun `every status row carries hover help for label and value`() {
        diagnostics.statusRows().forEach {
            assertTrue(it.labelHelp.isNotBlank() && it.valueHelp.isNotBlank(), it.label)
        }
    }
}
