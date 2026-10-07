package io.github.Earth1283.justChat.core.metadata

import io.github.Earth1283.justChat.core.config.MetadataDefaults
import io.github.Earth1283.justChat.core.health.Integration
import io.github.Earth1283.justChat.core.health.IntegrationMonitor
import io.github.Earth1283.justChat.core.model.CapabilityHealth
import io.github.Earth1283.justChat.core.model.ChatMetadata
import io.github.Earth1283.justChat.core.model.MetadataSource
import io.github.Earth1283.justChat.core.model.ResolvedMetadata
import java.util.UUID
import java.util.concurrent.atomic.LongAdder

fun interface MetadataSink {
    fun record(uuid: UUID, username: String, metadata: ChatMetadata, source: MetadataSource)
}

class LiveSource(val source: MetadataSource, val integration: Integration, val provider: MetadataProvider) {
    @Volatile
    internal var breakerOpenUntilNanos: Long = 0L
}

class MetadataResolver(
    private val cache: MemoryMetadataCache,
    private val defaults: () -> MetadataDefaults,
    private val sink: MetadataSink,
    private val monitor: IntegrationMonitor,
    private val nanoClock: () -> Long = System::nanoTime,
) {
    @Volatile
    private var live: List<LiveSource> = emptyList()

    @Volatile
    var lastSource: MetadataSource = MetadataSource.DEFAULT
        private set

    @Volatile
    var liveEverSeen: Boolean = false

    private val counters = Array(MetadataSource.entries.size) { LongAdder() }

    fun setLiveSources(sources: List<LiveSource>) {
        live = sources
        if (sources.isNotEmpty()) liveEverSeen = true
    }

    fun liveSources(): List<LiveSource> = live

    fun hits(source: MetadataSource): Long = counters[source.ordinal].sum()

    fun resolve(uuid: UUID, username: String): ResolvedMetadata {
        val d = defaults()
        try {
            for (ls in live) {
                if (ls.breakerOpenUntilNanos != 0L && nanoClock() < ls.breakerOpenUntilNanos) continue
                val m = try {
                    ls.provider.get(uuid)
                } catch (t: Throwable) {
                    monitor.failure(ls.integration, t)
                    ls.breakerOpenUntilNanos = nanoClock() + BREAKER_NANOS
                    null
                }
                if (m != null) {
                    monitor.success(ls.integration)
                    liveEverSeen = true
                    if (cache.putLive(uuid, m, username, ls.source)) sink.record(uuid, username, m, ls.source)
                    return done(m, ls.source, d)
                }
            }
            cache.get(uuid)?.let {
                return done(it.metadata, if (it.fromStore) MetadataSource.SQLITE_CACHE else MetadataSource.MEMORY_CACHE, d)
            }
        } catch (_: Throwable) {
            resolverFaulted = true
            return done(null, MetadataSource.DEFAULT, d, faulted = true)
        }
        return done(null, MetadataSource.DEFAULT, d)
    }

    @Volatile
    private var resolverFaulted = false

    fun capabilityHealth(): CapabilityHealth {
        if (resolverFaulted) return CapabilityHealth.FAILED
        return when (lastSource) {
            MetadataSource.LUCKPERMS_API, MetadataSource.VAULT_API -> CapabilityHealth.HEALTHY
            MetadataSource.MEMORY_CACHE, MetadataSource.SQLITE_CACHE -> CapabilityHealth.DEGRADED
            MetadataSource.DEFAULT -> if (liveEverSeen) CapabilityHealth.DEGRADED else CapabilityHealth.HEALTHY
        }
    }

    private fun done(m: ChatMetadata?, source: MetadataSource, d: MetadataDefaults, faulted: Boolean = false): ResolvedMetadata {
        if (!faulted && resolverFaulted) resolverFaulted = false
        lastSource = source
        counters[source.ordinal].increment()
        return ResolvedMetadata(
            prefix = m?.prefix ?: d.prefix,
            suffix = m?.suffix ?: d.suffix,
            group = m?.primaryGroup ?: d.group,
            source = source,
        )
    }

    private companion object {
        const val BREAKER_NANOS = 5_000_000_000L
    }
}
