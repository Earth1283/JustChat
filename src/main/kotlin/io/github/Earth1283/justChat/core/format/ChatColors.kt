package io.github.Earth1283.justChat.core.format

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import java.util.Locale

object ChatColors {
    val namedColorNames: List<String> = NamedTextColor.NAMES.keys().toList().sorted()

    fun parse(input: String): TextColor? {
        val text = input.trim().lowercase(Locale.ROOT)
        if (text.startsWith("#")) return TextColor.fromCSSHexString(text)
        return NamedTextColor.NAMES.value(text)
    }

    fun serialize(color: TextColor): String =
        (color as? NamedTextColor)?.let { NamedTextColor.NAMES.key(it) } ?: color.asHexString()
}
