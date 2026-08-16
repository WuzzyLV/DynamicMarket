package me.wuzzyxy.dynamicmarket.configs

import me.wuzzyxy.dynamicmarket.DynamicMarket
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Logger

/***
 * icon is a Material name or a CraftEngine id ("namespace:value") — same rule as items.yml.
 * Ignored where the element is bound to a traded item (the item's own icon wins instead;
 * see GuiManager.resolveIconOrWarn callers) — same precedent as the chip icon in the bet GUI
 * this was modelled on. Null where the admin never named one, which is how a category button
 * knows to borrow an icon from the category's own items instead of showing a barrier.
 * name/lore are MiniMessage, run through %placeholder% substitution first.
 */
data class GuiElementConfig(val icon: String?, val name: String, val lore: List<String>)

/***
 * Every named element below the layout/title strings is nullable and stays that way: presence
 * in menus.yml *is* the on/off switch. Deleting a whole section (not just blanking a field)
 * removes that button/slot from the menu entirely — nothing falls back to a hardcoded default
 * for a section the admin explicitly removed. Only `filler` is exempt, since a Gui with no
 * background item isn't "filler disabled", it's an inventory hole a player could drop items into.
 *
 * `empty` is the default for content slots with nothing to put in them (no category on this page,
 * no mover today, no quantity tier for that slot). Absent means those slots fall through to filler.
 */
data class SharedMenuElements(
    val filler: GuiElementConfig,
    val empty: GuiElementConfig?,
    val back: GuiElementConfig?,
    val close: GuiElementConfig?,
    val prevPage: GuiElementConfig?,
    val nextPage: GuiElementConfig?,
)

data class MainMenuConfig(
    val title: String,
    val layout: List<String>,
    val empty: GuiElementConfig?,
    val sellEverything: GuiElementConfig?,
    val gainersLabel: GuiElementConfig?,
    val fallersLabel: GuiElementConfig?,
    val category: GuiElementConfig?,
    val mover: GuiElementConfig?,
)

/***
 * [button] is the one field here the *home* screen reads: it's this category's button over there,
 * `main.category` with whatever the category's own override changes. Null means the category gets
 * no button at all — either because `main.category` is gone or because this one override switched
 * it off. Its icon is null until an admin names one, at which point the button stops borrowing an
 * icon from the category's items.
 *
 * [description] is the category in the admin's own words, available as %description% wherever this
 * category is rendered. A lore line that is only %description% expands to as many lines as it has;
 * used inline or in a name it collapses to one.
 */
data class CategoryMenuConfig(
    val title: String,
    val layout: List<String>,
    val description: List<String>,
    val button: GuiElementConfig?,
    val empty: GuiElementConfig?,
    val label: GuiElementConfig?,
    val item: GuiElementConfig?,
)

data class ItemMenuConfig(
    val title: String,
    val layout: List<String>,
    val empty: GuiElementConfig?,
    val display: GuiElementConfig?,
    val quantity: GuiElementConfig?,
    val sellAll: GuiElementConfig?,
)

/***
 * menus.yml — layout and every icon/name/lore template for the /market GUI. Loaded the same
 * way as everything else under configs/: never overwrites an existing file, so removing a
 * section on disk sticks. Layout/title strings still fall back to a code default if missing
 * (they're structure, not a feature to switch off) — see the loader helpers below.
 *
 * All three screens are laid out the same way now: one `layout` list, one row per string, nine
 * slots per row. Nothing resizes itself at runtime — the grid an admin writes is the grid
 * players get, and content that outgrows it pages instead of falling off the bottom.
 */
class MenuConfig(plugin: DynamicMarket) {

    private val log = plugin.logger
    private val config = loadFile(plugin)

    val shared = SharedMenuElements(
        filler = config.element("shared.filler", "GRAY_STAINED_GLASS_PANE", " ", emptyList()),
        empty = config.elementOrNull("shared.empty"),
        back = config.elementOrNull("shared.back"),
        close = config.elementOrNull("shared.close"),
        prevPage = config.elementOrNull("shared.prev_page"),
        nextPage = config.elementOrNull("shared.next_page"),
    )

