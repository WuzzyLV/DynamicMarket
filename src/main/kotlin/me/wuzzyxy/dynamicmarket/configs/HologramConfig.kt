package me.wuzzyxy.dynamicmarket.configs

import me.wuzzyxy.dynamicmarket.DynamicMarket
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Logger

/*** The board's one TextDisplay: fixed position/appearance, never changes between frames. */
data class BoardAppearance(
    val x: Double,
    val y: Double,
    val z: Double,
    val scale: Double,
    /*** True keeps the vanilla text background box; default is transparent. */
    val background: Boolean,
)

sealed class ScreenLine {
    data class Static(val text: String) : ScreenLine()

    /***
     * template/bullish/bearish/mixed/up/down each default to the matching report.* template in
     * config.yml when null — set one here to override just this line's wording/colour without
     * touching the global default every other board still uses. mover/category-summary/
     * volume-leader use `template`; sentiment is tiered so it has one override per tier
     * (`bullish`/`bearish`/`mixed`); top-mover doesn't know its sign until render time, so it
     * takes `up`/`down` instead of a single `template`.
     */
    data class Sourced(
        val source: String,
        val category: String,
        val direction: String,
        val rank: Int,
        val template: String?,
        val bullish: String?,
        val bearish: String?,
        val mixed: String?,
        val up: String?,
        val down: String?,
    ) : ScreenLine()
}

/*** One page of a screen: how long it's shown, and the lines stacked (newline-joined) on it. */
data class ScreenFrame(val holdSeconds: Int, val lines: List<ScreenLine>)

data class ScreenDefinition(val appearance: BoardAppearance, val frames: List<ScreenFrame>)

/***
 * holograms.yml — admin-authored screen definitions for the market holo boards: the board's
 * appearance plus the frames that cycle through it. A screen is ONE hologram (one TextDisplay,
 * its lines newline-joined into a single Component) — never one entity per line. Loaded the
 * same defensive way as every other config here (configs/MenuConfig.kt, configs/ItemConfig.kt):
 * never overwrites a file already on disk, and a malformed screen/frame/line is skipped with a
 * warning rather than failing the whole load.
 */
class HologramConfig(plugin: DynamicMarket) {

    val screens: Map<String, ScreenDefinition>

    init {
        val config = loadFile(plugin)
        val logger = plugin.logger
        val names = config.getConfigurationSection("screens")?.getKeys(false).orEmpty()
        screens = names.mapNotNull { name -> parseScreen(config, name, logger)?.let { name to it } }.toMap()
    }
}

private fun parseScreen(config: YamlConfiguration, name: String, logger: Logger): ScreenDefinition? {
    val path = "screens.$name"

    val position = config.getConfigurationSection("$path.position")
    val appearance = BoardAppearance(
        x = position?.getDouble("x") ?: 0.0,
        y = position?.getDouble("y") ?: 0.0,
        z = position?.getDouble("z") ?: 0.0,
        scale = config.getDouble("$path.scale", 1.0),
        background = config.getBoolean("$path.background", false),
    )

    val frames = config.getMapList("$path.frames").mapNotNull { parseFrame(it, name, logger) }
    if (frames.isEmpty()) {
        logger.warning("holograms.yml: screen '$name' has no valid frames, skipping it")
        return null
    }

    return ScreenDefinition(appearance, frames)
}

private fun parseFrame(raw: Map<*, *>, screen: String, logger: Logger): ScreenFrame? {
    val holdSeconds = raw.int("hold-seconds") ?: 8
    val rawLines = raw["lines"] as? List<*> ?: emptyList<Any?>()
    val lines = rawLines.mapNotNull { parseLine(it, screen, logger) }

    if (lines.isEmpty()) {
        logger.warning("holograms.yml: screen '$screen' has a frame with no valid lines, skipping it")
        return null
    }
    return ScreenFrame(holdSeconds, lines)
}

/***
 * A line is either a bare MiniMessage string (static) or a {source, ...} object naming which
 * MarketReport aggregate to pull from. See HoloBoardManager.componentFor for what each source
 * maps to and how the template/bullish/bearish/mixed overrides are applied.
 */
private fun parseLine(raw: Any?, screen: String, logger: Logger): ScreenLine? = when (raw) {
    is String -> ScreenLine.Static(raw)
    is Map<*, *> -> {
        val source = raw["source"] as? String
        when (source) {
            null -> {
                logger.warning("holograms.yml: screen '$screen' has a line with no 'source', skipping it")
                null
            }
            "static" -> ScreenLine.Static(raw["text"] as? String ?: "")
            else -> ScreenLine.Sourced(
                source = source,
                category = raw["category"] as? String ?: "all",
                direction = raw["direction"] as? String ?: "up",
                rank = raw.int("rank") ?: 1,
                template = raw["template"] as? String,
                bullish = raw["bullish"] as? String,
                bearish = raw["bearish"] as? String,
                mixed = raw["mixed"] as? String,
                up = raw["up"] as? String,
                down = raw["down"] as? String,
            )
        }
    }
    else -> {
        logger.warning("holograms.yml: screen '$screen' has an unreadable line entry, skipping it")
        null
    }
}

private fun Map<*, *>.int(key: String): Int? = (this[key] as? Number)?.toInt()

private fun loadFile(plugin: DynamicMarket): YamlConfiguration {
    val file = File(plugin.dataFolder, "holograms.yml")
    if (!file.exists()) {
        file.parentFile.mkdirs()
        plugin.saveResource("holograms.yml", false)
    }
    return YamlConfiguration.loadConfiguration(file)
}
