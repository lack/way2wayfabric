package com.jimramsay.way2wayfabric
import net.fabricmc.api.ModInitializer
import net.blay09.mods.balm.api.Balm
import net.blay09.mods.waystones.api.IWaystone
import net.blay09.mods.waystones.api.WaystoneActivatedEvent
import net.blay09.mods.waystones.api.KnownWaystonesEvent
import net.blay09.mods.waystones.api.WaystoneUpdateReceivedEvent
import xaero.common.core.IXaeroMinimapClientPlayNetHandler
import xaero.common.minimap.waypoints.WaypointSet
import xaero.common.minimap.waypoints.Waypoint
import xaero.common.minimap.waypoints.WaypointsManager
import xaero.common.settings.ModSettings
import xaero.common.XaeroMinimapSession
import xaero.minimap.XaeroMinimap
import net.minecraft.client.MinecraftClient
import org.slf4j.LoggerFactory
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.random.Random

val logger = LoggerFactory.getLogger("Way2WayFabric")

fun IWaystone.fromDimension(dim: String): Boolean {
    logger.debug("Waystone ${this.name}: Checking dimension ${this.dimension.value.path} vs map dimension ${dim}")
    return this.dimension.value.path == dim
}

fun WaypointsManager.getNamedSet(name: String): WaypointSet {
    val world = this.currentWorld
    return world.sets.getOrElse(name, {
        world.addSet(name)
        return world.sets.get(name)!!
    })
}

fun WaypointsManager.sameDimensionAs(waystone: IWaystone): Boolean {
    val currentDimension = this.currentWorld.container.subName
    return waystone.fromDimension(currentDimension)
}

fun Waypoint(waystone: IWaystone): Waypoint {
    val pos = waystone.pos.up(2)
    val color = Random.nextInt(ModSettings.ENCHANT_COLORS.size)
    val symbol = "〨"
    return Waypoint(pos.x, pos.y, pos.z, waystone.name, symbol, color, 0, false)
}

fun Waypoint.matches(that: Waypoint): Boolean {
    return this.x == that.x && this.y == that.y && this.z == that.z
}

fun WaypointSet.updateWaypoint(new: Waypoint): Boolean {
    logger.debug("Updating waypoint ${new.name}")
    val existing = this.list.find{it.matches(new)}
    if (existing == null) {
        logger.info("Adding new waypoint: ${new.symbol}:${new.name}")
        this.list.add(new)
        return true
    }
    if (existing.name != new.name || existing.symbol != new.symbol) {
        logger.info("Found existing waypoint: ${existing.symbol}:${existing.name} -> ${new.symbol}${new.name}")
        existing.name = new.name
        existing.symbol = new.symbol
        return true
    }
    return false
}

@Suppress("UNUSED")
object Way2WayFabric: ModInitializer {
    const val MOD_ID = "way2wayfabric"
    private val warned = HashSet<String>()

    override fun onInitialize() {
        Balm.getEvents().onEvent(KnownWaystonesEvent::class.java, this::onKnownWaystones)
        Balm.getEvents().onEvent(WaystoneUpdateReceivedEvent::class.java, this::onWaystoneUpdate)
        logger.info("Way2wayFabric has been initialized.")
    }

    fun waypointManager() : WaypointsManager? {
        return XaeroMinimapSession.getCurrentSession()?.waypointsManager
    }

    fun warnOnce(message: String) {
        if (!this.warned.contains(message)) {
            logger.warn(message)
            this.warned.add(message)
        }
    }

    fun updateAllWaypoints(ev: KnownWaystonesEvent, repeats: Int) {
        val waypointMgr = this.waypointManager()
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
                    updateAllWaypoints(ev, repeats + 1)
                }
            } else {
                warnOnce("Could not find a world to put waypoints in")
            }
            return
        }

        var changed = 0
        ev.waystones.filter {
            waypointMgr.sameDimensionAs(it)
        }.forEach {
            if (waypointSet.updateWaypoint(Waypoint(it)))
                changed += 1
        }
        
        if (changed > 0) {
            logger.info("Synchronized $changed waystone waypoints")
            XaeroMinimap.instance.settings.saveAllWaypoints(waypointMgr)
        }
    }

    fun onKnownWaystones(ev: KnownWaystonesEvent) {
        logger.debug("Known: ${ev.waystones.size} waystones")
        updateAllWaypoints(ev, 0)
    }

    fun onWaystoneUpdate(ev: WaystoneUpdateReceivedEvent) {
        logger.debug("Update: ${ev.waystone}")
        val waypointMgr = this.waypointManager()
        if (waypointMgr == null) {
            warnOnce("Could not get a waypoint manager")
            return
        }

        val waypointSet = waypointMgr.currentWorld?.currentSet
        if (waypointSet == null) {
            warnOnce("Could not find a world to put waypoints in")
            return
        }

        if (waypointMgr.sameDimensionAs(ev.waystone)) {
            if (waypointSet.updateWaypoint(Waypoint(ev.waystone))) {
                logger.info("Updated waystone \"${ev.waystone.name}\" waypoint")
                XaeroMinimap.instance.settings.saveAllWaypoints(waypointMgr)
            }
        }
    }
}
