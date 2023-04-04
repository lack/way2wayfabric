package com.jimramsay.way2wayfabric

import net.fabricmc.api.ModInitializer
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.random.Random

import xaero.common.minimap.waypoints.WaypointSet
import xaero.common.minimap.waypoints.Waypoint
import xaero.common.minimap.waypoints.WaypointsManager
import xaero.common.settings.ModSettings
import xaero.common.XaeroMinimapSession
import xaero.minimap.XaeroMinimap

val logger = LoggerFactory.getLogger("Way2WayFabric")

interface IWay2WayHandler {
    fun syncWaystone(waystone: GenericWaystone)
    fun syncAllWaystones(waystones: List<GenericWaystone>, modIdx: Int)
    fun removeWaystone(waystone: GenericWaystone)
    fun removeAllWaystones(modIdx: Int)
}

interface IWaystoneProvider {
    val isPresent: Boolean
    var modIdx: Int
    fun register(handler: IWay2WayHandler)
}

data class GenericWaystone(val x: Int, val y: Int, val z: Int, val name: String, val dimension: String, val modIdx: Int) {
    constructor(pos : BlockPos, name: String, dimension: String, modIdx: Int) : this(pos.x, pos.y, pos.z, name, dimension, modIdx)

    companion object {
        val SYMBOL = arrayOf("〨", "兰", "亗", "主")

        fun symbol(idx: Int): String {
            if (idx < 0)
                return SYMBOL[0]
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
    val existing = list.find{it.matches(new)}
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
    private var _waypointMgr: WaypointsManager? = null
    private val deferred = ArrayList<(WaypointsManager)->Unit>()
    private var running = false
    private var count = 0

    fun tryInit() {
        if (_waypointMgr == null) {
            _waypointMgr = XaeroMinimapSession.getCurrentSession()?.waypointsManager
        }
        if (!ready()) {
            if (failed()) {
                logger.warn("Waypoint manager is not ready after $count attempts. Giving up and discarding ${deferred.size} deferred events")
                running = false
                return
            }
            if (!running)
                logger.info("Defering waystone events until the waypoint manager is ready")
            else
                logger.debug("Setting deferred event timer ($count)")
            running = true
            count++
            Timer("Deferred Event", false).schedule(500) {
                tryInit()
            }
            return
        }
        logger.info("Waypoint manager is ready. Running ${deferred.size} deferred events")
        deferred.forEach{
            it(waypointMgr)
        }
        running = false
    }

    fun failed(): Boolean {
        return count > 20
    }

    fun ready(): Boolean {
        val currentWorld = _waypointMgr?.currentWorld
        return _waypointMgr != null && currentWorld != null
    }

    fun whenReady(fn : (WaypointsManager)->Unit) {
        if (ready()) {
            logger.debug("Running immediately")
            fn(waypointMgr)
        }
        if (failed()) return
        logger.debug("Deferring $fn")
        deferred.add(fn)
        if (!running) tryInit()
        logger.info("Deferred ${deferred.size} waypoint events so far")
    }

    val waypointMgr: WaypointsManager
        get() = _waypointMgr!!
}

@Suppress("UNUSED")
object Way2WayFabric: ModInitializer, IWay2WayHandler {
    const val MOD_ID = "way2wayfabric"
    private val mapWatcher = MapWatcher()

    override fun onInitialize() {
        var providers = ArrayList<IWaystoneProvider>()
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
        if (providers.size == 1)
            providers[0].modIdx = -1
        else
            providers.forEachIndexed {
                i, it -> it.modIdx = i
            }
        logger.info("Way2wayFabric has been initialized for ${providers.size} waystone provider(s)")
    }

    override fun syncAllWaystones(waystones: List<GenericWaystone>, modIdx: Int) { mapWatcher.whenReady { mgr ->
        logger.debug("Known: ${waystones.size} waystones")
        val waypointSet = mgr.currentWorld.currentSet

        var stale = waypointSet.list.filter {
            it.fromMod(modIdx)
        }

        var changed = 0
        waystones.filter {
            mgr.sameDimensionAs(it)
        }.forEach {
            if (waypointSet.updateWaypointFor(it))
                changed += 1
            stale = stale.filterNot {
                wp -> wp.matches(it)
            }
        }

        if (stale.size > 0) {
            waypointSet.list.removeAll(stale)
        }
        
        if (changed > 0 || stale.size > 0) {
            logger.info("Synchronized $changed waystone waypoints and removed ${stale.size} stale entries")
            XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
        }
    } }

    override fun syncWaystone(waystone: GenericWaystone) { mapWatcher.whenReady { mgr ->
        logger.debug("Update: ${waystone}")
        val waypointSet = mgr.currentWorld.currentSet

        if (mgr.sameDimensionAs(waystone)) {
            if (waypointSet.updateWaypointFor(waystone)) {
                logger.info("Updated waystone \"${waystone.name}\" waypoint")
                XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
            }
        }
    } }

    override fun removeWaystone(waystone: GenericWaystone) { mapWatcher.whenReady { mgr ->
        logger.debug("Removing waypoint for $waystone")
        val waypointSet = mgr.currentWorld.currentSet

        val changed = waypointSet.list.removeIf {
            it.matches(waystone)
        }
        if (changed) {
            logger.info("Removed waypoint for $waystone")
            XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
        }
    } }

    override fun removeAllWaystones(modIdx: Int) { mapWatcher.whenReady { mgr ->
        logger.debug("Removing all waystone waypoints")
        val waypointSet = mgr.currentWorld.currentSet

        val changed = waypointSet.list.removeIf {
            it.fromMod(modIdx)
        }
        if (changed) {
            logger.info("Removed all waystone waypoints")
            XaeroMinimap.instance.settings.saveAllWaypoints(mgr)
        }
    } }
}
