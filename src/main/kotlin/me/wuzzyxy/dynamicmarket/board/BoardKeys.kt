package me.wuzzyxy.dynamicmarket.board

import me.wuzzyxy.dynamicmarket.DynamicMarket
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/*** PDC keys stamped on every board entity, always together via markBoard — see its doc. */
class BoardKeys(plugin: DynamicMarket) {
    val boardName: NamespacedKey = NamespacedKey(plugin, "board_name")
    val sessionToken: NamespacedKey = NamespacedKey(plugin, "board_session")
}

/***
 * Board name + session token, written together on purpose: the stale-entity purge
 * (HoloBoardManager's EntitiesLoadEvent listener) keys off the token, so an entity spawned
 * without it would become an unkillable orphan the next time the plugin reloads or restarts.
 */
fun Entity.markBoard(keys: BoardKeys, boardName: String, sessionToken: UUID) {
    persistentDataContainer.set(keys.boardName, PersistentDataType.STRING, boardName)
    persistentDataContainer.set(keys.sessionToken, PersistentDataType.STRING, sessionToken.toString())
}

fun Entity.boardName(keys: BoardKeys): String? =
    persistentDataContainer.get(keys.boardName, PersistentDataType.STRING)

fun Entity.boardSessionToken(keys: BoardKeys): String? =
    persistentDataContainer.get(keys.sessionToken, PersistentDataType.STRING)
