package io.github.Earth1283.justChat.core.metadata

import io.github.Earth1283.justChat.core.config.MetadataDefaults
import io.github.Earth1283.justChat.core.health.Integration
import io.github.Earth1283.justChat.core.health.IntegrationMonitor
import io.github.Earth1283.justChat.core.model.CapabilityHealth
import io.github.Earth1283.justChat.core.model.ChatMetadata
import io.github.Earth1283.justChat.core.model.IntegrationHealth
import io.github.Earth1283.justChat.core.model.MetadataSource
import io.github.Earth1283.justChat.core.persistence.PersistenceManager
import io.github.Earth1283.justChat.core.persistence.SqliteContinuityStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class SwitchableProvider(var metadata: ChatMetadata?, var failure: Throwable? = null) : MetadataProvider {
    var calls = 0

    override fun get(uuid: UUID): ChatMetadata? {
        calls++
        failure?.let { throw it }
        return metadata
    }
}

class MetadataChaosTest {
    private val player = UUID.randomUUID()
    private val defaults = MetadataDefaults("", "", "default")
    private val directory: Path = Files.createTempDirectory("justchat-test")
    private val database = directory.resolve("justchat.db")
    private var now = 0L
    private val opened = mutableListOf<PersistenceManager>()

    @AfterTest
    fun cleanUp() {
        opened.forEach { it.shutdown() }
        directory.toFile().deleteRecursively()
    }

    private inner class Server(luckPerms: SwitchableProvider?, vault: SwitchableProvider? = null) {
        val monitor = IntegrationMonitor()
        val persistence = PersistenceManager(database, monitor, flushDelayMillis = 10).also { opened += it; it.enable() }
        val cache = MemoryMetadataCache()
        val resolver = MetadataResolver(cache, { defaults }, { uuid, name, metadata, source ->
            persistence.queueMetadata(uuid, name, metadata, source)
        }, monitor, nanoClock = { now })

        init {
            resolver.setLiveSources(
                listOfNotNull(
                    luckPerms?.let { LiveSource(MetadataSource.LUCKPERMS_API, Integration.LUCKPERMS, it) },
                    vault?.let { LiveSource(MetadataSource.VAULT_API, Integration.VAULT, it) },
                ),
            )
        }

        fun login() {
            persistence.loadPlayer(player, 2_000)?.metadata?.let {
                cache.putStored(player, it.metadata, it.username)
            }
        }

        fun resolve() = resolver.resolve(player, "Steve")

        fun settle() = persistence.flushBlocking()
    }

    @Test
    fun `luckperms outage then server restart then luckperms returns never loses chat`() {
        val admin = ChatMetadata("&c[Admin] ", "", "admin")
        val luckPerms = SwitchableProvider(admin)
        val first = Server(luckPerms)
        first.login()

        val live = first.resolve()
        assertEquals(MetadataSource.LUCKPERMS_API, live.source)
        assertEquals("&c[Admin] ", live.prefix)
        assertEquals(CapabilityHealth.HEALTHY, first.resolver.capabilityHealth())

        luckPerms.failure = IllegalStateException("LuckPerms is gone")
        val duringOutage = first.resolve()
        assertEquals(MetadataSource.MEMORY_CACHE, duringOutage.source)
        assertEquals("&c[Admin] ", duringOutage.prefix)
        assertEquals(IntegrationHealth.FAILED, first.monitor.health(Integration.LUCKPERMS))
        assertEquals(CapabilityHealth.DEGRADED, first.resolver.capabilityHealth())

        first.settle()
        first.persistence.shutdown()

        val restarted = Server(luckPerms = null)
        restarted.login()
        val fromSqlite = restarted.resolve()
        assertEquals(MetadataSource.SQLITE_CACHE, fromSqlite.source)
        assertEquals("&c[Admin] ", fromSqlite.prefix)
        assertEquals("admin", fromSqlite.group)

        val restored = Server(SwitchableProvider(ChatMetadata("&4[Owner] ", null, "owner")))
        restored.login()
        val authoritative = restored.resolve()
        assertEquals(MetadataSource.LUCKPERMS_API, authoritative.source)
        assertEquals("&4[Owner] ", authoritative.prefix)
        assertEquals("owner", authoritative.group)
    }

    @Test
    fun `vault supplies metadata when luckperms is absent and counts as healthy`() {
        val server = Server(luckPerms = null, vault = SwitchableProvider(ChatMetadata("&b[Vault] ", null, "vip")))
        val resolved = server.resolve()
        assertEquals(MetadataSource.VAULT_API, resolved.source)
        assertEquals(CapabilityHealth.HEALTHY, server.resolver.capabilityHealth())
    }

