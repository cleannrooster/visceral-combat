package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.networking.Packet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class ServerEventHandlers {

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
            VisceralCombat.clientConfig = VisceralCombat.config
        );

        ServerTickEvents.END_WORLD_TICK.register((ServerWorld world) -> {
            for (PlayerEntity player : world.getPlayers()) {
                if (player.age % 100 == 0
                        && player instanceof ServerPlayerEntity playerEntity
                        && player instanceof HitstopAccessor accessor) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    new Packet.HolsterAssert(accessor.isHolster()).write(buf);
                    ServerPlayNetworking.send(playerEntity, Packet.HolsterAssert.ID, buf);
                }
            }
        });
    }
}
