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
import xaero.minimap.XaeroMinimap
import net.minecraft.client.MinecraftClient
import org.slf4j.LoggerFactory

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
    val color = (Math.random() * ModSettings.ENCHANT_COLORS.size).toInt()
    return Waypoint(pos.x, pos.y, pos.z, waystone.name, waystone.name.substring(0, 1), color, 0, false)
}

fun Waypoint.matches(that: Waypoint): Boolean {
    return this.x == that.x && this.y == that.y && this.z == that.z
}

fun WaypointSet.updateWaypoint(new: Waypoint): Boolean {
    logger.debug("Updating waypoint ${new.name}")
    val existing = this.list.find{it.matches(new)}
    if (existing == null) {
        logger.info("Adding new waypoint for ${new.name}")
        this.list.add(new)
        return true
    }
    if (existing.name != new.name) {
        logger.info("Found existing waypoint -> Updating name from ${existing.name} to ${new.name}")
        existing.name = new.name
        existing.symbol = new.symbol
        return true
    }
    return false
}

@Suppress("UNUSED")
object Way2WayFabric: ModInitializer {
    private const val MOD_ID = "way2way_fabric"

    override fun onInitialize() {
        Balm.getEvents().onEvent(KnownWaystonesEvent::class.java, this::onKnownWaystones)
        Balm.getEvents().onEvent(WaystoneUpdateReceivedEvent::class.java, this::onWaystoneUpdate)
        logger.info("Way2wayFabric has been initialized.")
    }

    fun waypointManager() : WaypointsManager? {
        val connection = MinecraftClient.getInstance().networkHandler
        val handler = connection as? IXaeroMinimapClientPlayNetHandler
        return handler?.xaero_minimapSession?.waypointsManager
    }

    fun onKnownWaystones(ev: KnownWaystonesEvent) {
        logger.debug("Known: ${ev.waystones.size} waystones")

        val waypointMgr = this.waypointManager()
        if (waypointMgr == null) {
            logger.debug("Could not get a waypoint manager")
            return
        }

        val waypointSet = waypointMgr.currentWorld?.currentSet
        if (waypointSet == null) {
            logger.debug("Could not find a world to put waypoints in")
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

    fun onWaystoneUpdate(ev: WaystoneUpdateReceivedEvent) {
        logger.debug("Update: ${ev.waystone}")
        val waypointMgr = this.waypointManager()
        if (waypointMgr == null) {
            logger.debug("Could not get a waypoint manager")
            return
        }

        val waypointSet = waypointMgr.currentWorld?.currentSet
        if (waypointSet == null) {
            logger.debug("Could not find a world to put waypoints in")
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
