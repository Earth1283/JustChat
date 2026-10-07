package io.github.Earth1283.justChat.core.format

import io.github.Earth1283.justChat.core.config.UnresolvedPolicy
import io.github.Earth1283.justChat.core.context
import io.github.Earth1283.justChat.core.party
import io.github.Earth1283.justChat.core.plain
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompiledTemplateTest {
    private fun compile(format: String) = CompiledTemplate.compile(format, Placeholder.CHAT)

    @Test
    fun `renders every built-in placeholder`() {
        val template = compile("<prefix>|<suffix>|<player>|<username>|<display_name>|<world>|<group>|<uuid>|<message>")
        val sender = party(name = "Steve", displayName = "Stevie", world = "nether", prefix = "&c[Admin] ", suffix = "&7!", group = "admin")
        val rendered = template.render(context(sender = sender, message = "hi")).plain()
        assertEquals("[Admin] |!|Steve|Steve|Stevie|nether|admin|${sender.uuid}|hi", rendered)
    }

    @Test
    fun `default format renders prefix name arrow and message`() {
        val template = compile("<prefix><player> <dark_gray>»</dark_gray> <message>")
        val rendered = template.render(context(sender = party(prefix = "&6[VIP] "), message = "yo"))
        assertEquals("[VIP] Steve » yo", rendered.plain())
    }

    @Test
    fun `styles around placeholders apply to their values`() {
        val template = compile("<gray><player></gray> <message>")
        val rendered = template.render(context(message = "hey"))
        assertEquals(NamedTextColor.GRAY, styleOf(rendered, "Steve").color())
        assertEquals(null, styleOf(rendered, "hey").color())
    }

    @Test
    fun `placeholders inside hover text are substituted`() {
        val template = compile("<hover:show_text:'World: <world>'><player></hover> <message>")
        val rendered = template.render(context(sender = party(world = "end")))
        val hover = assertNotNull(styleOf(rendered, "Steve").hoverEvent())
        assertEquals(HoverEvent.Action.SHOW_TEXT, hover.action())
        assertEquals("World: end", (hover.value() as Component).plain())
    }

    @Test
    fun `placeholders inside click values are substituted`() {
        val template = compile("<click:suggest_command:'/msg <username> '><player></click> <message>")
        val rendered = template.render(context(sender = party(name = "Alex")))
        val click = assertNotNull(styleOf(rendered, "Alex").clickEvent())
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, click.action())
        assertEquals("/msg Alex ", (click.payload() as ClickEvent.Payload.Text).value())
    }

    @Test
    fun `fully static format is reused without allocation`() {
        val template = CompiledTemplate.compile("<red>no placeholders", Placeholder.CHAT)
        assertSame(template.render(context()), template.render(context()))
    }

    @Test
    fun `placeholders inside gradient are rejected at compile time`() {
        assertFailsWith<TemplateException> { compile("<gradient:red:blue><player></gradient> <message>") }
    }

    @Test
    fun `unknown tags are left as literal text`() {
        val rendered = compile("<nonsense><player>: <message>").render(context())
        assertTrue(rendered.plain().contains("Steve: hello"))
    }

    @Test
    fun `external placeholders are expanded when a provider resolves them`() {
        val template = compile("%player_ping%ms <message>")
        assertTrue(template.usesExternal)
        val rendered = template.render(context(external = { _, p -> if (p == "%player_ping%") "42" else null }))
        assertEquals("42ms hello", rendered.plain())
    }

    @Test
    fun `external placeholders keep their text when unresolved and policy is KEEP`() {
        val template = compile("%vault_rank% <message>")
        val rendered = template.render(context(external = null, unresolved = UnresolvedPolicy.KEEP))
        assertEquals("%vault_rank% hello", rendered.plain())
    }

    @Test
    fun `external placeholders disappear when unresolved and policy is EMPTY`() {
        val template = compile("[%vault_rank%] <message>")
        val rendered = template.render(context(external = { _, p -> p }, unresolved = UnresolvedPolicy.EMPTY))
        assertEquals("[] hello", rendered.plain())
    }

    @Test
    fun `a throwing external provider degrades to the unresolved policy`() {
        val template = compile("%boom% <message>")
        val rendered = template.render(context(external = { _, _ -> error("expansion exploded") }))
        assertEquals("%boom% hello", rendered.plain())
    }

    @Test
    fun `external placeholder output may contain legacy colors`() {
        val template = compile("%rank% <message>")
        val rendered = template.render(context(external = { _, _ -> "&aGreen" }))
        assertEquals(NamedTextColor.GREEN, styleOf(rendered, "Green").color())
    }

    @Test
    fun `private message placeholders describe sender and receiver`() {
        val template = CompiledTemplate.compile("<sender> -> <receiver> (<receiver_group>): <message>", Placeholder.PRIVATE_MESSAGE)
        val rendered = template.render(context(sender = party("Alice"), receiver = party("Bob", group = "mod")))
        assertEquals("Alice -> Bob (mod): hello", rendered.plain())
    }

    @Test
    fun `message text is not parsed as a template`() {
        val rendered = compile("<player>: <message>").render(context(message = "<red><player>"))
        assertEquals("Steve: <red><player>", rendered.plain())
    }

    private fun styleOf(root: Component, text: String): Style =
        effectiveStyles(root, Style.empty()).first { it.first == text }.second

    private fun effectiveStyles(component: Component, inherited: Style): List<Pair<String, Style>> {
        val own = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
        val here = if (component is TextComponent) listOf(component.content() to own) else emptyList()
        return here + component.children().flatMap { effectiveStyles(it, own) }
    }
}
