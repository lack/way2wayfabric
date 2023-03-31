package com.jimramsay.way2wayfabric

import net.fabricmc.loader.api.FabricLoader
import net.blay09.mods.balm.api.Balm
import net.blay09.mods.waystones.Waystones
import net.blay09.mods.waystones.api.IWaystone
import net.blay09.mods.waystones.api.KnownWaystonesEvent
import net.blay09.mods.waystones.api.WaystoneUpdateReceivedEvent

fun IWaystone.generic(): GenericWaystone {
    val pos = this.pos.up(2)
    return GenericWaystone(pos, name, dimension.value.path)
}

object BlayWaystones : IWaystoneProvider{
    override val isPresent: Boolean
        get() = FabricLoader.getInstance().isModLoaded(Waystones.MOD_ID)

    override fun register(handler: IWay2WayHandler) {
        Balm.getEvents().onEvent(KnownWaystonesEvent::class.java, {
            handler.syncAllWaystones(it.waystones.map { it.generic() })
        })
        Balm.getEvents().onEvent(WaystoneUpdateReceivedEvent::class.java, {
            handler.syncWaystone(it.waystone.generic())
        })
    }
}