    val main = MainMenuConfig(
        title = config.str("main.title", "<dark_gray><bold>Market"),
        layout = config.layoutOrDefault("main.layout", DEFAULT_MAIN_LAYOUT, MAIN_SLOTS, log),
        empty = config.elementOrNull("main.empty"),
        sellEverything = config.elementOrNull("main.elements.sell_everything"),
        gainersLabel = config.elementOrNull("main.elements.gainers_label"),
        fallersLabel = config.elementOrNull("main.elements.fallers_label"),
        category = config.elementOrNull("main.category"),
        mover = config.elementOrNull("main.mover"),
    )

    private val categoryDefaults = CategoryMenuConfig(
        title = config.str("category.title", "<dark_gray>Market <gray>» <white>%category%"),
        layout = config.layoutOrDefault("category.layout", DEFAULT_CATEGORY_LAYOUT, CATEGORY_SLOTS, log),
        description = config.lines("category.description", emptyList()),
        button = main.category,
        empty = config.elementOrNull("category.empty"),
        label = config.elementOrNull("category.label"),
        item = config.elementOrNull("category.item"),
    )

    private val categoryOverrides = config.categoryOverrides(categoryDefaults, log)

    val item = ItemMenuConfig(
        title = config.str("item.title", "<dark_gray>Market <gray>» <white>%item%"),
        layout = config.layoutOrDefault("item.layout", DEFAULT_ITEM_LAYOUT, ITEM_SLOTS, log),
        empty = config.elementOrNull("item.empty"),
        display = config.elementOrNull("item.display"),
        quantity = config.elementOrNull("item.quantity"),
        sellAll = config.elementOrNull("item.sell_all"),
    )

    /*** The category screen as this category sees it: `category.overrides.<name>` merged over the base section, field by field. */
    fun category(name: String): CategoryMenuConfig = categoryOverrides[name.lowercase()] ?: categoryDefaults
}

private const val ROW_SLOTS = 9
private const val MAX_ROWS = 6

// '#' filler, 's' sell everything, 'g' category button, '<'/'>' category page nav,
// 'u' top gainer, 'l' gainers label, 'd' top faller, 'L' fallers label, 'c' close.
private const val MAIN_SLOTS = "#sg<>uldLc"

// '#' filler, 'i' category label, 'x' item, 'b' back, '<'/'>' page nav, 'c' close.
private const val CATEGORY_SLOTS = "#ixb<>c"

// '#' filler, 'd' item display, 'q' quantity tier, 'a' sell all, 'b' back, 'c' close.
private const val ITEM_SLOTS = "#dqabc"

private val DEFAULT_MAIN_LAYOUT = listOf(
    "# # # # # # # # s",
    "# g g g g g g g #",
    "# g g g g g g g #",
    "l u u u u u u u u",
    "L d d d d d d d d",
    "# < # # # # # > c",
)

private val DEFAULT_CATEGORY_LAYOUT = listOf(
    "# # # # i # # # #",
    "# x x x x x x x #",
    "# x x x x x x x #",
    "# x x x x x x x #",
    "b < # # # # # > c",
)

private val DEFAULT_ITEM_LAYOUT = listOf(
    "# # # # d # # # #",
    "# q q q q q q q #",
    "b # a # # # # # c",
)

private fun loadFile(plugin: DynamicMarket): YamlConfiguration {
    val file = File(plugin.dataFolder, "menus.yml")
    if (!file.exists()) {
        file.parentFile.mkdirs()
        plugin.saveResource("menus.yml", false)
    }
    return YamlConfiguration.loadConfiguration(file)
}

private fun ConfigurationSection.element(path: String, defaultIcon: String, defaultName: String, defaultLore: List<String>): GuiElementConfig {
    val section = getConfigurationSection(path)
    return GuiElementConfig(
        icon = section?.getString("icon") ?: defaultIcon,
        name = section?.getString("name") ?: defaultName,
        lore = if (section?.isSet("lore") == true) section.getStringList("lore") else defaultLore,
    )
}

