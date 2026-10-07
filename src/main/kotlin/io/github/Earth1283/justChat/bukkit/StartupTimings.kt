package io.github.Earth1283.justChat.bukkit

class StartupTimings {
    private val phases = LinkedHashMap<String, Double>()
    private val startedAtNanos = System.nanoTime()

    fun <T> measure(phase: String, block: () -> T): T {
        val phaseStart = System.nanoTime()
        try {
            return block()
        } finally {
            phases[phase] = (System.nanoTime() - phaseStart) / NANOS_PER_MILLI
        }
    }

    fun totalMillis(): Double = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI

    fun breakdown(): List<String> =
        phases.map { (phase, millis) -> "%-16s %6.1fms".format(phase, millis) } + "%-16s %6.1fms".format("Total", totalMillis())

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
