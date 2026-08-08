package me.wuzzyxy.dynamicmarket.gui

import me.wuzzyxy.dynamicmarket.configs.GuiElementConfig
import me.wuzzyxy.dynamicmarket.items.MarketItem
import me.wuzzyxy.dynamicmarket.items.countMatching
import me.wuzzyxy.dynamicmarket.items.prettyItemName
import me.wuzzyxy.dynamicmarket.market.TradeResult
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.SlotElement
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.window.Window
import org.bukkit.entity.Player

/*** Every slot in [layout] (row-major) whose token starts with [char] — used for the repeated-but-distinct 'q' quantity slots a single addIngredient binding can't represent. */
private fun layoutSlots(layout: List<String>, char: Char): List<Int> =
    layout.flatMapIndexed { row, line ->
        line.split(' ').filter(String::isNotEmpty).mapIndexedNotNull { col, token ->
            if (token.firstOrNull() == char) row * 9 + col else null
        }
    }

/***
 * Buy/sell screen for one item. Layout comes from menus.yml (item.layout): one button per
 * configured quantity tier (left click buys, right click sells — same dual-purpose icon the
 * old DeluxeMenus menu used), plus a Sell All that always reflects exactly how many the player
 * is actually carrying right now. Every named element (display/quantity/sell-all/back/close)
 * is optional — see MenuConfig — and just isn't placed when its config section is missing.
 *
 * Registers with [GuiManager.registry] while open, so a trade made *here* also refreshes any
 * other player who has this same item's screen open right now — not just this window.
 */
class ItemTradeGui(private val ctx: GuiManager, private val item: MarketItem) {

    private val liveItems = mutableListOf<Item>()
    private val menu get() = ctx.menuConfig.item
    private val shared get() = ctx.menuConfig.shared

    fun open(player: Player) {
        val displaySlot = menu.display?.let { track(displayItem(it)) } ?: fillerItem(shared.filler, ctx.resolver)
        val sellAllSlot = menu.sellAll?.let { track(sellAllItem(it)) } ?: fillerItem(shared.filler, ctx.resolver)

        val gui = Gui.builder()
            .setStructure(*menu.layout.toTypedArray())
            .addIngredient('#', fillerItem(shared.filler, ctx.resolver))
            // 'q' is a placeholder here — each occurrence needs a *different* quantity, which
            // a single addIngredient binding can't express, so every 'q' slot gets overwritten
            // individually below once the Gui exists.
            .addIngredient('q', fillerItem(shared.filler, ctx.resolver))
            .addIngredient('d', displaySlot)
            .addIngredient('a', sellAllSlot)
            .addIngredient('b', navOrFiller(shared.back) { config -> backItem(config, ctx.resolver) { viewer -> ctx.openCategory(viewer, item.category) } })
            .addIngredient('c', navOrFiller(shared.close) { config -> closeItem(config, ctx.resolver) })
            .build()

        val quantityConfig = menu.quantity
        if (quantityConfig != null) {
            val quantitySlots = layoutSlots(menu.layout, 'q')
            val quantities = ctx.shopConfig.QUANTITIES
            if (quantities.size != quantitySlots.size) {
                ctx.plugin.logger.warning(
                    "shop.quantities has ${quantities.size} tier(s) but item.layout has ${quantitySlots.size} 'q' slot(s) — " +
                        "showing ${minOf(quantities.size, quantitySlots.size)}"
                )
            }
            for ((slot, quantity) in quantitySlots.zip(quantities)) {
                gui.setSlotElement(slot, SlotElement.Item(track(quantityItem(quantityConfig, quantity))))
            }
        }

        ctx.registry.register(player.uniqueId, setOf(item.name), liveItems)

        Window.builder()
            .setTitle(menu.title.withPlaceholders(mapOf("item" to prettyItemName(item.name))))
            .setUpperGui(gui)
            .addCloseHandler { ctx.registry.unregister(player.uniqueId) }
            .open(player)
    }

