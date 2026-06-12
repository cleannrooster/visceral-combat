package com.cleannrooster.visceral_combat.networking;

import com.cleannrooster.visceral_combat.VisceralCombat;
import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.config.ConfigSync;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class ClientNetworkHandler {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigSync.PACKET_ID, (payload, context) -> {
            VisceralCombatClient.clientConfig = payload.config();
            VisceralCombat.config = payload.config();
        });

        ClientPlayNetworking.registerGlobalReceiver(Packet.HolsterAssert.PACKET_ID, (payload, context) -> {
            if (context.player() instanceof HitstopAccessor accessor) {
                accessor.setHolster(payload.bool());
            }
        });
    }
}
