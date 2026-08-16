package me.wuzzyxy.dynamicmarket.gui

import me.wuzzyxy.dynamicmarket.configs.GuiElementConfig
import me.wuzzyxy.dynamicmarket.configs.SharedMenuElements
import me.wuzzyxy.dynamicmarket.items.ItemResolver
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.item.BoundItem
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemBuilder

/***
 * Every slot in [layout] (row-major) holding [slot] — used for the repeated-but-distinct
 * positions a single addIngredient binding can't represent, where each occurrence needs its own
 * item: quantity tiers, gainers, fallers.
 */
internal fun layoutSlots(layout: List<String>, slot: Char): List<Int> =
    layout.flatMapIndexed { row, line ->
        line.filterNot(Char::isWhitespace).mapIndexedNotNull { col, char ->
            if (char == slot) row * 9 + col else null
        }
    }

/*** %token% substitution, run before MiniMessage sees the string — ours first, same order the reference bet GUI uses. */
internal fun String.withPlaceholders(placeholders: Map<String, String>): String =
    placeholders.entries.fold(this) { acc, (k, v) -> acc.replace("%$k%", v) }

/*** The placeholder name when the line is nothing but a single %token%, else null. */
private fun String.soleToken(): String? {
    val trimmed = trim()
    if (trimmed.length <= 2 || trimmed.first() != '%' || trimmed.last() != '%') return null
    if (trimmed.indexOf('%', 1) != trimmed.length - 1) return null
    return trimmed.substring(1, trimmed.length - 1)
}

private const val GROUP_OPEN = "[group]"
private const val GROUP_CLOSE = "[/group]"

private fun renderLoreLine(
    line: String,
    token: String?,
    placeholders: Map<String, String>,
    blocks: Map<String, List<String>>,
): List<String> = when {
    token == null -> listOf(line.withPlaceholders(placeholders))
    token in blocks -> blocks.getValue(token).map { it.withPlaceholders(placeholders) }
    else -> {
        val value = placeholders[token]
        if (value.isNullOrEmpty()) emptyList() else listOf(line.replace("%$token%", value))
    }
}

/***
 * A lore line that is *only* one placeholder and resolves empty is dropped rather than rendered
 * blank. [blocks] are the placeholders that carry more than one line — a category description,
 * say: a lore line that is only one of those becomes as many lines as the block has, and none at
 * all when it's empty, so nobody has to guess how long a description will be when laying out lore.
 *
 * That per-line rule can't save a heading or a spacer, which have no placeholder to resolve and
 * so would sit there introducing nothing. `[group]` … `[/group]` around a run of lines drops the
 * whole run unless at least one placeholder line inside it produced something — the heading goes
 * with the lines it was heading. Only lines that are *only* a placeholder keep a group alive;
 * decoration can't hold one open on its own. A `[group]` left unclosed runs to the end of the
 * lore, and opening a second one closes the first.
 */
internal fun List<String>.renderLore(
    placeholders: Map<String, String>,
    blocks: Map<String, List<String>> = emptyMap(),
): List<String> {
    val lore = mutableListOf<String>()
    var group: MutableList<String>? = null
    var filled = false

    fun closeGroup() {
        group?.let { if (filled) lore += it }
        group = null
    }

    for (line in this) {
        when (line.trim()) {
            GROUP_OPEN -> {
                closeGroup()
                group = mutableListOf()
                filled = false
            }
            GROUP_CLOSE -> closeGroup()
            else -> {
                val token = line.soleToken()
                val rendered = renderLoreLine(line, token, placeholders, blocks)
                if (token != null && rendered.isNotEmpty()) filled = true
                (group ?: lore) += rendered
            }
        }
    }
    closeGroup()
    return lore
}

/***
 * Turns a config element into a real ItemBuilder. [iconOverride] wins over the configured
 * icon — used for every element bound to an actual traded item, where the icon isn't really
 * configurable at all (see GuiElementConfig's doc comment).
 */
internal fun GuiElementConfig.render(
    resolver: ItemResolver,
    placeholders: Map<String, String> = emptyMap(),
    iconOverride: ItemStack? = null,
    blocks: Map<String, List<String>> = emptyMap(),
): ItemBuilder {
    val stack = iconOverride ?: icon?.let { resolver.resolve(it) } ?: ItemStack(Material.BARRIER)
    val builder = ItemBuilder(stack).setName(name.withPlaceholders(placeholders))
    val loreLines = lore.renderLore(placeholders, blocks)
    if (loreLines.isNotEmpty()) builder.addLoreLines(*loreLines.toTypedArray())
    return builder
}

fun fillerItem(config: GuiElementConfig, resolver: ItemResolver): Item =
    Item.simple(config.render(resolver).hideTooltip(true))

/***
 * What a content slot shows when there's nothing to put in it: no category on this page, no
 * mover today, no quantity tier for that slot. The screen's own `empty` wins, then `shared.empty`,
 * and with neither set the slot just blends into the filler the way it always did.
 *
 * An empty element with nothing to say gets no tooltip, so the default (a pane named " ") reads
 * as background rather than as a button with a blank hover box.
 */
internal fun emptySlotProvider(screen: GuiElementConfig?, shared: SharedMenuElements, resolver: ItemResolver): ItemBuilder {
    val config = screen ?: shared.empty ?: shared.filler
    val builder = config.render(resolver)
    return if (config.name.isBlank() && config.lore.isEmpty()) builder.hideTooltip(true) else builder
}

internal fun emptySlotItem(screen: GuiElementConfig?, shared: SharedMenuElements, resolver: ItemResolver): Item =
    Item.simple(emptySlotProvider(screen, shared, resolver))

/*** A button the admin kept, or plain filler where they deleted its section from menus.yml. */
internal fun buttonOrFiller(
    config: GuiElementConfig?,
    filler: GuiElementConfig,
    resolver: ItemResolver,
    build: (GuiElementConfig) -> Item,
): Item = if (config == null) fillerItem(filler, resolver) else build(config)

/***
 * Page nav that only exists when there's somewhere to go — a menu that fits on one page shows
 * filler in both slots rather than a dead button that clicks for nothing.
 */
internal fun prevPageItem(config: GuiElementConfig?, filler: GuiElementConfig, resolver: ItemResolver): Item =
    BoundItem.pagedBuilder()
        .setItemProvider { _, gui ->
            if (config != null && gui.page > 0) config.render(resolver) else filler.render(resolver).hideTooltip(true)
        }
        .addClickHandler { _, gui, _ -> if (gui.page > 0) gui.page-- }
        .build()

internal fun nextPageItem(config: GuiElementConfig?, filler: GuiElementConfig, resolver: ItemResolver): Item =
    BoundItem.pagedBuilder()
        .setItemProvider { _, gui ->
            if (config != null && gui.page < gui.pageCount - 1) config.render(resolver) else filler.render(resolver).hideTooltip(true)
        }
        .addClickHandler { _, gui, _ -> if (gui.page < gui.pageCount - 1) gui.page++ }
        .build()

fun closeItem(config: GuiElementConfig, resolver: ItemResolver): Item = Item.builder()
    .setItemProvider(config.render(resolver))
    .addClickHandler { click -> click.player().closeInventory() }
    .build()

fun backItem(config: GuiElementConfig, resolver: ItemResolver, onClick: (Player) -> Unit): Item = Item.builder()
    .setItemProvider(config.render(resolver))
    .addClickHandler { click -> onClick(click.player()) }
    .build()
