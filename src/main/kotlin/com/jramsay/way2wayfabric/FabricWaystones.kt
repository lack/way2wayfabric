package com.jimramsay.way2wayfabric

import net.fabricmc.loader.api.FabricLoader
import wraith.fwaystones.FabricWaystones
import wraith.fwaystones.integration.event.WaystoneEvents
import wraith.fwaystones.util.Utils

fun waystoneFromHash(hash: String?): GenericWaystone?     {
    if (hash == null) return null
    val waystone = FabricWaystones.WAYSTONE_STORAGE.getWaystoneData(hash) ?: return null
    val pos = waystone.way_getPos().up(1)
    val dimension = waystone.worldName.split(':')[1]
    return GenericWaystone(pos, waystone.waystoneName, dimension)
}

object FabricWaystones : IWaystoneProvider {
    override val isPresent: Boolean
        get() = FabricLoader.getInstance().isModLoaded(FabricWaystones.MOD_ID)

    override fun register(handler: IWay2WayHandler) {
        WaystoneEvents.DISCOVER_WAYSTONE_EVENT.register {
            val waystone = waystoneFromHash(it)
            if (waystone != null)
                handler.syncWaystone(waystone)
        }
        WaystoneEvents.RENAME_WAYSTONE_EVENT.register {
            val waystone = waystoneFromHash(it)
            if (waystone != null)
                handler.syncWaystone(waystone)
        }
        WaystoneEvents.REMOVE_WAYSTONE_EVENT.register {
            val waystone = waystoneFromHash(it)
            if (waystone != null)
                handler.removeWaystone(waystone)
        }
        WaystoneEvents.FORGET_ALL_WAYSTONES_EVENT.register {
            handler.removeAllWaystones()
        }
    }
}