/*** Null when the section is absent — the admin removed it. Present-but-sparse fields still get a safe fallback. */
private fun ConfigurationSection.elementOrNull(path: String): GuiElementConfig? {
    val section = getConfigurationSection(path) ?: return null
    return GuiElementConfig(
        icon = section.getString("icon")?.takeIf(String::isNotBlank),
        name = section.getString("name") ?: " ",
        lore = section.getStringList("lore"),
    )
}

/*** One line or several — a description that fits on one shouldn't have to be written as a list. */
private fun ConfigurationSection.lines(path: String, base: List<String>): List<String> = when (val value = get(path)) {
    null -> base
    is List<*> -> value.mapNotNull { it?.toString() }
    else -> listOf(value.toString())
}

/***
 * Same idea inside an override, except "absent" now means *inherit* rather than *off* — an
 * override that only wants different lore shouldn't have to restate the icon and name. Writing
 * `label: false` (anything that isn't a config block) is how you switch an element off for one
 * category when the base section has it on.
 */
private fun ConfigurationSection.overrideElement(path: String, base: GuiElementConfig?): GuiElementConfig? {
    if (!isSet(path)) return base
    val section = getConfigurationSection(path) ?: return null
    return GuiElementConfig(
        icon = section.getString("icon")?.takeIf(String::isNotBlank) ?: base?.icon,
        name = section.getString("name") ?: base?.name ?: " ",
        lore = if (section.isSet("lore")) section.getStringList("lore") else base?.lore.orEmpty(),
    )
}

/***
 * InvUI throws on a ragged or oversized structure, which would surface as a stack trace the
 * first time someone runs /market rather than on the reload that broke it. Checked here instead,
 * and a layout that can't work is dropped for the built-in one so the menu still opens.
 */
private fun ConfigurationSection.layoutOrDefault(
    path: String,
    default: List<String>,
    slots: String,
    log: Logger,
    where: String = path,
): List<String> {
    if (!isSet(path)) return default

    val rows = getStringList(path)
    val packed = rows.map { row -> row.filterNot(Char::isWhitespace) }
    val problem = when {
        packed.isEmpty() -> "it's empty"
        packed.size > MAX_ROWS -> "it has ${packed.size} rows and a chest fits $MAX_ROWS"
        packed.any { it.length != ROW_SLOTS } -> "every row needs exactly $ROW_SLOTS slots"
        else -> null
    }
    if (problem != null) {
        log.warning("menus.yml $where is unusable ($problem) — falling back to the built-in layout")
        return default
    }

    val unknown = packed.flatMap(String::toList).filterNot { it in slots }.distinct()
    if (unknown.isNotEmpty()) {
        log.warning(
            "menus.yml $where uses slot character(s) ${unknown.joinToString(" ")} that mean nothing on this " +
                "screen — those slots will render empty. Valid here: ${slots.toList().joinToString(" ")}"
        )
    }
    return rows
}

private fun ConfigurationSection.categoryOverrides(base: CategoryMenuConfig, log: Logger): Map<String, CategoryMenuConfig> {
    val overrides = getConfigurationSection("category.overrides") ?: return emptyMap()
    return overrides.getKeys(false).mapNotNull { name ->
        val override = overrides.getConfigurationSection(name)
        if (override == null) {
            log.warning("menus.yml category.overrides.$name isn't a config block — ignoring it")
            return@mapNotNull null
        }
        name.lowercase() to CategoryMenuConfig(
            title = override.getString("title") ?: base.title,
            layout = override.layoutOrDefault(
                "layout",
                base.layout,
                CATEGORY_SLOTS,
                log,
                where = "category.overrides.$name.layout",
            ),
            description = override.lines("description", base.description),
            button = override.overrideElement("button", base.button),
            empty = override.overrideElement("empty", base.empty),
            label = override.overrideElement("label", base.label),
            item = override.overrideElement("item", base.item),
        )
    }.toMap()
}

private fun ConfigurationSection.str(path: String, default: String): String = getString(path, default) ?: default
