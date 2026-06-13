package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.networking.Packet;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.server.network.ServerPlayerEntity;

public class ServerEventHandlers {

    public static void register() {
        LifecycleEvent.SERVER_STARTING.register(server ->
            VisceralCombat.clientConfig = VisceralCombat.config
        );

        TickEvent.SERVER_POST.register(server -> {
            for (var world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    if (player.age % 100 == 0 && player instanceof HitstopAccessor accessor) {
                        NetworkManager.sendToPlayer(player, new Packet.HolsterAssert(accessor.isHolster()));
                    }
                }
            }
        });
    }
}
