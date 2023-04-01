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
    fun syncAllWaystones(waystones: List<GenericWaystone>)
    fun removeWaystone(waystone: GenericWaystone)
    fun removeAllWaystones()
}

interface IWaystoneProvider {
    val isPresent: Boolean
    fun register(handler: IWay2WayHandler)
}

data class GenericWaystone(val x: Int, val y: Int, val z: Int, val name: String, val dimension: String) {
    constructor(pos : BlockPos, name: String, dimension: String) : this(pos.x, pos.y, pos.z, name, dimension)

    companion object {
        val SYMBOL = "〨"
    }

    override fun toString(): String {
        return "$SYMBOL:$name ($x,$y,$z)"
    }

    fun toWaypoint(): Waypoint {
        val color = Random.nextInt(ModSettings.ENCHANT_COLORS.size)
        return Waypoint(x, y, z, name, SYMBOL, color, 0, false)
    }
}

fun WaypointsManager.getNamedSet(name: String): WaypointSet {
    val world = currentWorld
    return world.sets.getOrElse(name, {
        world.addSet(name)
        return world.sets.get(name)!!
    })
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

@Suppress("UNUSED")
object Way2WayFabric: ModInitializer, IWay2WayHandler {
    const val MOD_ID = "way2wayfabric"
    private val warned = HashSet<String>()

    override fun onInitialize() {
        var providers= 0
        if (BlayWaystones.isPresent) {
            BlayWaystones.register(this)
            logger.info("Registered with waystones for waypoint sync")
            providers++
        }
        logger.info("Way2wayFabric has been initialized for $providers waystone provider(s)")
    }

    fun waypointManager() : WaypointsManager? {
        return XaeroMinimapSession.getCurrentSession()?.waypointsManager
    }

    fun warnOnce(message: String) {
        if (!warned.contains(message)) {
            logger.warn(message)
            warned.add(message)
        }
    }

    fun updateAllWaypoints(waystones: List<GenericWaystone>, repeats: Int) {
        val waypointMgr = waypointManager()
        if (waypointMgr == null) {
            warnOnce("Could not get a waypoint manager")
            return
        }

        val waypointSet = waypointMgr.currentWorld?.currentSet
        if (waypointSet == null) {
            if (repeats < 20) {
                // TODO: If there was an event from Xaero's Minimap we could
                // wait for that would signal the wayponts are ready that would
                // be better, but I haven't found one yet...
                Timer("Deferred Event", false).schedule(500) {
                    logger.debug("Running deferred event handler...")
                    updateAllWaypoints(waystones, repeats + 1)
                }
            } else {
                warnOnce("Could not find a world to put waypoints in")
            }
            return
        }

        var stale = waypointSet.list.filter {
            it.symbol == GenericWaystone.SYMBOL
        }

        var changed = 0
        waystones.filter {
            waypointMgr.sameDimensionAs(it)
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
            XaeroMinimap.instance.settings.saveAllWaypoints(waypointMgr)
        }
    }

    override fun syncAllWaystones(waystones: List<GenericWaystone>) {
        logger.debug("Known: ${waystones.size} waystones")
        updateAllWaypoints(waystones, 0)
    }

    override fun syncWaystone(waystone: GenericWaystone) {
        logger.debug("Update: ${waystone}")
        val waypointMgr = waypointManager()
        if (waypointMgr == null) {
            warnOnce("Could not get a waypoint manager")
            return
        }

        val waypointSet = waypointMgr.currentWorld?.currentSet
        if (waypointSet == null) {
            warnOnce("Could not find a world to put waypoints in")
            return
        }

        if (waypointMgr.sameDimensionAs(waystone)) {
            if (waypointSet.updateWaypointFor(waystone)) {
                logger.info("Updated waystone \"${waystone.name}\" waypoint")
                XaeroMinimap.instance.settings.saveAllWaypoints(waypointMgr)
            }
        }
    }

    override fun removeWaystone(waystone: GenericWaystone) {
        logger.debug("Removing waypoint for $waystone")

        val waypointMgr = waypointManager()
        if (waypointMgr == null) {
            warnOnce("Could not get a waypoint manager")
            return
        }

        val waypointSet = waypointMgr.currentWorld?.currentSet
        if (waypointSet == null) {
            warnOnce("Could not find a world to put waypoints in")
            return
        }
        val changed = waypointSet.list.removeIf {
            it.matches(waystone)
        }
        if (changed) {
            logger.info("Removed waypoint for $waystone")
            XaeroMinimap.instance.settings.saveAllWaypoints(waypointMgr)
        }
    }

    override fun removeAllWaystones() {
        logger.debug("Removing all waystone waypoints")
        val waypointMgr = waypointManager()
        if (waypointMgr == null) {
            warnOnce("Could not get a waypoint manager")
            return
        }

        val waypointSet = waypointMgr.currentWorld?.currentSet
        if (waypointSet == null) {
            warnOnce("Could not find a world to put waypoints in")
            return
        }
        val changed = waypointSet.list.removeIf {
            it.symbol == GenericWaystone.SYMBOL
        }
        if (changed) {
            logger.info("Removed all waystone waypoints")
            XaeroMinimap.instance.settings.saveAllWaypoints(waypointMgr)
        }
    }
}