    @Test
    fun `luckperms is preferred over vault`() {
        val server = Server(SwitchableProvider(ChatMetadata("LP", null, null)), SwitchableProvider(ChatMetadata("V", null, null)))
        assertEquals("LP", server.resolve().prefix)
    }

    @Test
    fun `failing luckperms falls through to vault`() {
        val luckPerms = SwitchableProvider(null, failure = IllegalStateException("boom"))
        val server = Server(luckPerms, SwitchableProvider(ChatMetadata("V", null, null)))
        assertEquals(MetadataSource.VAULT_API, server.resolve().source)
    }

    @Test
    fun `a failing provider is skipped by the circuit breaker instead of throwing every message`() {
        val luckPerms = SwitchableProvider(null, failure = IllegalStateException("boom"))
        val server = Server(luckPerms)
        repeat(50) { server.resolve() }
        assertEquals(1, luckPerms.calls)
        now += 6_000_000_000L
        luckPerms.failure = null
        luckPerms.metadata = ChatMetadata("back", null, null)
        assertEquals("back", server.resolve().prefix)
    }

    @Test
    fun `all sources failing yields configured defaults and chat metadata still resolves`() {
        val server = Server(SwitchableProvider(null, failure = IllegalStateException("down")))
        val resolved = server.resolve()
        assertEquals(MetadataSource.DEFAULT, resolved.source)
        assertEquals("default", resolved.group)
        assertEquals(CapabilityHealth.DEGRADED, server.resolver.capabilityHealth())
    }

    @Test
    fun `an empty server with no integrations reports healthy metadata from defaults`() {
        val server = Server(luckPerms = null)
        assertEquals(MetadataSource.DEFAULT, server.resolve().source)
        assertEquals(CapabilityHealth.HEALTHY, server.resolver.capabilityHealth())
    }

    @Test
    fun `provider reporting no prefix is valid and falls back to default prefix field only`() {
        val server = Server(SwitchableProvider(ChatMetadata(null, null, "builder")))
        val resolved = server.resolve()
        assertEquals("", resolved.prefix)
        assertEquals("builder", resolved.group)
        assertEquals(MetadataSource.LUCKPERMS_API, resolved.source)
    }

    @Test
    fun `sqlite failure leaves the memory cache serving`() {
        val luckPerms = SwitchableProvider(ChatMetadata("P", null, "g"))
        val monitor = IntegrationMonitor()
        val brokenOpen = PersistenceManager(directory.resolve("nope/db"), monitor, openStore = { error("disk on fire") }, flushDelayMillis = 5)
            .also { opened += it; it.enable() }
        val cache = MemoryMetadataCache()
        val resolver = MetadataResolver(cache, { defaults }, { u, n, m, s -> brokenOpen.queueMetadata(u, n, m, s) }, monitor)
        resolver.setLiveSources(listOf(LiveSource(MetadataSource.LUCKPERMS_API, Integration.LUCKPERMS, luckPerms)))

        assertEquals("P", resolver.resolve(player, "Steve").prefix)
        luckPerms.failure = IllegalStateException("gone")
        brokenOpen.flushBlocking()
        val resolved = resolver.resolve(player, "Steve")

        assertEquals(MetadataSource.MEMORY_CACHE, resolved.source)
        assertEquals("P", resolved.prefix)
        assertEquals(IntegrationHealth.FAILED, monitor.health(Integration.SQLITE))
    }

    @Test
    fun `metadata is only written when it changes`() {
        val writes = mutableListOf<UUID>()
        val monitor = IntegrationMonitor()
        val resolver = MetadataResolver(MemoryMetadataCache(), { defaults }, { u, _, _, _ -> writes += u }, monitor)
        resolver.setLiveSources(listOf(LiveSource(MetadataSource.VAULT_API, Integration.VAULT, SwitchableProvider(ChatMetadata("x", null, null)))))
        repeat(100) { resolver.resolve(player, "Steve") }
        assertEquals(1, writes.size)
    }

    @Test
    fun `pinned players survive eviction pressure`() {
        val cache = MemoryMetadataCache(maxEntries = 10)
        val pinned = UUID.randomUUID()
        cache.pin(pinned)
        cache.putLive(pinned, ChatMetadata("keep", null, null), "P", MetadataSource.LUCKPERMS_API)
        repeat(100) { cache.putLive(UUID.randomUUID(), ChatMetadata("x", null, null), "N", MetadataSource.LUCKPERMS_API) }
        assertTrue(cache.get(pinned) != null)
        assertTrue(cache.size <= 11)
    }

    @Test
    fun `stored data never overwrites live data`() {
        val cache = MemoryMetadataCache()
        cache.putLive(player, ChatMetadata("live", null, null), "Steve", MetadataSource.LUCKPERMS_API)
        cache.putStored(player, ChatMetadata("old", null, null), "Steve")
        assertEquals("live", cache.get(player)?.metadata?.prefix)
    }
}
