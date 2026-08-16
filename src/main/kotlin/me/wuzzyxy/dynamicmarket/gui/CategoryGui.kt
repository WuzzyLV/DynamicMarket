package me.wuzzyxy.dynamicmarket.gui

import me.wuzzyxy.dynamicmarket.configs.GuiElementConfig
import me.wuzzyxy.dynamicmarket.items.MarketItem
import me.wuzzyxy.dynamicmarket.items.prettyItemName
import xyz.xenondevs.invui.gui.Markers
import xyz.xenondevs.invui.gui.PagedGui
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.window.Window
import org.bukkit.entity.Player

/***
 * Item grid for one category. Layout, title and templates come from menus.yml, taken through
 * MenuConfig.category() so a `category.overrides.<name>` block can give this one category its own
 * grid or wording without restating the parts it shares. Paged rather than fixed slots, so a
 * category that outgrows its window can't silently lose items off the bottom the way the old
 * DeluxeMenus config did (see temp/menu example/market*.yml).
 *
 * Page nav only renders when there's actually somewhere to go, and an 'x' slot with no item left
 * to show renders the configured empty element rather than a hole in the window.
 *
 * Registers with [GuiManager.registry] while open, so a trade made on this category's items
 * (from anyone's item screen, including this player's own) refreshes the prices shown here too.
 */
class CategoryGui(private val ctx: GuiManager, private val category: String) {

    private val shared get() = ctx.menuConfig.shared

    fun open(player: Player) {
        val screen = ctx.menuConfig.category(category)
        val itemConfig = screen.item
        val categoryItems = if (itemConfig != null) resolvableItems() else emptyList()
        val content = if (itemConfig == null) emptyList() else categoryItems.map { itemButton(itemConfig, it) }

        val displayCategory = category.replaceFirstChar(Char::uppercaseChar)
        val labelPlaceholders = mapOf(
            "category" to displayCategory,
            "count" to content.size.toString(),
            "description" to screen.description.joinToString(" "),
        )
        val labelBlocks = mapOf("description" to screen.description)

        val gui: PagedGui<Item> = PagedGui.itemsBuilder()
            .setStructure(*screen.layout.toTypedArray())
            .addIngredient('#', fillerItem(shared.filler, ctx.resolver))
            .addIngredient('i', buttonOrFiller(screen.label, shared.filler, ctx.resolver) { Item.simple(it.render(ctx.resolver, labelPlaceholders, blocks = labelBlocks)) })
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .addIngredient('b', buttonOrFiller(shared.back, shared.filler, ctx.resolver) { backItem(it, ctx.resolver) { viewer -> ctx.openMainMenu(viewer) } })
            .addIngredient('<', prevPageItem(shared.prevPage, shared.filler, ctx.resolver))
            .addIngredient('>', nextPageItem(shared.nextPage, shared.filler, ctx.resolver))
            .addIngredient('c', buttonOrFiller(shared.close, shared.filler, ctx.resolver) { closeItem(it, ctx.resolver) })
            .setBackground(emptySlotProvider(screen.empty, shared, ctx.resolver))
            .setContent(content)
            .build()

        val slotsPerPage = layoutSlots(screen.layout, 'x').size
        if (content.size > slotsPerPage && (shared.prevPage == null || shared.nextPage == null)) {
            ctx.warnOnce(
                "category-no-page-nav-$category",
                "Category '$category' has ${content.size} items but only $slotsPerPage fit on one page, and " +
                    "page navigation is disabled in menus.yml — the rest are unreachable from the GUI"
            )
        }

        if (content.isNotEmpty()) {
            ctx.registry.register(player.uniqueId, categoryItems.map(MarketItem::name).toSet(), content)
        }

        Window.builder()
            .setTitle(screen.title.withPlaceholders(mapOf("category" to displayCategory, "description" to screen.description.joinToString(" "))))
            .setUpperGui(gui)
            .addCloseHandler { ctx.registry.unregister(player.uniqueId) }
            .open(player)
    }

    // See GuiManager.resolveIconOrWarn — an unresolvable item can't be shown at all.
    private fun resolvableItems(): List<MarketItem> = ctx.manager.workingItems
        .filter { it.category == category }
        .sortedBy(MarketItem::name)
        .filter { ctx.resolveIconOrWarn(it.name) != null }

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
