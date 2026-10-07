package io.github.Earth1283.justChat.core.persistence

import io.github.Earth1283.justChat.core.health.Integration
import io.github.Earth1283.justChat.core.health.IntegrationMonitor
import io.github.Earth1283.justChat.core.model.ChatMetadata
import io.github.Earth1283.justChat.core.model.MetadataSource
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PersistenceManager(
    private val databaseFile: Path,
    private val monitor: IntegrationMonitor,
    private val openStore: (Path) -> ContinuityStore = SqliteContinuityStore::open,
    private val flushDelayMillis: Long = 3_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onStoreOpened: (openedInMillis: Long, hasLiveHistory: Boolean) -> Unit = { _, _ -> },
) {
    private val io: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "JustChat-IO").apply { isDaemon = true }
    }

    private val pendingMetadata = ConcurrentHashMap<UUID, MetadataRow>()
    private val pendingColors = ConcurrentHashMap<UUID, ColorRow>()
    private val pendingIgnoreOps = ArrayList<IgnoreOp>()
    private val flushScheduled = AtomicBoolean(false)

    @Volatile
    private var enabled = false

    private var store: ContinuityStore? = null
    private var reopenNotBeforeMillis = 0L
    private var reopenBackoffMillis = INITIAL_BACKOFF_MILLIS

    val isEnabled: Boolean get() = enabled

    fun enable() {
        if (enabled) return
        enabled = true
        io.execute { ensureStore() }
    }

    fun disable() {
        if (!enabled) return
        enabled = false
        io.execute {
            flushPending()
            closeStore()
            monitor.unavailable(Integration.SQLITE, "disabled in config")
            discardPending()
        }
    }

    fun queueMetadata(uuid: UUID, username: String?, metadata: ChatMetadata, source: MetadataSource) {
        if (!enabled) return
        pendingMetadata[uuid] = MetadataRow(uuid, username, metadata, source.name, clock())
        scheduleFlush()
    }

    fun queueColor(uuid: UUID, color: String?) {
        if (!enabled) return
        pendingColors[uuid] = ColorRow(uuid, color)
        scheduleFlush()
    }

    fun queueIgnore(op: IgnoreOp) {
        if (!enabled) return
        synchronized(pendingIgnoreOps) {
            if (pendingIgnoreOps.size >= MAX_PENDING_IGNORE_OPS) pendingIgnoreOps.removeAt(0)
            pendingIgnoreOps += op
        }
        scheduleFlush()
    }

    fun loadPlayer(uuid: UUID, timeoutMillis: Long): StoredPlayer? {
        if (!enabled) return null
        val result = io.submit<StoredPlayer?> {
            flushPending()
            ensureStore()?.let { readPlayer(it, uuid) }
        }
        return try {
            result.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            result.cancel(false)
            null
        }
    }

    fun flushBlocking(timeoutMillis: Long = 2_000) {
        runCatching { io.submit { flushPending() }.get(timeoutMillis, TimeUnit.MILLISECONDS) }
    }

    fun shutdown() {
        runCatching {
            io.submit {
                flushPending()
                closeStore()
            }.get(3, TimeUnit.SECONDS)
        }
        io.shutdownNow()
    }

    fun pendingWriteCount(): Int = pendingMetadata.size + pendingColors.size + synchronized(pendingIgnoreOps) { pendingIgnoreOps.size }

    private fun readPlayer(store: ContinuityStore, uuid: UUID): StoredPlayer? = try {
        store.loadPlayer(uuid).also { monitor.success(Integration.SQLITE) }
    } catch (t: Throwable) {
        failStore(t)
        null
    }

    private fun scheduleFlush(delayMillis: Long = flushDelayMillis) {
        if (!flushScheduled.compareAndSet(false, true)) return
        io.schedule({
            flushScheduled.set(false)
            flushPending()
        }, delayMillis, TimeUnit.MILLISECONDS)
    }

    private fun flushPending() {
        val batch = drainPending()
        if (batch.isEmpty) return
        val target = ensureStore()
        if (target == null) {
            restore(batch)
            return
        }
        try {
            target.write(batch)
            monitor.success(Integration.SQLITE)
        } catch (t: Throwable) {
            failStore(t)
            restore(batch)
        }
    }

    private fun drainPending(): WriteBatch {
        val metadata = pendingMetadata.keys.toList().mapNotNull { pendingMetadata.remove(it) }
        val colors = pendingColors.keys.toList().mapNotNull { pendingColors.remove(it) }
        val ignoreOps = synchronized(pendingIgnoreOps) { pendingIgnoreOps.toList().also { pendingIgnoreOps.clear() } }
        return WriteBatch(metadata, colors, ignoreOps)
    }

    private fun restore(batch: WriteBatch) {
        if (!enabled) return
        batch.metadata.forEach { pendingMetadata.putIfAbsent(it.uuid, it) }
        batch.colors.forEach { pendingColors.putIfAbsent(it.uuid, it) }
        synchronized(pendingIgnoreOps) { pendingIgnoreOps.addAll(0, batch.ignoreOpsInOrder) }
        scheduleFlush(reopenBackoffMillis)
    }

    private fun discardPending() {
        pendingMetadata.clear()
        pendingColors.clear()
        synchronized(pendingIgnoreOps) { pendingIgnoreOps.clear() }
    }

    private fun ensureStore(): ContinuityStore? {
        store?.let { return it }
        if (!enabled || clock() < reopenNotBeforeMillis) return null
        val startedAt = System.nanoTime()
        return try {
            val opened = openStore(databaseFile)
            store = opened
            reopenBackoffMillis = INITIAL_BACKOFF_MILLIS
            monitor.healthy(Integration.SQLITE)
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
            onStoreOpened(elapsedMillis, runCatching { opened.hasLiveHistory() }.getOrDefault(false))
            opened
        } catch (t: Throwable) {
            failStore(t)
            null
        }
    }

    private fun failStore(t: Throwable) {
        monitor.failure(Integration.SQLITE, t)
        closeStore()
        reopenNotBeforeMillis = clock() + reopenBackoffMillis
        reopenBackoffMillis = (reopenBackoffMillis * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
    }

    private fun closeStore() {
        runCatching { store?.close() }
        store = null
    }

    private companion object {
        const val INITIAL_BACKOFF_MILLIS = 15_000L
        const val MAX_BACKOFF_MILLIS = 300_000L
        const val MAX_PENDING_IGNORE_OPS = 10_000
    }
}
