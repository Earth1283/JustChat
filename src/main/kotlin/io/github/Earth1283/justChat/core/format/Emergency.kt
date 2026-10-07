package io.github.Earth1283.justChat.core.format

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object Emergency {
    const val FORMAT = "<gray><player></gray> <dark_gray>»</dark_gray> <white><message></white>"

    private val compiledFormat: CompiledTemplate? = runCatching { CompiledTemplate.compile(FORMAT, Placeholder.CHAT) }.getOrNull()

    fun render(ctx: FormatContext): Component =
        runCatching { compiledFormat?.render(ctx) }.getOrNull() ?: handBuilt(ctx)

    fun renderPrivate(ctx: FormatContext, outgoing: Boolean): Component {
        val label = if (outgoing) "You → ${ctx.receiver?.username.orEmpty()}" else "${ctx.sender.username} → You"
        return Component.text()
            .append(Component.text("[$label] ", NamedTextColor.GRAY))
            .append(ctx.message)
            .build()
    }

    private fun handBuilt(ctx: FormatContext): Component = Component.text()
        .append(Component.text(ctx.sender.username, NamedTextColor.GRAY))
        .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
        .append(ctx.message.colorIfAbsent(NamedTextColor.WHITE))
        .build()
}
