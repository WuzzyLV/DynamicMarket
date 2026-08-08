package me.wuzzyxy.dynamicmarket.gui

import me.wuzzyxy.dynamicmarket.configs.GuiElementConfig
import me.wuzzyxy.dynamicmarket.configs.MainMenuConfig
import me.wuzzyxy.dynamicmarket.items.MarketItem
import me.wuzzyxy.dynamicmarket.items.prettyItemName
import me.wuzzyxy.dynamicmarket.market.MarketReport
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.SlotElement
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.window.Window
import java.util.Locale
import org.bukkit.entity.Player

/*** Splits a row template ("# g g g g g g g #") into its 9 column characters, InvUI's own structure convention. */
private fun parseRow(template: String): List<Char> =
    template.split(' ').filter(String::isNotEmpty).map { it[0] }

/***
 * Home screen: category buttons plus, unlike the old DeluxeMenus menu this replaces, a
 * "Recent Market Movers" section sized off how many risers/fallers actually exist. Row
 * *templates* (what each column of a row holds) come from menus.yml; the *number* of category
 * and mover rows is still computed here from live data — a quiet market adds zero mover rows
 * instead of reserving empty ones, which is the one thing this screen deliberately doesn't
 * hand over to config. Every named element (category/mover/sell-everything/labels) is optional
 * — see MenuConfig — and simply isn't placed when its config section is missing.
 */
class MainMenuGui(private val ctx: GuiManager) {

    private val liveItems = mutableListOf<Item>()
    private val menu: MainMenuConfig get() = ctx.menuConfig.main
    private val shared get() = ctx.menuConfig.shared

    fun open(player: Player) {
        val shopConfig = ctx.shopConfig
        val report = ctx.manager.report

        val categoryConfig = menu.category
        val moverConfig = menu.mover

        val byCategory: Map<String, List<MarketItem>> = if (categoryConfig == null) {
            emptyMap()
        } else {
            ctx.manager.workingItems
                .filter { ctx.resolveIconOrWarn(it.name) != null }
                .groupBy(MarketItem::category)
        }
        val categories = byCategory.keys.sorted()

        val gainers = if (moverConfig == null) emptyList() else liveMovers(report, "up", shopConfig.MOVERS_SHOWN)
        val fallers = if (moverConfig == null) emptyList() else liveMovers(report, "down", shopConfig.MOVERS_SHOWN)

        val categoryRowTemplate = parseRow(menu.categoryRow)
        val gainersRowTemplate = parseRow(menu.gainersRow)
        val fallersRowTemplate = parseRow(menu.fallersRow)

        val categorySlotsPerRow = categoryRowTemplate.count { it == 'g' }.coerceAtLeast(1)
        val categoryRows = if (categories.isEmpty()) 0 else (categories.size - 1) / categorySlotsPerRow + 1
        val moverRowCount = (if (gainers.isNotEmpty()) 1 else 0) + (if (fallers.isNotEmpty()) 1 else 0)

        var rows = 1 + categoryRows + moverRowCount + 1
        if (rows > 6) {
            ctx.plugin.logger.warning(
                "Market home screen doesn't fit in 6 rows ($categoryRows category rows, " +
                    "$moverRowCount mover rows) — truncating"
            )
            rows = 6
        }

        val gui = Gui.empty(9, rows)
        for (slot in 0 until gui.size) gui.setSlotElement(slot, SlotElement.Item(fillerItem(shared.filler, ctx.resolver)))

        placeStaticRow(gui, 0, parseRow(menu.header)) { char -> headerElement(char) }

        var row = 1
        if (categoryConfig != null) {
            for (chunk in categories.chunked(categorySlotsPerRow)) {
                if (row >= rows - 1) break
                placeContentRow(gui, row, categoryRowTemplate, 'g', chunk, { null }) { category ->
                    categoryItem(categoryConfig, category, byCategory.getValue(category))
                }
                row++
            }
        }

        if (moverConfig != null && gainers.isNotEmpty() && row < rows - 1) {
            val placeholders = mapOf("hours" to report.windowHours.toString())
            placeContentRow(gui, row, gainersRowTemplate, 'm', gainers, { char ->
                if (char == 'l') menu.gainersLabel?.let { Item.simple(it.render(ctx.resolver, placeholders)) } else null
            }) { mover -> moverItem(moverConfig, mover, rising = true) }
            row++
        }
        if (moverConfig != null && fallers.isNotEmpty() && row < rows - 1) {
            val placeholders = mapOf("hours" to report.windowHours.toString())
            placeContentRow(gui, row, fallersRowTemplate, 'm', fallers, { char ->
                if (char == 'l') menu.fallersLabel?.let { Item.simple(it.render(ctx.resolver, placeholders)) } else null
            }) { mover -> moverItem(moverConfig, mover, rising = false) }
            row++
        }

        placeStaticRow(gui, rows - 1, parseRow(menu.footer)) { char -> footerElement(char) }

        Window.builder()
            .setTitle(menu.title)
            .setUpperGui(gui)
            .open(player)
    }