    private fun navOrFiller(config: GuiElementConfig?, build: (GuiElementConfig) -> Item): Item =
        config?.let(build) ?: fillerItem(shared.filler, ctx.resolver)

    private fun track(guiItem: Item): Item {
        liveItems += guiItem
        return guiItem
    }

    private fun refresh() {
        liveItems.forEach(Item::notifyWindows)
    }

    private fun displayItem(config: GuiElementConfig): Item = Item.builder()
        .setItemProvider { player -> displayProvider(config, player) }
        .build()

    private fun displayProvider(config: GuiElementConfig, player: Player): ItemBuilder {
        val icon = ctx.resolveIconOrWarn(item.name)
        val owned = player.inventory.countMatching(ctx.resolver, item.name)
        val unitPrice = ctx.manager.priceHandler.getUnitPrice(item)
        val placeholders = mapOf(
            "item" to prettyItemName(item.name),
            "category" to item.category,
            "unit" to ctx.economy.format(unitPrice),
            "owned" to owned.toString(),
        )
        return config.render(ctx.resolver, placeholders, iconOverride = icon)
    }

    private fun quantityItem(config: GuiElementConfig, quantity: Int): Item = Item.builder()
        .setItemProvider { player -> quantityProvider(config, player, quantity) }
        .addClickHandler { click ->
            val viewer = click.player()
            val clickType = click.clickType()
            val result = when {
                clickType.isLeftClick -> ctx.trades.buy(viewer, item, quantity)
                clickType.isRightClick -> ctx.trades.sell(viewer, item, quantity)
                else -> null
            }
            if (result != null) {
                viewer.announce(result, ctx.economy, ctx.shopConfig)
                refresh()
                if (result !is TradeResult.Denied) ctx.registry.refresh(item.name, exclude = viewer.uniqueId)
            }
        }
        .build()

    private fun quantityProvider(config: GuiElementConfig, player: Player, quantity: Int): ItemBuilder {
        val icon = ctx.resolveIconOrWarn(item.name, quantity)
        val buyPrice = ctx.manager.priceHandler.getBuyPrice(item, quantity)
        val sellPrice = ctx.manager.priceHandler.getSellPrice(item, quantity)
        val canAfford = ctx.economy.has(player, buyPrice)
        val canSell = player.inventory.countMatching(ctx.resolver, item.name) >= quantity

        val placeholders = mapOf(
            "amount" to quantity.toString(),
            "item" to prettyItemName(item.name),
            "buy" to ctx.economy.format(buyPrice),
            "sell" to ctx.economy.format(sellPrice),
            "afford_color" to if (canAfford) "<green>" else "<red>",
            "sell_color" to if (canSell) "<green>" else "<red>",
        )
        return config.render(ctx.resolver, placeholders, iconOverride = icon)
    }

    private fun sellAllItem(config: GuiElementConfig): Item = Item.builder()
        .setItemProvider { player -> sellAllProvider(config, player) }
        .addClickHandler { click ->
            val viewer = click.player()
            val result = ctx.trades.sellAll(viewer, item)
            viewer.announce(result, ctx.economy, ctx.shopConfig)
            refresh()
            if (result !is TradeResult.Denied) ctx.registry.refresh(item.name, exclude = viewer.uniqueId)
        }
        .build()

    private fun sellAllProvider(config: GuiElementConfig, player: Player): ItemBuilder {
        val owned = player.inventory.countMatching(ctx.resolver, item.name)
        val price = if (owned > 0) ctx.manager.priceHandler.getSellPrice(item, owned) else 0.0
        val placeholders = mapOf(
            "item" to prettyItemName(item.name),
            "owned" to owned.toString(),
            "price" to ctx.economy.format(price),
            "sell_all_hint" to if (owned > 0) "<yellow>Click to sell all" else "<dark_gray>Nothing to sell",
        )
        return config.render(ctx.resolver, placeholders)
    }
}
