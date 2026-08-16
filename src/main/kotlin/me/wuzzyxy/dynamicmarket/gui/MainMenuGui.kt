package me.wuzzyxy.dynamicmarket.gui

import me.wuzzyxy.dynamicmarket.configs.GuiElementConfig
import me.wuzzyxy.dynamicmarket.configs.MainMenuConfig
import me.wuzzyxy.dynamicmarket.items.MarketItem
import me.wuzzyxy.dynamicmarket.items.prettyItemName
import me.wuzzyxy.dynamicmarket.market.MarketReport
import xyz.xenondevs.invui.gui.Markers
import xyz.xenondevs.invui.gui.PagedGui
import xyz.xenondevs.invui.gui.SlotElement
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.window.Window
import java.util.Locale
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

private val RANKED_MOVER = Regex("%top_(\\w+)_line(?:_(\\d+))?%")

/*** How far down the rankings a template reaches, so we only look up the movers it can actually show. */
private fun GuiElementConfig.deepestRank(token: String): Int {
    var deepest = 0
    for (text in lore + name) {
        for (match in RANKED_MOVER.findAll(text)) {
            if (match.groupValues[1] != token) continue
            deepest = maxOf(deepest, match.groupValues[2].toIntOrNull() ?: 1)
        }
    }
    return deepest
}

/***
 * Home screen. Its whole shape is menus.yml (main.layout) — where the category buttons sit,
 * where the gainer and faller strips sit, which slots hold their labels, page nav and close.
 * Nothing here resizes the window: a market with nothing moving today leaves the configured
 * empty slot in the 'u'/'d' positions instead of quietly dropping a row, so the screen an admin
 * lays out is the screen players get.
 *
 * Categories page over the 'g' slots the same way items page on the category screen, so adding
 * one more category can't push another off the bottom. Each button is main.category as that
 * category's own `button` override leaves it, with %description% carrying whatever the admin
 * wrote about the category in menus.yml.
 */
class MainMenuGui(private val ctx: GuiManager) {

    private val liveItems = mutableListOf<Item>()
    private val menu: MainMenuConfig get() = ctx.menuConfig.main
    private val shared get() = ctx.menuConfig.shared

