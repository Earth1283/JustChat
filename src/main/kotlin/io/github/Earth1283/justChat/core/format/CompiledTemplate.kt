package io.github.Earth1283.justChat.core.format

import io.github.Earth1283.justChat.core.config.UnresolvedPolicy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

class TemplateException(message: String) : RuntimeException(message)

class CompiledTemplate private constructor(
    val source: String,
    private val root: Node,
    private val slots: Array<Slot>,
) {
    val usesExternal: Boolean = slots.any { it is Slot.External }

    fun render(ctx: FormatContext): Component {
        if (slots.isEmpty()) return (root as? Node.Static)?.component ?: renderNode(root, ctx, emptyArray())
        val values = arrayOfNulls<Component>(slots.size)
        for (i in slots.indices) values[i] = slotValue(slots[i], ctx)
        @Suppress("UNCHECKED_CAST")
        return renderNode(root, ctx, values as Array<Component>)
    }

    private fun renderNode(node: Node, ctx: FormatContext, values: Array<Component>): Component = when (node) {
        is Node.Static -> node.component
        is Node.Dynamic -> {
            val b = Component.text()
            b.style(node.style.resolve(this, ctx, values))
            var seenSlot = false
            val content = StringBuilder()
            for (part in node.parts) {
                when (part) {
                    is Part.Lit -> if (!seenSlot) content.append(part.text) else b.append(Component.text(part.text))
                    is Part.SlotRef -> {
                        seenSlot = true
                        val v = values[part.index]
                        if (v !== Component.empty()) b.append(v)
                    }
                }
            }
            b.content(content.toString())
            for (child in node.children) b.append(renderNode(child, ctx, values))
            b.build()
        }
    }

    private fun plainOf(parts: Array<Part>, values: Array<Component>): String {
        val sb = StringBuilder()
        for (part in parts) when (part) {
            is Part.Lit -> sb.append(part.text)
            is Part.SlotRef -> sb.append(PlainTextComponentSerializer.plainText().serialize(values[part.index]))
        }
        return sb.toString()
    }

    private fun slotValue(slot: Slot, ctx: FormatContext): Component = when (slot) {
        is Slot.Builtin -> builtinValue(slot.placeholder, ctx)
        is Slot.External -> externalValue(slot.raw, ctx)
    }

    private fun externalValue(raw: String, ctx: FormatContext): Component {
        val expanded = try {
            ctx.external?.expand(ctx.sender.subject, raw)
        } catch (_: Throwable) {
            null
        }
        if (expanded == null || expanded == raw) {
            return when (ctx.unresolved) {
                UnresolvedPolicy.KEEP -> Component.text(raw)
                UnresolvedPolicy.EMPTY -> Component.empty()
            }
        }
        return LegacyText.toComponent(expanded)
    }

    private fun builtinValue(p: Placeholder, ctx: FormatContext): Component {
        val s = ctx.sender
        val r = ctx.receiver
        return when (p) {
            Placeholder.PLAYER, Placeholder.USERNAME, Placeholder.SENDER -> Component.text(s.username)
            Placeholder.DISPLAY_NAME -> LegacyText.toComponent(s.displayName)
            Placeholder.MESSAGE -> ctx.message
            Placeholder.WORLD -> Component.text(s.world ?: "")
            Placeholder.PREFIX -> LegacyText.toComponent(s.metadata.prefix)
            Placeholder.SUFFIX -> LegacyText.toComponent(s.metadata.suffix)
            Placeholder.GROUP -> Component.text(s.metadata.group)
            Placeholder.UUID -> Component.text(s.uuid.toString())
            Placeholder.RECEIVER -> Component.text(r?.username ?: "")
            Placeholder.RECEIVER_DISPLAY_NAME -> LegacyText.toComponent(r?.displayName)
            Placeholder.RECEIVER_PREFIX -> LegacyText.toComponent(r?.metadata?.prefix)
            Placeholder.RECEIVER_SUFFIX -> LegacyText.toComponent(r?.metadata?.suffix)
            Placeholder.RECEIVER_GROUP -> Component.text(r?.metadata?.group ?: "")
        }
    }

    private sealed interface Slot {
        class Builtin(val placeholder: Placeholder) : Slot
        class External(val raw: String) : Slot
    }

    private sealed interface Part {
        class Lit(val text: String) : Part
        class SlotRef(val index: Int) : Part
    }

    private sealed interface Node {
        class Static(val component: Component) : Node
        class Dynamic(val parts: Array<Part>, val style: StyleSpec, val children: Array<Node>) : Node
    }

    private class StyleSpec(
        val base: Style,
        val hover: Node?,
        val clickAction: ClickEvent.Action<ClickEvent.Payload.Text>?,
        val clickParts: Array<Part>?,
        val insertion: Array<Part>?,
    ) {
        val isStatic: Boolean get() = hover == null && clickParts == null && insertion == null

        fun resolve(t: CompiledTemplate, ctx: FormatContext, values: Array<Component>): Style {
            if (isStatic) return base
            var s = base
            if (hover != null) s = s.hoverEvent(HoverEvent.showText(t.renderNode(hover, ctx, values)))
            if (clickParts != null && clickAction != null) {
                s = s.clickEvent(ClickEvent.clickEvent(clickAction, ClickEvent.Payload.string(t.plainOf(clickParts, values))))
            }
            if (insertion != null) s = s.insertion(t.plainOf(insertion, values))
            return s
        }
    }

    companion object {
        private const val OPEN = ''
        private const val CLOSE = ''
        private val EXTERNAL = Regex("%[A-Za-z][^%\\s<>]*%")

        private val mini: MiniMessage = MiniMessage.miniMessage()

        private fun marker(index: Int) = "$OPEN$index$CLOSE"

        fun compile(source: String, allowed: Set<Placeholder>): CompiledTemplate {
            val slots = ArrayList<Slot>()
            val builtinIndex = HashMap<Placeholder, Int>()
            val externalIndex = HashMap<String, Int>()

            val withExternal = EXTERNAL.replace(source) { m ->
                val idx = externalIndex.getOrPut(m.value) { slots.add(Slot.External(m.value)); slots.size - 1 }
                marker(idx)
            }

            val resolvers = allowed.map { p ->
                val idx = slots.size.also { slots.add(Slot.Builtin(p)) }
                builtinIndex[p] = idx
                TagResolver.resolver(p.tag, Tag.preProcessParsed(marker(idx)))
            }

            val tree = try {
                mini.deserialize(withExternal, TagResolver.resolver(resolvers))
            } catch (e: Exception) {
                throw TemplateException("Format could not be parsed: ${e.message}")
            }

            val root = convert(tree)

            return prune(source, root, slots)
        }

        private fun prune(source: String, root: Node, slots: List<Slot>): CompiledTemplate {
            val used = sortedSetOf<Int>()
            collectUsed(root, used)
            if (used.size == slots.size) return CompiledTemplate(source, root, slots.toTypedArray())
            val remap = HashMap<Int, Int>()
            val kept = ArrayList<Slot>()
            for (i in used) { remap[i] = kept.size; kept.add(slots[i]) }
            return CompiledTemplate(source, remapNode(root, remap), kept.toTypedArray())
        }

        private fun collectUsed(node: Node, out: MutableSet<Int>) {
            if (node !is Node.Dynamic) return
            node.parts.forEach { if (it is Part.SlotRef) out += it.index }
            node.children.forEach { collectUsed(it, out) }
            node.style.hover?.let { collectUsed(it, out) }
            node.style.clickParts?.forEach { if (it is Part.SlotRef) out += it.index }
            node.style.insertion?.forEach { if (it is Part.SlotRef) out += it.index }
        }

        private fun remapParts(parts: Array<Part>, map: Map<Int, Int>): Array<Part> =
            Array(parts.size) { i -> parts[i].let { if (it is Part.SlotRef) Part.SlotRef(map.getValue(it.index)) else it } }

        private fun remapNode(node: Node, map: Map<Int, Int>): Node = when (node) {
            is Node.Static -> node
            is Node.Dynamic -> Node.Dynamic(
                remapParts(node.parts, map),
                StyleSpec(
                    node.style.base,
                    node.style.hover?.let { remapNode(it, map) },
                    node.style.clickAction,
                    node.style.clickParts?.let { remapParts(it, map) },
                    node.style.insertion?.let { remapParts(it, map) },
                ),
                Array(node.children.size) { remapNode(node.children[it], map) },
            )
        }

        private fun hasMarker(s: String?) = s != null && (s.indexOf(OPEN) >= 0 || s.indexOf(CLOSE) >= 0)

        private fun hasMarker(c: Component): Boolean {
            if (c is TextComponent && hasMarker(c.content())) return true
            if (hasMarkerInStyle(c.style())) return true
            return c.children().any { hasMarker(it) }
        }

        private fun hasMarkerInStyle(style: Style): Boolean {
            if (hasMarker(style.insertion())) return true
            val click = style.clickEvent()
            val payload = click?.payload()
            if (payload is ClickEvent.Payload.Text && hasMarker(payload.value())) return true
            val hover = style.hoverEvent()
            if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
                val v = hover.value()
                if (v is Component && hasMarker(v)) return true
            }
            return false
        }

        private fun convert(c: Component): Node {
            if (!hasMarker(c)) return Node.Static(c)
            if (c !is TextComponent) {
                throw TemplateException("A placeholder is used inside a '${c.javaClass.simpleName}' tag, which is not supported.")
            }
            val parts = splitParts(c.content())
            val spec = styleSpec(c.style())
            val children = Array(c.children().size) { convert(c.children()[it]) }
            return Node.Dynamic(parts, spec, children)
        }

        private fun styleSpec(style: Style): StyleSpec {
            if (!hasMarkerInStyle(style)) return StyleSpec(style, null, null, null, null)
            var base = style
            var hoverNode: Node? = null
            val hover = style.hoverEvent()
            if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT && (hover.value() as? Component)?.let(::hasMarker) == true) {
                hoverNode = convert(hover.value() as Component)
                base = base.hoverEvent(null)
            }
            var clickAction: ClickEvent.Action<ClickEvent.Payload.Text>? = null
            var clickParts: Array<Part>? = null
            val payload = style.clickEvent()?.payload()
            if (payload is ClickEvent.Payload.Text && hasMarker(payload.value())) {
                @Suppress("UNCHECKED_CAST")
                clickAction = style.clickEvent()!!.action() as ClickEvent.Action<ClickEvent.Payload.Text>
                clickParts = splitParts(payload.value())
                base = base.clickEvent(null)
            }
            var insertionParts: Array<Part>? = null
            if (hasMarker(style.insertion())) {
                insertionParts = splitParts(style.insertion()!!)
                base = base.insertion(null)
            }
            return StyleSpec(base, hoverNode, clickAction, clickParts, insertionParts)
        }

        private fun splitParts(text: String): Array<Part> {
            val out = ArrayList<Part>()
            var i = 0
            val lit = StringBuilder()
            while (i < text.length) {
                val ch = text[i]
                if (ch == OPEN) {
                    val end = text.indexOf(CLOSE, i + 1)
                    val index = if (end > i + 1) text.substring(i + 1, end).toIntOrNull() else null
                    if (index == null) throw TemplateException(unsplittable())
                    if (lit.isNotEmpty()) { out += Part.Lit(lit.toString()); lit.setLength(0) }
                    out += Part.SlotRef(index)
                    i = end + 1
                } else if (ch == CLOSE) {
                    throw TemplateException(unsplittable())
                } else {
                    lit.append(ch); i++
                }
            }
            if (lit.isNotEmpty()) out += Part.Lit(lit.toString())
            return out.toTypedArray()
        }

        private fun unsplittable() =
            "A placeholder was split apart by a tag such as <gradient> or <rainbow>. Placeholders cannot be used inside those tags."
    }
}
