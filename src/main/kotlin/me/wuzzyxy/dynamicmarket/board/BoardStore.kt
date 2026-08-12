package me.wuzzyxy.dynamicmarket.board

import me.wuzzyxy.dynamicmarket.DynamicMarket
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException

data class PlacedBoard(
    val name: String,
    val screen: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
)

/***
 * boards.yml — plugin-owned record of placed holo boards, written by /dmarket holoboard
 * place/remove/move. Rewritten wholesale on every change (hand edits only with the server
 * stopped, same caveat as any plugin-owned config), and loaded defensively — a board with a
 * missing field, or one whose world isn't loaded at the time of the *caller's* lookup, is
 * skipped with a warning rather than failing every other board's load.
 */
class BoardStore(private val plugin: DynamicMarket) {

    private val file = File(plugin.dataFolder, "boards.yml")

    fun load(): List<PlacedBoard> {
        if (!file.exists()) return emptyList()

        val config = YamlConfiguration.loadConfiguration(file)
        val names = config.getConfigurationSection("boards")?.getKeys(false).orEmpty()
        return names.mapNotNull { name -> parseBoard(config, name) }
    }

    private fun parseBoard(config: YamlConfiguration, name: String): PlacedBoard? {
        val path = "boards.$name"
        val screen = config.getString("$path.screen")
        val world = config.getString("$path.world")
        val hasCoords = config.isSet("$path.x") && config.isSet("$path.y") && config.isSet("$path.z")

        if (screen == null || world == null || !hasCoords) {
            plugin.logger.warning("boards.yml: board '$name' is missing screen/world/coordinates, skipping it")
            return null
        }

        return PlacedBoard(
            name, screen, world,
            config.getDouble("$path.x"),
            config.getDouble("$path.y"),
            config.getDouble("$path.z"),
            config.getDouble("$path.yaw", 0.0).toFloat(),
        )
    }

    fun save(boards: List<PlacedBoard>) {
        val config = YamlConfiguration()
        for (board in boards) {
            val path = "boards.${board.name}"
            config.set("$path.screen", board.screen)
            config.set("$path.world", board.world)
            config.set("$path.x", board.x)
            config.set("$path.y", board.y)
            config.set("$path.z", board.z)
            config.set("$path.yaw", board.yaw.toDouble())
        }

        try {
            file.parentFile.mkdirs()
            config.save(file)
        } catch (failure: IOException) {
            plugin.logger.warning("Failed to save boards.yml: ${failure.message}")
        }
    }
}