    private fun headerElement(char: Char): Item? = when (char) {
        's' -> menu.sellEverything?.let { track(sellEverythingItem(it)) }
        else -> null
    }

    private fun footerElement(char: Char): Item? = when (char) {
        'c' -> shared.close?.let { closeItem(it, ctx.resolver) }
        else -> null
    }

    private fun placeStaticRow(gui: Gui, rowIndex: Int, template: List<Char>, resolveStatic: (Char) -> Item?) {
        for ((col, char) in template.withIndex()) {
            val item = resolveStatic(char) ?: continue
            gui.setSlotElement(rowIndex * 9 + col, SlotElement.Item(item))
        }
    }

    private fun <T> placeContentRow(
        gui: Gui,
        rowIndex: Int,
        template: List<Char>,
        contentChar: Char,
        content: List<T>,
        resolveStatic: (Char) -> Item?,
        contentItem: (T) -> Item,
    ) {
        var contentIndex = 0
        for ((col, char) in template.withIndex()) {
            val item = if (char == contentChar) {
                val value = content.getOrNull(contentIndex) ?: continue
                contentIndex++
                contentItem(value)
            } else {
                resolveStatic(char) ?: continue
            }
            gui.setSlotElement(rowIndex * 9 + col, SlotElement.Item(item))
        }
    }

    private fun track(item: Item): Item {
        liveItems += item
        return item
    }

    private fun liveMovers(report: MarketReport, direction: String, limit: Int): List<Pair<MarketItem, Double>> =
        report.movers(direction, MarketReport.ALL, limit).mapNotNull { (name, change) ->
            val marketItem = ctx.manager.getWorkingItem(name) ?: return@mapNotNull null
            if (ctx.resolveIconOrWarn(name) == null) return@mapNotNull null
            marketItem to change
        }

    private fun categoryItem(config: GuiElementConfig, category: String, items: List<MarketItem>): Item {
        val representative = items.minByOrNull(MarketItem::name) ?: items.first()
        val icon = ctx.resolveIconOrWarn(representative.name)
        val topGainer = ctx.manager.report.movers("up", category, 1).firstOrNull()
        val topFaller = ctx.manager.report.movers("down", category, 1).firstOrNull()

        val placeholders = mapOf(
            "category" to category.replaceFirstChar(Char::uppercaseChar),
            "count" to items.size.toString(),
            "top_gainer_line" to (topGainer?.let { moverLine(it, rising = true) } ?: ""),
            "top_faller_line" to (topFaller?.let { moverLine(it, rising = false) } ?: ""),
        )

        return Item.builder()
            .setItemProvider(config.render(ctx.resolver, placeholders, iconOverride = icon))
            .addClickHandler { click -> ctx.openCategory(click.player(), category) }
            .build()
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
