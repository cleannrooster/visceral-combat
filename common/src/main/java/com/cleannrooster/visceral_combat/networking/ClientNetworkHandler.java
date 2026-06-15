package com.cleannrooster.visceral_combat.networking;

import com.cleannrooster.visceral_combat.VisceralCombat;
import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.config.ConfigSync;
import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ClientNetworkHandler {

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ConfigSync.PACKET_ID, ConfigSync.CODEC,
            (payload, context) -> context.queue(() -> {
                VisceralCombatClient.clientConfig = payload.config();
                VisceralCombat.config = payload.config();
            })
        );

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, Packet.HolsterAssert.PACKET_ID, Packet.HolsterAssert.CODEC,
            (payload, context) -> context.queue(() -> {
                if (context.getPlayer() instanceof HitstopAccessor accessor) {
                    accessor.setHolster(payload.bool());
                }
            })
        );

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, Packet.LungeAck.PACKET_ID, Packet.LungeAck.CODEC,
            (payload, context) -> context.queue(() -> VisceralCombatClient.lungePending = false)
        );
    }
}
