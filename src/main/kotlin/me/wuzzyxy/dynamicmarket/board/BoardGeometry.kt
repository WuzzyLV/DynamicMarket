package me.wuzzyxy.dynamicmarket.board

import org.bukkit.Location
import kotlin.math.cos
import kotlin.math.sin

/***
 * Screens are authored as if the board faces yaw 0 (south) — see holograms.yml. This rotates
 * the horizontal (dx, dz) offset about the board's own yaw so a screen written once renders
 * correctly on whichever wall it's placed on. Standard 2D rotation matching Bukkit's yaw
 * convention (yaw 0 -> +Z, yaw 90 -> -X): the same transform that turns (0,1) into the
 * direction vector `Location.getDirection()` would report at that yaw.
 */
fun boardOffsetLocation(origin: Location, dx: Double, dy: Double, dz: Double, boardYaw: Float): Location {
    val radians = Math.toRadians(boardYaw.toDouble())
    val cos = cos(radians)
    val sin = sin(radians)
    val rotatedX = dx * cos - dz * sin
    val rotatedZ = dx * sin + dz * cos

    val location = origin.clone().add(rotatedX, dy, rotatedZ)
    location.yaw = boardYaw
    location.pitch = 0f
    return location
}

/*** Boards are flat wall decor authored for yaw 0, so placement always snaps to the nearest 90°. */
fun snapYaw(yaw: Float): Float {
    val normalized = ((yaw % 360f) + 360f) % 360f
    val snapped = (Math.round(normalized / 90f) * 90) % 360
    return snapped.toFloat()
}
