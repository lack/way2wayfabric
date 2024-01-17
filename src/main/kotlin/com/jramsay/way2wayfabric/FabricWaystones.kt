package com.jimramsay.way2wayfabric

import net.fabricmc.loader.api.FabricLoader
import wraith.fwaystones.FabricWaystones
import wraith.fwaystones.integration.event.WaystoneEvents

object FabricWaystones : IWaystoneProvider {
    override val isPresent: Boolean
        get() = FabricLoader.getInstance().isModLoaded(FabricWaystones.MOD_ID)

    override var modIdx: Int = -1

    override fun register(handler: IWay2WayHandler) {
        WaystoneEvents.DISCOVER_WAYSTONE_EVENT.register {
            val waystone = waystoneFromHash(it)
            if (waystone != null) {
                handler.syncWaystone(waystone)
            }
        }
        WaystoneEvents.RENAME_WAYSTONE_EVENT.register {
            val waystone = waystoneFromHash(it)
            if (waystone != null) {
                handler.syncWaystone(waystone)
            }
        }
        WaystoneEvents.REMOVE_WAYSTONE_EVENT.register {
            val waystone = waystoneFromHash(it)
            if (waystone != null) {
                handler.removeWaystone(waystone)
            }
        }
        WaystoneEvents.FORGET_ALL_WAYSTONES_EVENT.register {
            handler.removeAllWaystones(modIdx)
        }
    }

    fun waystoneFromHash(hash: String?): GenericWaystone? {
        if (hash == null) return null
        val waystone = FabricWaystones.WAYSTONE_STORAGE.getWaystoneData(hash) ?: return null
        val pos = waystone.way_getPos().above(1)
        val dimension = waystone.worldName.split(':')[1]
        return GenericWaystone(pos, waystone.waystoneName, dimension, modIdx)
    }
}
