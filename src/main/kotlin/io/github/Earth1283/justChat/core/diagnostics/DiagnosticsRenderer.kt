package io.github.Earth1283.justChat.core.diagnostics

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

object DiagnosticsRenderer {
    private const val LABEL_COLUMN_WIDTH = 20

    fun status(rows: List<StatusRow>): Component {
        val out = Component.text().append(Component.text("JustChat Status", NamedTextColor.GOLD, TextDecoration.BOLD))
        out.append(Component.newline())
        for (row in rows) {
            out.append(Component.newline())
            out.append(label(row))
            out.append(value(row))
        }
        return out.build()
    }

    fun doctor(findings: List<Finding>): Component {
        val out = Component.text().append(Component.text("JustChat Doctor", NamedTextColor.GOLD, TextDecoration.BOLD))
        for (finding in findings) {
            out.append(Component.newline()).append(Component.newline())
            out.append(Component.text("[${tag(finding.level)}] ", levelColor(finding.level), TextDecoration.BOLD))
            out.append(Component.text(finding.title, NamedTextColor.WHITE))
            out.append(Component.newline())
            out.append(Component.text("Impact: ", NamedTextColor.GRAY)).append(Component.text(finding.impact, NamedTextColor.WHITE))
            finding.suggestedAction?.let {
                out.append(Component.newline()).append(Component.newline())
                out.append(Component.text("Suggested action:", NamedTextColor.GRAY))
                out.append(Component.newline()).append(Component.text(it, NamedTextColor.WHITE))
            }
        }
        return out.build()
    }

    private fun label(row: StatusRow): Component {
        val padded = row.label.padEnd(LABEL_COLUMN_WIDTH)
        return Component.text(padded, NamedTextColor.GRAY).hoverEvent(HoverEvent.showText(Component.text(row.labelHelp, NamedTextColor.GRAY)))
    }

    private fun value(row: StatusRow): Component =
        Component.text(row.value, toneColor(row.tone)).hoverEvent(HoverEvent.showText(Component.text(row.valueHelp, NamedTextColor.GRAY)))

    private fun toneColor(tone: Tone): TextColor = when (tone) {
        Tone.GOOD -> NamedTextColor.GREEN
        Tone.NEUTRAL -> NamedTextColor.GRAY
        Tone.WARN -> NamedTextColor.YELLOW
        Tone.BAD -> NamedTextColor.RED
    }

    private fun levelColor(level: FindingLevel): TextColor = when (level) {
        FindingLevel.OK -> NamedTextColor.GREEN
        FindingLevel.INFO -> NamedTextColor.AQUA
        FindingLevel.WARN -> NamedTextColor.YELLOW
        FindingLevel.ERROR -> NamedTextColor.RED
    }

    private fun tag(level: FindingLevel): String = when (level) {
        FindingLevel.OK -> " OK "
        FindingLevel.INFO -> "INFO"
        FindingLevel.WARN -> "WARN"
        FindingLevel.ERROR -> "FAIL"
    }
}
