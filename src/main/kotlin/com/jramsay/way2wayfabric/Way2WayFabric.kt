package com.jimramsay.way2wayfabric

import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.math.BlockPos
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

fun WaypointsManager.sameDimensionAs(waystone: GenericWaystone): Boolean {
    return currentWorld.container.subName == waystone.dimension
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

fun WaypointSet.updateWaypointFor(waystone: GenericWaystone): Boolean {
    val new = waystone.toWaypoint()
    logger.debug("Updating waypoint for $waystone")
    val existing = list.find { it.matches(new) }
    if (existing == null) {
        logger.info("Adding new waypoint for $waystone")
        list.add(new)
        return true
    }
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
            val waypointSet = mgr.currentWorld.currentSet

            var stale =
                waypointSet.list.filter {
                    it.fromMod(modIdx)
                }

            var changed = 0
            waystones.filter {
                mgr.sameDimensionAs(it)
            }.forEach {
                if (waypointSet.updateWaypointFor(it)) {
                    changed += 1
                }
                stale =
                    stale.filterNot {
                            wp ->
                        wp.matches(it)
                    }
            }

            if (stale.size > 0) {
                waypointSet.list.removeAll(stale)
            }

            if (changed > 0 || stale.size > 0) {
                logger.info("Synchronized $changed waystone waypoints and removed ${stale.size} stale entries")
                XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
            }
        }
    }

    override fun syncWaystone(waystone: GenericWaystone) {
        mapWatcher.whenReady { mgr ->
            logger.debug("Update: $waystone")
            val waypointSet = mgr.currentWorld.currentSet

            if (mgr.sameDimensionAs(waystone)) {
                if (waypointSet.updateWaypointFor(waystone)) {
                    logger.debug("Updated $waystone")
                    XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
                }
            }
        }
    }

    override fun removeWaystone(waystone: GenericWaystone) {
        mapWatcher.whenReady { mgr ->
            logger.debug("Removing waypoint for $waystone")
            val waypointSet = mgr.currentWorld.currentSet

            val changed =
                waypointSet.list.removeIf {
                    it.matches(waystone)
                }
            if (changed) {
                logger.info("Removed waypoint for $waystone")
                XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
            }
        }
    }

    override fun removeAllWaystones(modIdx: Int) {
        mapWatcher.whenReady { mgr ->
            logger.debug("Removing all waystone waypoints")
            val waypointSet = mgr.currentWorld.currentSet

            val changed =
                waypointSet.list.removeIf {
                    it.fromMod(modIdx)
                }
            if (changed) {
                logger.info("Removed all waystone waypoints")
                XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
            }
        }
    }
}
