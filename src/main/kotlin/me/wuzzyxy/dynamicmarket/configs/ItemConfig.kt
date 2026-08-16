package me.wuzzyxy.dynamicmarket.configs

import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.items.MarketItem
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.math.ln

class ItemConfig(private val plugin: DynamicMarket) {

    private val config: YamlConfiguration = initFiles()

    private fun initFiles(): YamlConfiguration {
        val configFile = File(plugin.dataFolder, "items.yml")
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            plugin.saveResource("items.yml", false)
        }
        return YamlConfiguration.loadConfiguration(configFile)
    }

    fun getAllItems(): List<MarketItem> {
        val items = config.getConfigurationSection("items")
        if (items == null) {
            plugin.logger.severe("Failed to load items from config")
            return emptyList()
        }

        return items.getKeys(false).map { key ->
            val path = "items.$key."
            MarketItem(
                key,
                config.getDouble(path + "base_price"),
                0L, 0L,
                config.getDouble(path + "min_price"),
                impactK(path),
            ).apply {
                halfLifeHours = config.getDouble(path + "half_life_hours", MarketItem.DEFAULT_HALF_LIFE_HOURS)
                category = config.getString(path + "category", "misc") ?: "misc"
            }
        }
    }

    /***
     * units_to_double is the version a person can reason about — how many net units it
     * takes to double the price. Configs written before the rename only have percentage,
     * which is the same number to first order, so they carry over without editing.
     */
    private fun impactK(path: String): Double {
        val unitsToDouble = config.getDouble(path + "units_to_double", 0.0)
        if (unitsToDouble > 0) return ln(2.0) / unitsToDouble

        val percentage = config.getDouble(path + "percentage", 0.0)
        if (percentage > 0) return percentage

        val item = path.trimEnd('.')
        if (percentage < 0) {
            // Negative k runs the curve backwards: buying would make the item cheaper and
            // selling would make it dearer, which is a money printer, not a cheap shop.
            plugin.logger.warning("$item has a negative percentage, which inverts the price curve — treating it as no impact")
        } else {
            plugin.logger.warning("$item sets neither units_to_double nor percentage — its price will never move")
        }
        return 0.0
    }
}
