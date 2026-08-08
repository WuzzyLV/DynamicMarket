package me.wuzzyxy.dynamicmarket.gui

import me.wuzzyxy.dynamicmarket.configs.GuiElementConfig
import me.wuzzyxy.dynamicmarket.items.ItemResolver
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemBuilder

/*** %token% substitution, run before MiniMessage sees the string — ours first, same order the reference bet GUI uses. */
internal fun String.withPlaceholders(placeholders: Map<String, String>): String =
    placeholders.entries.fold(this) { acc, (k, v) -> acc.replace("%$k%", v) }

/*** A lore line that is *only* one placeholder and resolves empty is dropped rather than rendered blank. */
internal fun List<String>.renderLore(placeholders: Map<String, String>): List<String> = mapNotNull { line ->
    val trimmed = line.trim()
    val isSoleToken = trimmed.length > 2 && trimmed[0] == '%' && trimmed.last() == '%' && trimmed.indexOf('%', 1) == trimmed.length - 1
    if (isSoleToken) {
        val value = placeholders[trimmed.substring(1, trimmed.length - 1)]
        if (value.isNullOrEmpty()) null else line.replace(trimmed, value)
    } else {
        line.withPlaceholders(placeholders)
    }
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
): ItemBuilder {
    val stack = iconOverride ?: resolver.resolve(icon) ?: ItemStack(Material.BARRIER)
    val builder = ItemBuilder(stack).setName(name.withPlaceholders(placeholders))
    val loreLines = lore.renderLore(placeholders)
    if (loreLines.isNotEmpty()) builder.addLoreLines(*loreLines.toTypedArray())
    return builder
}

fun fillerItem(config: GuiElementConfig, resolver: ItemResolver): Item =
    Item.simple(config.render(resolver).hideTooltip(true))

fun closeItem(config: GuiElementConfig, resolver: ItemResolver): Item = Item.builder()
    .setItemProvider(config.render(resolver))
    .addClickHandler { click -> click.player().closeInventory() }
    .build()

fun backItem(config: GuiElementConfig, resolver: ItemResolver, onClick: (Player) -> Unit): Item = Item.builder()
    .setItemProvider(config.render(resolver))
    .addClickHandler { click -> onClick(click.player()) }
    .build()
