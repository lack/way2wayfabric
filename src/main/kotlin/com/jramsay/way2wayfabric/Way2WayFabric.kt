package com.jimramsay.way2wayfabric

import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import org.slf4j.LoggerFactory
import xaero.common.XaeroMinimapSession
import xaero.common.minimap.waypoints.Waypoint
import xaero.common.minimap.waypoints.WaypointSet
import xaero.common.minimap.waypoints.WaypointsManager
import xaero.common.settings.ModSettings
import xaero.minimap.XaeroMinimap
import java.util.LinkedList
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.random.Random

val logger = LoggerFactory.getLogger("Way2WayFabric")

interface IWay2WayHandler {
    fun syncWaystone(waystone: GenericWaystone)

    fun syncAllWaystones(
        waystones: List<GenericWaystone>,
        modIdx: Int,
    )

    fun removeWaystone(waystone: GenericWaystone)

    fun removeAllWaystones(modIdx: Int)
}

interface IWaystoneProvider {
    val isPresent: Boolean
    var modIdx: Int

    fun register(handler: IWay2WayHandler)
}

data class GenericWaystone(val x: Int, val y: Int, val z: Int, val name: String, val dimension: String, val modIdx: Int) {
    constructor(pos: BlockPos, name: String, dimension: String, modIdx: Int) : this(pos.x, pos.y, pos.z, name, dimension, modIdx)

    companion object {
        val SYMBOL = arrayOf("〨", "兰", "亗", "主")

        fun symbol(idx: Int): String {
            if (idx < 0) {
                return SYMBOL[0]
            }
            return SYMBOL[idx]
        }
    }

    val symbol: String
        get() = symbol(modIdx)

    override fun toString(): String {
        return "$symbol:$name ($x,$y,$z)"
    }

    fun toWaypoint(): Waypoint {
        val color = Random.nextInt(ModSettings.ENCHANT_COLORS.size)
        return Waypoint(x, y, z, name, symbol, color, 0, false)
    }
}

data class WaypointContext(val waypoint: Waypoint, val wpset: WaypointSet) {
    fun remove(): Boolean {
        return wpset.list.remove(waypoint)
    }

    fun matches(waystone: GenericWaystone): Boolean {
        return waypoint.matches(waystone)
    }

    fun updateFrom(waystone: GenericWaystone): Boolean {
        return wpset.updateWaypoint(waypoint, waystone)
    }
}

fun WaypointsManager.sameDimensionAs(waystone: GenericWaystone): Boolean {
    return currentWorld.container.subName == waystone.dimension
}

fun WaypointsManager.allModWaypoints(modIdx: Int): List<WaypointContext> {
    return this.currentWorld.sets.flatMap { wps -> wps.value.allWay2way(modIdx).map { WaypointContext(it, wps.value) } }
}

fun WaypointsManager.findExistingFor(waystone: GenericWaystone): WaypointContext? {
    return allModWaypoints(waystone.modIdx).find { it.matches(waystone) }
}

fun WaypointsManager.findWay2waySet(): WaypointSet {
    // For now, always use the currently active set
    return this.currentWorld.currentSet
}

fun Waypoint.matches(that: Waypoint): Boolean {
    return this.x == that.x && this.y == that.y && this.z == that.z
}

fun Waypoint.matches(that: GenericWaystone): Boolean {
    return this.x == that.x && this.y == that.y && this.z == that.z
}

fun Waypoint.fromMod(modIdx: Int): Boolean {
    return symbol == GenericWaystone.symbol(modIdx)
}

fun WaypointSet.allWay2way(modIdx: Int): List<Waypoint> {
    return this.list.filter { it.fromMod(modIdx) }
}

fun WaypointSet.addWaypointFor(waystone: GenericWaystone) {
    val new = waystone.toWaypoint()
    logger.info("Adding new waypoint for $waystone")
    list.add(new)
}

fun WaypointSet.updateWaypoint(
    existing: Waypoint,
    waystone: GenericWaystone,
): Boolean {
    val new = waystone.toWaypoint()
    logger.debug("Updating waypoint for $waystone")
    if (existing.name != new.name || existing.symbol != new.symbol) {
        logger.info("Found existing waypoint: ${existing.symbol}:${existing.name} -> ${new.symbol}:${new.name}")
        existing.name = new.name
        existing.symbol = new.symbol
        return true
    }
    return false
}

class MapWatcher {
    private val deferred = LinkedList<(WaypointsManager) -> Unit>()
    private var running = false
    private var count = 0

    @Synchronized
    fun whenReady(fn: (WaypointsManager) -> Unit) {
        val mgr = mgrIfReady()
        if (mgr == null || deferred.isNotEmpty()) {
            defer(fn)
            return
        }
        logger.debug("Running immediately")
        fn(mgr)
    }

    fun mgrIfReady(): WaypointsManager? {
        val mgr = XaeroMinimapSession.getCurrentSession()?.waypointsManager
        if (mgr?.currentWorld == null) {
            return null
        }
        return mgr
    }