    fun open(player: Player) {
        val layout = menu.layout
        val report = ctx.manager.report
        val blank = emptySlotItem(menu.empty, shared, ctx.resolver)
        val hours = mapOf("hours" to report.windowHours.toString())

        // menus.yml order first, then alphabetical — both for the categories nobody ordered and
        // as the tiebreak, so two categories sharing an order still land somewhere predictable.
        val categoryButtons = ctx.manager.workingItems
            .filter { ctx.resolveIconOrWarn(it.name) != null }
            .groupBy(MarketItem::category)
            .entries
            .sortedWith(compareBy({ ctx.menuConfig.category(it.key).order }, { it.key }))
            .mapNotNull { (category, items) -> categoryItem(category, items) }

        val gainerSlots = layoutSlots(layout, 'u')
        val fallerSlots = layoutSlots(layout, 'd')
        val gainers = menu.mover?.let { liveMovers(report, "up", gainerSlots.size) }.orEmpty()
        val fallers = menu.mover?.let { liveMovers(report, "down", fallerSlots.size) }.orEmpty()

        val gui = PagedGui.itemsBuilder()
            .setStructure(*layout.toTypedArray())
            .addIngredient('#', fillerItem(shared.filler, ctx.resolver))
            .addIngredient('g', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            // 'u' and 'd' are placeholders: each one needs a *different* mover, which a single
            // ingredient binding can't express, so they're overwritten slot by slot below.
            .addIngredient('u', blank)
            .addIngredient('d', blank)
            .addIngredient('s', buttonOrFiller(menu.sellEverything, shared.filler, ctx.resolver) { track(sellEverythingItem(it)) })
            .addIngredient('l', buttonOrFiller(menu.gainersLabel, shared.filler, ctx.resolver) { Item.simple(it.render(ctx.resolver, hours)) })
            .addIngredient('L', buttonOrFiller(menu.fallersLabel, shared.filler, ctx.resolver) { Item.simple(it.render(ctx.resolver, hours)) })
            .addIngredient('<', prevPageItem(shared.prevPage, shared.filler, ctx.resolver))
            .addIngredient('>', nextPageItem(shared.nextPage, shared.filler, ctx.resolver))
            .addIngredient('c', buttonOrFiller(shared.close, shared.filler, ctx.resolver) { closeItem(it, ctx.resolver) })
            .setBackground(emptySlotProvider(menu.empty, shared, ctx.resolver))
            .setContent(categoryButtons)
            .build()

        menu.mover?.let { moverConfig ->
            for ((slot, gainer) in gainerSlots.zip(gainers)) {
                gui.setSlotElement(slot, SlotElement.Item(moverItem(moverConfig, gainer, rising = true)))
            }
            for ((slot, faller) in fallerSlots.zip(fallers)) {
                gui.setSlotElement(slot, SlotElement.Item(moverItem(moverConfig, faller, rising = false)))
            }
        }

        warnIfUnreachable(categoryButtons.size, layoutSlots(layout, 'g').size)

        Window.builder()
            .setTitle(menu.title)
            .setUpperGui(gui)
            .open(player)
    }

    private fun warnIfUnreachable(categories: Int, slotsPerPage: Int) {
        if (slotsPerPage == 0 && categories > 0) {
            ctx.warnOnce(
                "main-no-category-slots",
                "menus.yml main.layout has no 'g' slots, so none of the $categories categories are reachable from the home screen"
            )
        }
        if (categories > slotsPerPage && slotsPerPage > 0 && (shared.prevPage == null || shared.nextPage == null)) {
            ctx.warnOnce(
                "main-no-page-nav",
                "The home screen fits $slotsPerPage of $categories categories per page and page navigation is disabled " +
                    "in menus.yml — the rest are unreachable"
            )
        }
    }

    private fun track(item: Item): Item {
        liveItems += item
        return item
    }

    private fun liveMovers(report: MarketReport, direction: String, limit: Int): List<Pair<MarketItem, Double>> {
        if (limit == 0) return emptyList()
        return report.movers(direction, MarketReport.ALL, limit).mapNotNull { (name, change) ->
            val marketItem = ctx.manager.getWorkingItem(name) ?: return@mapNotNull null
            if (ctx.resolveIconOrWarn(name) == null) return@mapNotNull null
            marketItem to change
        }
    }

    /*** Null where this category has no button — main.category deleted, or one override switching it off. */
    private fun categoryItem(category: String, items: List<MarketItem>): Item? {
        val screen = ctx.menuConfig.category(category)
        val button = screen.button ?: return null
        val description = screen.description
        val eventLines = ctx.manager.eventManager.activeEventLines(category)

        val placeholders = buildMap {
            put("category", category.replaceFirstChar(Char::uppercaseChar))
            put("count", items.size.toString())
            put("description", description.joinToString(" "))
            put("hours", ctx.manager.report.windowHours.toString())
            // A still-visible random event takes over this category's movers block rather
            // than sharing it — otherwise both would render side by side in the same group.
            if (eventLines.isEmpty()) {
                putMoverLines(button, category, "gainer", "up", rising = true)
                putMoverLines(button, category, "faller", "down", rising = false)
            }
        }

        return Item.builder()
            .setItemProvider(
                button.render(
                    ctx.resolver,
                    placeholders,
                    iconOverride = categoryIcon(button, category, items),
                    blocks = mapOf("description" to description, "event_line" to eventLines),
                )
            )
            .addClickHandler { click -> ctx.openCategory(click.player(), category) }
            .build()
    }

    /***
     * %top_gainer_line% is the category's biggest riser, %top_gainer_line_2% the one after it, and
     * so on for as deep as the template asks — an unnumbered token is rank 1. Ranks the category
     * doesn't have get no entry here at all, which is what makes their lore lines disappear.
     */
    private fun MutableMap<String, String>.putMoverLines(
        button: GuiElementConfig,
        category: String,
        token: String,
        direction: String,
        rising: Boolean,
    ) {
        val deepest = button.deepestRank(token)
        val movers = ctx.manager.report.movers(direction, category, deepest)
        movers.forEachIndexed { index, mover ->
            val line = moverLine(mover, rising)
            put("top_${token}_line_${index + 1}", line)
            if (index == 0) put("top_${token}_line", line)
        }
    }

    /*** A category has no traded item of its own, so its button borrows one unless menus.yml names an icon for it. */
    private fun categoryIcon(button: GuiElementConfig, category: String, items: List<MarketItem>): ItemStack? {
        if (button.icon != null) {
            val icon = ctx.resolver.resolve(button.icon)
            if (icon != null) return icon
            ctx.warnOnce(
                "category-icon-$category",
                "menus.yml icon '${button.icon}' for category '$category' isn't a real item or CraftEngine id — " +
                    "using one of the category's own items instead"
            )
        }
        return ctx.resolveIconOrWarn(items.minBy(MarketItem::name).name)
    }

    private fun moverLine(mover: Pair<String, Double>, rising: Boolean): String {
        val arrow = if (rising) "<green>▲" else "<red>▼"
        val color = if (rising) "<green>" else "<red>"
        val sign = if (mover.second >= 0) "+" else ""
        return "$arrow ${prettyItemName(mover.first)} $color$sign${pct(mover.second)}%"
    }

    private fun moverItem(config: GuiElementConfig, mover: Pair<MarketItem, Double>, rising: Boolean): Item {
        val (marketItem, change) = mover
        val icon = ctx.resolveIconOrWarn(marketItem.name)
        val placeholders = mapOf(
            "item" to prettyItemName(marketItem.name),
            "arrow" to if (rising) "<green>▲" else "<red>▼",
            "sign" to if (change >= 0) "+" else "",
            "change" to pct(change),
            "category" to marketItem.category,
        )

        return Item.builder()
            .setItemProvider(config.render(ctx.resolver, placeholders, iconOverride = icon))
            .addClickHandler { click -> ctx.openItem(click.player(), marketItem) }
            .build()
    }

    private fun sellEverythingItem(config: GuiElementConfig): Item = Item.builder()
        .setItemProvider(config.render(ctx.resolver))
        .addClickHandler { click ->
            val viewer = click.player()
            val result = ctx.trades.sellEverything(viewer)
            viewer.announceSweep(result, ctx.economy, ctx.shopConfig)
            liveItems.forEach(Item::notifyWindows)
            ctx.registry.refresh(result.lines.map { it.item.name }, exclude = viewer.uniqueId)
        }
        .build()

    private fun pct(change: Double): String = String.format(Locale.ROOT, "%.1f", change)
}
