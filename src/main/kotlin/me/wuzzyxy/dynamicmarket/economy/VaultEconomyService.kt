package me.wuzzyxy.dynamicmarket.economy

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.text.DecimalFormat

/***
 * Looks the Economy provider up fresh on every call instead of caching it at construction —
 * Vault (or the economy plugin behind it) can register, unregister or swap providers at any
 * point in the server's life, and this is cheap enough not to matter.
 */
class VaultEconomyService {

    private val economy: Economy?
        get() = Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider

    val available: Boolean
        get() = economy != null

    fun balance(player: OfflinePlayer): Double = economy?.getBalance(player) ?: 0.0

    fun has(player: OfflinePlayer, amount: Double): Boolean = economy?.has(player, amount) ?: false

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean =
        economy?.withdrawPlayer(player, amount)?.transactionSuccess() ?: false

    fun deposit(player: OfflinePlayer, amount: Double): Boolean =
        economy?.depositPlayer(player, amount)?.transactionSuccess() ?: false

    fun format(amount: Double): String = economy?.format(amount) ?: FALLBACK_FORMAT.format(amount)

    private companion object {
        val FALLBACK_FORMAT = DecimalFormat("0.00")
    }
}