    @Synchronized
    private fun defer(fn: (WaypointsManager) -> Unit) {
        if (givenUp()) return
        logger.debug("Deferring $fn")
        deferred.add(fn)
        logger.info("Deferred ${deferred.size} waypoint events so far")
        recheckMapReady()
    }

    fun givenUp(): Boolean {
        return count > 24
    }

    @Synchronized
    fun recheckMapReady() {
        val mgr = mgrIfReady()
        if (mgr == null) {
            if (givenUp()) {
                logger.warn(
                    "Waypoint manager is not ready after $count attempts. Giving up and discarding ${deferred.size} deferred events",
                )
                running = false
                return
            }
            if (!running) {
                logger.info("Defering waystone events until the waypoint manager is ready")
            } else {
                logger.debug("Setting deferred event timer ($count)")
            }
            running = true
            count++
            Timer("Deferred Events", false).schedule(500) {
                recheckMapReady()
            }
            return
        }
        logger.info("Waypoint manager is ready. Running ${deferred.size} deferred events")
        deferred.forEach {
            it(mgr)
        }
        deferred.clear()
        count = 0
        running = false
    }
}

@Suppress("UNUSED")
object Way2WayFabric : ModInitializer, IWay2WayHandler {
    const val MOD_ID = "way2wayfabric"
    private val mapWatcher = MapWatcher()
    private val compatibleMaps = arrayOf("xaerominimap", "xaerominimapfair")

    fun ensureCompatibleMap() {
        val matched =
            FabricLoader.getInstance().allMods.filter {
                    mod ->
                compatibleMaps.any { it == mod.metadata.id }
            }
        if (matched.any()) {
            logger.info("Found compatible map mod $matched")
        } else {
            logger.warn("No compatible map mods are loaded! (Expecting one of: $compatibleMaps)")
        }
    }

    override fun onInitialize() {
        var providers = LinkedList<IWaystoneProvider>()
        ensureCompatibleMap()
        if (BlayWaystones.isPresent) {
            BlayWaystones.register(this)
            logger.info("Registered with waystones for waypoint sync")
            providers.add(BlayWaystones)
        }
        if (FabricWaystones.isPresent) {
            FabricWaystones.register(this)
            logger.info("Registered with FabricWaystones for waypoint sync")
            providers.add(FabricWaystones)
        }
        if (providers.size == 1) {
            providers.first.modIdx = -1
        } else {
            providers.forEachIndexed {
                    i, it ->
                it.modIdx = i
            }
        }
        logger.info("Way2wayFabric has been initialized for ${providers.size} waystone provider(s)")
    }

    override fun syncAllWaystones(
        waystones: List<GenericWaystone>,
        modIdx: Int,
    ) {
        mapWatcher.whenReady { mgr ->
            logger.debug("Known: ${waystones.size} waystones")
            val existing = LinkedList(mgr.allModWaypoints(modIdx))
            val new = LinkedList<GenericWaystone>()
            var changed = 0
            waystones.filter {
                mgr.sameDimensionAs(it)
            }.forEach {
                val found = existing.find { wpc -> wpc.matches(it) }
                if (found == null) {
                    new.add(it)
                } else {
                    if (found.updateFrom(it)) {
                        changed += 1
                    }
                    existing.remove(found)
                }
            }
            if (new.isNotEmpty()) {
                var activeSet = mgr.findWay2waySet()
                new.forEach {
                    activeSet.addWaypointFor(it)
                }
            }

            existing.forEach { it.remove() }

            if (changed > 0 || existing.size > 0) {
                logger.info("Synchronized $changed waystone waypoints, added ${new.size} and removed ${existing.size} stale entries")
                XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
            }
        }
    }

    override fun syncWaystone(waystone: GenericWaystone) {
        mapWatcher.whenReady { mgr ->
            if (!mgr.sameDimensionAs(waystone)) {
                return@whenReady
            }
            logger.debug("Update: $waystone")
            val existing = mgr.findExistingFor(waystone)
            var changed: Boolean
            if (existing == null) {
                val waypointSet = mgr.findWay2waySet()
                waypointSet.addWaypointFor(waystone)
                changed = true
            } else {
                changed = existing.updateFrom(waystone)
            }

            if (changed) {
                logger.debug("Updated $waystone")
                XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
            }
        }
    }

    override fun removeWaystone(waystone: GenericWaystone) {
        mapWatcher.whenReady { mgr ->
            if (!mgr.sameDimensionAs(waystone)) {
                return@whenReady
            }
            logger.debug("Remove: $waystone")
            val existing = mgr.findExistingFor(waystone)
            if (existing == null) {
                return@whenReady
            }
            if (existing.remove()) {
                logger.info("Removed waypoint for $waystone")
                XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
            }
        }
    }

    override fun removeAllWaystones(modIdx: Int) {
        mapWatcher.whenReady { mgr ->
            logger.debug("Removing all waystone waypoints")
            val changed = mgr.allModWaypoints(modIdx).count { it.remove() }
            if (changed > 0) {
                logger.info("Removed $changed waystone waypoints")
                XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
            }
        }
    }
}
