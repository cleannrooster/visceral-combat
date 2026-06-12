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
        ClientPlayNetworking.registerGlobalReceiver(ConfigSync.ID, (client, handler, buf, responseSender) -> {
            var config = ConfigSync.read(buf);
            VisceralCombatClient.clientConfig = config;
            VisceralCombat.config = config;
        });

        ClientPlayNetworking.registerGlobalReceiver(Packet.HolsterAssert.ID, (client, handler, buf, responseSender) -> {
            var payload = Packet.HolsterAssert.read(buf);
            if (client.player instanceof HitstopAccessor accessor) {
                accessor.setHolster(payload.bool());
            }
        });
    }
}
