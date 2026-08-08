package me.wuzzyxy.dynamicmarket.configs

import me.wuzzyxy.dynamicmarket.DynamicMarket
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/***
 * icon is a Material name or a CraftEngine id ("namespace:value") — same rule as items.yml.
 * Ignored where the element is bound to a traded item (the item's own icon wins instead;
 * see GuiManager.resolveIconOrWarn callers) — same precedent as the chip icon in the bet GUI
 * this was modelled on. name/lore are MiniMessage, run through %placeholder% substitution first.
 */
data class GuiElementConfig(val icon: String, val name: String, val lore: List<String>)

/***
 * Every named element below the layout/title strings is nullable and stays that way: presence
 * in menus.yml *is* the on/off switch. Deleting a whole section (not just blanking a field)
 * removes that button/slot from the menu entirely — nothing falls back to a hardcoded default
 * for a section the admin explicitly removed. Only `filler` is exempt, since a Gui with no
 * background item isn't "filler disabled", it's an inventory hole a player could drop items into.
 */
data class SharedMenuElements(
    val filler: GuiElementConfig,
    val back: GuiElementConfig?,
    val close: GuiElementConfig?,
    val prevPage: GuiElementConfig?,
    val nextPage: GuiElementConfig?,
)

/***
 * header/category_row/gainers_row/fallers_row/footer are single-row InvUI structures (9
 * space-separated characters). Unlike category/item below, the home screen's total row count
 * is still computed at runtime from how many categories and movers actually exist — that's
 * the whole point of not reserving empty rows for a quiet market. What's configurable here is
 * everything *within* a row: which column holds what, and every icon/name/lore template.
 */
data class MainMenuConfig(
    val title: String,
    val header: String,
    val categoryRow: String,
    val gainersRow: String,
    val fallersRow: String,
    val footer: String,
    val sellEverything: GuiElementConfig?,
    val gainersLabel: GuiElementConfig?,
    val fallersLabel: GuiElementConfig?,
    val category: GuiElementConfig?,
    val mover: GuiElementConfig?,
)

/*** layout is a full InvUI structure (one string per row) — fixed size, same convention as InvUI's own docs and the bet GUI's `layout`. */
data class CategoryMenuConfig(
    val title: String,
    val layout: List<String>,
    val label: GuiElementConfig?,
    val item: GuiElementConfig?,
)

data class ItemMenuConfig(
    val title: String,
    val layout: List<String>,
    val display: GuiElementConfig?,
    val quantity: GuiElementConfig?,
    val sellAll: GuiElementConfig?,
)

/***
 * menus.yml — layout and every icon/name/lore template for the /market GUI. Loaded the same
 * way as everything else under configs/: never overwrites an existing file, so removing a
 * section on disk sticks. Layout/title strings still fall back to a code default if missing
 * (they're structure, not a feature to switch off) — see the two loader helpers below.
 */
class MenuConfig(plugin: DynamicMarket) {

    private val config = loadFile(plugin)

    val shared = SharedMenuElements(
        filler = config.element("shared.filler", "GRAY_STAINED_GLASS_PANE", " ", emptyList()),
        back = config.elementOrNull("shared.back"),
        close = config.elementOrNull("shared.close"),
        prevPage = config.elementOrNull("shared.prev_page"),
        nextPage = config.elementOrNull("shared.next_page"),
    )

    val main = MainMenuConfig(
        title = config.str("main.title", "<dark_gray><bold>Market"),
        header = config.str("main.header", "# # # # # # # # s"),
        categoryRow = config.str("main.category_row", "# g g g g g g g #"),
        gainersRow = config.str("main.gainers_row", "l m m m m m m m m"),
        fallersRow = config.str("main.fallers_row", "l m m m m m m m m"),
        footer = config.str("main.footer", "# # # # # # # # c"),
        sellEverything = config.elementOrNull("main.elements.sell_everything"),
        gainersLabel = config.elementOrNull("main.elements.gainers_label"),
        fallersLabel = config.elementOrNull("main.elements.fallers_label"),
        category = config.elementOrNull("main.category"),
        mover = config.elementOrNull("main.mover"),
    )

    val category = CategoryMenuConfig(
        title = config.str("category.title", "<dark_gray>Market <gray>» <white>%category%"),
        layout = config.strList(
            "category.layout",
            listOf(
                "# # # # i # # # #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "b < # # # # # > c",
            ),
        ),
        label = config.elementOrNull("category.label"),
        item = config.elementOrNull("category.item"),
    )

    val item = ItemMenuConfig(
        title = config.str("item.title", "<dark_gray>Market <gray>» <white>%item%"),
        layout = config.strList(
            "item.layout",
            listOf(
                "# # # # d # # # #",
                "# q q q q q q q #",
                "b # a # # # # # c",
            ),
        ),
        display = config.elementOrNull("item.display"),
        quantity = config.elementOrNull("item.quantity"),
        sellAll = config.elementOrNull("item.sell_all"),
    )
}

private fun loadFile(plugin: DynamicMarket): YamlConfiguration {
    val file = File(plugin.dataFolder, "menus.yml")
    if (!file.exists()) {
        file.parentFile.mkdirs()
        plugin.saveResource("menus.yml", false)
    }
    return YamlConfiguration.loadConfiguration(file)
}

private fun YamlConfiguration.element(path: String, defaultIcon: String, defaultName: String, defaultLore: List<String>): GuiElementConfig {
    val section = getConfigurationSection(path)
    return GuiElementConfig(
        icon = section?.getString("icon") ?: defaultIcon,
        name = section?.getString("name") ?: defaultName,
        lore = if (section?.isSet("lore") == true) section.getStringList("lore") else defaultLore,
    )
}

/*** Null when the section is absent — the admin removed it. Present-but-sparse fields still get a safe fallback. */
private fun YamlConfiguration.elementOrNull(path: String): GuiElementConfig? {
    val section = getConfigurationSection(path) ?: return null
    return GuiElementConfig(
        icon = section.getString("icon") ?: "BARRIER",
        name = section.getString("name") ?: " ",
        lore = section.getStringList("lore"),
    )
}

private fun YamlConfiguration.str(path: String, default: String): String = getString(path, default) ?: default

private fun YamlConfiguration.strList(path: String, default: List<String>): List<String> =
    if (isSet(path)) getStringList(path) else default
