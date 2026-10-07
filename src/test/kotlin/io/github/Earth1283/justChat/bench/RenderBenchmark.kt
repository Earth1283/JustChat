package io.github.Earth1283.justChat.bench

import io.github.Earth1283.justChat.core.config.ConfigManager
import io.github.Earth1283.justChat.core.config.MetadataDefaults
import io.github.Earth1283.justChat.core.format.ChatPipeline
import io.github.Earth1283.justChat.core.format.MessageStyle
import io.github.Earth1283.justChat.core.format.PlayerSnapshot
import io.github.Earth1283.justChat.core.health.Integration
import io.github.Earth1283.justChat.core.health.IntegrationMonitor
import io.github.Earth1283.justChat.core.metadata.LiveSource
import io.github.Earth1283.justChat.core.metadata.MemoryMetadataCache
import io.github.Earth1283.justChat.core.metadata.MetadataProvider
import io.github.Earth1283.justChat.core.metadata.MetadataResolver
import io.github.Earth1283.justChat.core.model.ChatMetadata
import io.github.Earth1283.justChat.core.model.MetadataSource
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.lang.management.ManagementFactory
import java.util.UUID

private const val WARMUP_ITERATIONS = 100_000
private const val MEASURED_ITERATIONS = 200_000
private const val PLAYER_COUNT = 1_000

private class Sample(val microseconds: DoubleArray, val allocatedBytesPerOp: Long) {
    fun percentile(p: Double) = microseconds[((microseconds.size - 1) * p).toInt()]
}

private fun measure(operation: (Int) -> Unit): Sample {
    repeat(WARMUP_ITERATIONS) { operation(it) }
    val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    val threadId = Thread.currentThread().id
    val timings = DoubleArray(MEASURED_ITERATIONS)
    val allocatedBefore = threads.getThreadAllocatedBytes(threadId)
    for (i in 0 until MEASURED_ITERATIONS) {
        val start = System.nanoTime()
        operation(i)
        timings[i] = (System.nanoTime() - start) / 1_000.0
    }
    val allocated = threads.getThreadAllocatedBytes(threadId) - allocatedBefore
    timings.sort()
    return Sample(timings, allocated / MEASURED_ITERATIONS)
}

private fun report(name: String, sample: Sample) = println(
    "%-44s p50 %6.2fus   p95 %6.2fus   p99 %7.2fus   %6d B/op".format(
        name, sample.percentile(0.50), sample.percentile(0.95), sample.percentile(0.99), sample.allocatedBytesPerOp,
    ),
)

fun main() {
    val monitor = IntegrationMonitor()
    val configManager = ConfigManager()
    val resolver = MetadataResolver(MemoryMetadataCache(), { MetadataDefaults("", "", "default") }, { _, _, _, _ -> }, monitor)
    val provider = MetadataProvider { ChatMetadata("&c[Admin] ", "&7*", "admin") }
    resolver.setLiveSources(listOf(LiveSource(MetadataSource.LUCKPERMS_API, Integration.LUCKPERMS, provider)))
    val pipeline = ChatPipeline(configManager, resolver)
    val style = MessageStyle(allowLegacyColors = false, allowMiniMessage = false, preferredColor = null)

    val players = List(PLAYER_COUNT) { PlayerSnapshot(UUID.randomUUID(), "Player$it", "Player$it", "world", null) }
    val messages = listOf("hello everyone", "anyone want to trade diamonds for iron?", "brb", "gg")

    report("pipeline: live metadata, default format", measure { i ->
        pipeline.renderPublic(players[i % PLAYER_COUNT], messages[i and 3], style)
    })

    val cachedOnly = MetadataResolver(MemoryMetadataCache(), { MetadataDefaults("", "", "default") }, { _, _, _, _ -> }, monitor)
    val cachedPipeline = ChatPipeline(configManager, cachedOnly)
    val admin = ChatMetadata("&c[Admin] ", "&7*", "admin")
    players.forEach { cachedOnly.setLiveSources(listOf(LiveSource(MetadataSource.LUCKPERMS_API, Integration.LUCKPERMS) { admin })); cachedOnly.resolve(it.uuid, it.username) }
    cachedOnly.setLiveSources(emptyList())
    report("pipeline: cached metadata (provider gone)", measure { i ->
        cachedPipeline.renderPublic(players[i % PLAYER_COUNT], messages[i and 3], style)
    })

    val mini = MiniMessage.miniMessage()
    val format = "<prefix><player> <dark_gray>»</dark_gray> <message>"
    report("baseline: MiniMessage parse per message", measure { i ->
        mini.deserialize(
            format,
            TagResolver.resolver(
                Placeholder.parsed("prefix", "<red>[Admin] </red>"),
                Placeholder.unparsed("player", players[i % PLAYER_COUNT].username),
                Placeholder.unparsed("message", messages[i and 3]),
            ),
        )
    })
}
