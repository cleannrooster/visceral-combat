package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.config.ConfigSync;
import com.cleannrooster.visceral_combat.networking.Packet;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

public class ServerEventHandlers {

    public static void register() {
        TickEvent.SERVER_POST.register(server -> {
            for (var world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    if (player.age % 100 == 0 && player instanceof HitstopAccessor accessor) {
                        var buf = new PacketByteBuf(Unpooled.buffer());
                        new Packet.HolsterAssert(accessor.isHolster()).write(buf);
                        NetworkManager.sendToPlayer(player, Packet.HolsterAssert.ID, buf);
                        // Periodically re-push the authoritative config so any client that missed the
                        // join-time sync self-heals. Must use the ConfigSync channel, not HolsterAssert.
                        NetworkManager.sendToPlayer(player, ConfigSync.ID, ConfigSync.write(VisceralCombat.config));
                    }
                }
            }
        });
    }
}
