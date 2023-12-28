package com.jimramsay.way2wayfabric

import net.blay09.mods.balm.api.Balm
import net.blay09.mods.waystones.Waystones
import net.blay09.mods.waystones.api.IWaystone
import net.blay09.mods.waystones.api.KnownWaystonesEvent
import net.blay09.mods.waystones.api.WaystoneUpdateReceivedEvent
import net.fabricmc.loader.api.FabricLoader

fun IWaystone.generic(modIdx: Int): GenericWaystone {
    val pos = this.pos.up(2)
    return GenericWaystone(pos, name, dimension.value.path, modIdx)
}

object BlayWaystones : IWaystoneProvider {
    override val isPresent: Boolean
        get() = FabricLoader.getInstance().isModLoaded(Waystones.MOD_ID)

    override var modIdx: Int = -1

    override fun register(handler: IWay2WayHandler) {
        Balm.getEvents().onEvent(KnownWaystonesEvent::class.java, {
            handler.syncAllWaystones(it.waystones.map { it.generic(modIdx) }, modIdx)
        })
        Balm.getEvents().onEvent(WaystoneUpdateReceivedEvent::class.java, {
            handler.syncWaystone(it.waystone.generic(modIdx))
        })
    }
}
