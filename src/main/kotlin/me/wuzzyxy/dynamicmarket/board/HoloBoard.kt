package me.wuzzyxy.dynamicmarket.board

import me.wuzzyxy.dynamicmarket.configs.ScreenDefinition
import me.wuzzyxy.dynamicmarket.configs.ScreenFrame
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID

private val TRANSPARENT: Color = Color.fromARGB(0, 0, 0, 0)
private val NO_ROTATION = AxisAngle4f(0f, 0f, 0f, 1f)

/***
 * One placed hologram: a single TextDisplay entity showing one frame at a time — a screen is
 * one physical sign, not one entity per line. Turning a frame into a Component is entirely
 * HoloBoardManager's job (it needs MarketReport for that, and builds one combined multi-line
 * MiniMessage string per frame rather than composing per-line Components, which is what was
 * producing doubled line breaks on the entity); this class only tracks which frame is showing
 * and drives the entity. render()/advanceFrame() are called by HoloBoardManager's two
 * repeating tasks.
 */
class HoloBoard(
    val name: String,
    val screenName: String,
    var screen: ScreenDefinition,
    var location: Location,
    var yaw: Float,
) {

    private var entity: TextDisplay? = null
    private var frameIndex: Int = 0
    private var ticksUntilNextFrame: Int = 0

    val chunk get() = location.chunk

    fun spawn(keys: BoardKeys, sessionToken: UUID) {
        val world = requireNotNull(location.world) { "board '$name' has no world" }
        val appearance = screen.appearance

        val spawned = world.spawn(boardOffsetLocation(location, appearance.x, appearance.y, appearance.z, yaw), TextDisplay::class.java)
        spawned.billboard = Display.Billboard.FIXED
        spawned.isVisibleByDefault = true
        spawned.backgroundColor = if (appearance.background) null else TRANSPARENT
        spawned.text(Component.empty())
        applyScale(spawned, appearance.scale)
        spawned.markBoard(keys, name, sessionToken)
        entity = spawned

        frameIndex = 0
        ticksUntilNextFrame = holdTicks(0)
    }

    fun destroy() {
        entity?.remove()
        entity = null
    }

    fun moveTo(newLocation: Location, newYaw: Float) {
        location = newLocation
        yaw = newYaw
        val appearance = screen.appearance
        entity?.takeIf { it.isValid }
            ?.teleport(boardOffsetLocation(location, appearance.x, appearance.y, appearance.z, yaw))
    }

    /*** Counts the current frame's hold time down by one tick-task pass; true once it's elapsed. */
    fun tick(ticksPerPass: Int): Boolean {
        if (screen.frames.isEmpty()) return false
        ticksUntilNextFrame -= ticksPerPass
        return ticksUntilNextFrame <= 0
    }

    /***
     * Re-renders whichever frame is currently showing, using fresh data — called on every
     * content refresh. Doesn't touch the hold timer, *unless* the frame that was showing has
     * gone empty since it was chosen (every sourced line on it lost its data), in which case
     * it jumps forward to the next one that still has something and starts that frame's hold
     * over, same as a normal cycle-driven advance would.
     */
    fun renderCurrent(hasData: (ScreenFrame) -> Boolean, componentFor: (ScreenFrame) -> Component) {
        show(frameIndex, hasData, componentFor, forceTimerReset = false)
    }

    /***
     * Hold time elapsed: move to the next frame in sequence, skipping any with no data, and
     * always start a fresh hold for wherever it lands (including landing back on the same
     * frame, if it's the only one with anything to show — otherwise its timer would sit at
     * zero and this would fire on every tick pass instead of waiting a full hold period).
     */
    fun advanceFrame(hasData: (ScreenFrame) -> Boolean, componentFor: (ScreenFrame) -> Component) {
        if (screen.frames.isEmpty()) return
        show((frameIndex + 1) % screen.frames.size, hasData, componentFor, forceTimerReset = true)
    }

    /***
     * Searches forward from [start] (wrapping) for the first frame [hasData] accepts, shows
     * it, and blanks the entity instead if nothing on the board has data right now — same
     * "blank text is how you hide it" rule the rest of this plugin's holograms use.
     */
    private fun show(start: Int, hasData: (ScreenFrame) -> Boolean, componentFor: (ScreenFrame) -> Component, forceTimerReset: Boolean) {
        val entity = entity?.takeIf { it.isValid } ?: return
        val frameCount = screen.frames.size
        if (frameCount == 0) {
            entity.text(Component.empty())
            return
        }

        val landed = findFrameWithData(start, hasData)
        if (landed == null) {
            if (forceTimerReset) ticksUntilNextFrame = holdTicks(start)
            entity.text(Component.empty())
            return
        }

        if (forceTimerReset || landed != frameIndex) {
            ticksUntilNextFrame = holdTicks(landed)
        }
        frameIndex = landed
        entity.text(componentFor(screen.frames[landed]))
    }

    private fun findFrameWithData(start: Int, hasData: (ScreenFrame) -> Boolean): Int? {
        val frameCount = screen.frames.size
        for (offset in 0 until frameCount) {
            val index = (start + offset) % frameCount
            if (hasData(screen.frames[index])) return index
        }
        return null
    }

    private fun holdTicks(frame: Int): Int = (screen.frames.getOrNull(frame)?.holdSeconds ?: 8) * 20

    private fun applyScale(entity: TextDisplay, scale: Double) {
        val s = scale.toFloat()
        entity.transformation = Transformation(Vector3f(), NO_ROTATION, Vector3f(s, s, s), NO_ROTATION)
    }
}
