package me.wuzzyxy.dynamicmarket.configs

import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.market.EventDirection
import me.wuzzyxy.dynamicmarket.market.EventScope
import me.wuzzyxy.dynamicmarket.market.MarketEventDefinition
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class EventConfig(private val plugin: DynamicMarket) {

    private val config: YamlConfiguration = initFile()

    private fun initFile(): YamlConfiguration {
        val configFile = File(plugin.dataFolder, "events.yml")
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            plugin.saveResource("events.yml", false)
        }
        return YamlConfiguration.loadConfiguration(configFile)
    }

    fun getAllDefinitions(): List<MarketEventDefinition> {
        val events = config.getConfigurationSection("events")
        if (events == null) {
            plugin.logger.warning("No events configured in events.yml")
            return emptyList()
        }

        return events.getKeys(false).mapNotNull { id -> parseDefinition(id, "events.$id.") }
    }

    private fun parseDefinition(id: String, path: String): MarketEventDefinition? {
        val scope = parseEnum<EventScope>(config.getString(path + "scope"), id, "scope") ?: return null
        val direction = parseEnum<EventDirection>(config.getString(path + "direction"), id, "direction") ?: return null

        val multiplierMin = config.getDouble(path + "multiplier_min", 1.0)
        val multiplierMax = config.getDouble(path + "multiplier_max", multiplierMin)
        if (multiplierMin <= 0 || multiplierMax < multiplierMin) {
            plugin.logger.warning("events.yml event '$id' has an invalid multiplier_min/max — skipping it")
            return null
        }

        return MarketEventDefinition(
            id = id,
            weight = config.getInt(path + "weight", 1).coerceAtLeast(0),
            scope = scope,
            targets = config.getStringList(path + "targets"),
            direction = direction,
            multiplierMin = multiplierMin,
            multiplierMax = multiplierMax,
            message = config.getString(path + "message", "") ?: "",
            badge = config.getStringList(path + "badge"),
        )
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, id: String, field: String): T? {
        val value = raw?.uppercase()?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
        if (value == null) {
            plugin.logger.warning("events.yml event '$id' has a missing/invalid $field ('$raw') — skipping it")
        }
        return value
    }
}
