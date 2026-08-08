package me.wuzzyxy.dynamicmarket.gui

import me.wuzzyxy.dynamicmarket.configs.GuiElementConfig
import me.wuzzyxy.dynamicmarket.items.MarketItem
import me.wuzzyxy.dynamicmarket.items.prettyItemName
import xyz.xenondevs.invui.gui.Markers
import xyz.xenondevs.invui.gui.PagedGui
import xyz.xenondevs.invui.item.BoundItem
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.window.Window
import org.bukkit.entity.Player

/***
 * Item grid for one category. Layout comes from menus.yml (category.layout) — paged rather
 * than fixed slots, so a category that outgrows its window can't silently lose items off the
 * bottom the way the old DeluxeMenus config did (see temp/menu example/market*.yml).
 *
 * Page nav only renders when there's actually somewhere to go: on a single-page category (the
 * common case today) both buttons stay as plain filler instead of a dead button that clicks
 * for nothing.
 *
 * Registers with [GuiManager.registry] while open, so a trade made on this category's items
 * (from anyone's item screen, including this player's own) refreshes the prices shown here too.
 */
class CategoryGui(private val ctx: GuiManager, private val category: String) {

    private val menu get() = ctx.menuConfig.category
    private val shared get() = ctx.menuConfig.shared

    fun open(player: Player) {
        val itemConfig = menu.item
        val categoryItems = if (itemConfig != null) resolvableItems() else emptyList()
        val content: List<Item> = if (itemConfig != null) categoryItems.map { itemButton(itemConfig, it) } else emptyList()

        val prev = BoundItem.pagedBuilder()
            .setItemProvider { _, gui -> navIcon(gui.page > 0, shared.prevPage) }
            .addClickHandler { _, gui, _ -> if (gui.page > 0) gui.page-- }
            .build()

        val next = BoundItem.pagedBuilder()
            .setItemProvider { _, gui -> navIcon(gui.page < gui.pageCount - 1, shared.nextPage) }
            .addClickHandler { _, gui, _ -> if (gui.page < gui.pageCount - 1) gui.page++ }
            .build()

        val displayCategory = category.replaceFirstChar(Char::uppercaseChar)
        val label = menu.label?.let {
            Item.simple(it.render(ctx.resolver, mapOf("category" to displayCategory, "count" to content.size.toString())))
        }

        val gui: PagedGui<Item> = PagedGui.itemsBuilder()
            .setStructure(*menu.layout.toTypedArray())
            .addIngredient('#', fillerItem(shared.filler, ctx.resolver))
            .addIngredient('i', label ?: fillerItem(shared.filler, ctx.resolver))
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .addIngredient('b', navOrFiller(shared.back) { config -> backItem(config, ctx.resolver) { viewer -> ctx.openMainMenu(viewer) } })
            .addIngredient('<', prev)
            .addIngredient('>', next)
            .addIngredient('c', navOrFiller(shared.close) { config -> closeItem(config, ctx.resolver) })
            .setContent(content)
            .build()

        val visibleContentSlots = menu.layout.sumOf { line -> line.count { it == 'x' } }
        if (content.size > visibleContentSlots && (shared.prevPage == null || shared.nextPage == null)) {
            ctx.plugin.logger.warning(
                "Category '$category' has ${content.size} items but only $visibleContentSlots fit on one page, and " +
                    "page navigation is disabled in menus.yml — the rest are unreachable from the GUI"
            )
        }

        if (content.isNotEmpty()) {
            ctx.registry.register(player.uniqueId, categoryItems.map(MarketItem::name).toSet(), content)
        }

        Window.builder()
            .setTitle(menu.title.withPlaceholders(mapOf("category" to displayCategory)))
            .setUpperGui(gui)
            .addCloseHandler { ctx.registry.unregister(player.uniqueId) }
            .open(player)
    }

    // See GuiManager.resolveIconOrWarn — an unresolvable item can't be shown at all.
    private fun resolvableItems(): List<MarketItem> = ctx.manager.workingItems
        .filter { it.category == category }
        .sortedBy(MarketItem::name)
        .filter { ctx.resolveIconOrWarn(it.name) != null }

    private fun navIcon(applicable: Boolean, config: GuiElementConfig?) =
        if (applicable && config != null) config.render(ctx.resolver) else shared.filler.render(ctx.resolver).hideTooltip(true)

    private fun navOrFiller(config: GuiElementConfig?, build: (GuiElementConfig) -> Item): Item =
        config?.let(build) ?: fillerItem(shared.filler, ctx.resolver)

    private fun itemButton(config: GuiElementConfig, item: MarketItem): Item = Item.builder()
        .setItemProvider(itemProvider(config, item))
        .addClickHandler { click -> ctx.openItem(click.player(), item) }
        .build()

    private fun itemProvider(config: GuiElementConfig, item: MarketItem): ItemBuilder {
        val icon = ctx.resolveIconOrWarn(item.name)
        val buyPrice = ctx.manager.priceHandler.getBuyPrice(item, 1)
        val sellPrice = ctx.manager.priceHandler.getSellPrice(item, 1)
        val placeholders = mapOf(
            "item" to prettyItemName(item.name),
            "buy" to ctx.economy.format(buyPrice),
            "sell" to ctx.economy.format(sellPrice),
        )
        return config.render(ctx.resolver, placeholders, iconOverride = icon)
    }
}
