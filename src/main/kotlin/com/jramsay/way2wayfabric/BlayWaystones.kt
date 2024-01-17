package com.jimramsay.way2wayfabric

import net.blay09.mods.balm.api.Balm
import net.blay09.mods.waystones.Waystones
import net.blay09.mods.waystones.api.Waystone
import net.blay09.mods.waystones.api.event.WaystoneUpdateReceivedEvent
import net.blay09.mods.waystones.api.event.WaystonesListReceivedEvent
import net.fabricmc.loader.api.FabricLoader

fun Waystone.generic(modIdx: Int): GenericWaystone {
    val pos = this.pos.above(2)
    return GenericWaystone(pos, name.string, dimension.location().path, modIdx)
}

object BlayWaystones : IWaystoneProvider {
    override val isPresent: Boolean
        get() = FabricLoader.getInstance().isModLoaded(Waystones.MOD_ID)

    override var modIdx: Int = -1

    override fun register(handler: IWay2WayHandler) {
        Balm.getEvents().onEvent(WaystonesListReceivedEvent::class.java, {
            handler.syncAllWaystones(it.waystones.map { it.generic(modIdx) }, modIdx)
        })
        Balm.getEvents().onEvent(WaystoneUpdateReceivedEvent::class.java, {
            handler.syncWaystone(it.waystone.generic(modIdx))
        })
    }
}
