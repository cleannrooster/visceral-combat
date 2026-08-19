package com.cleannrooster.visceral_combat.networking;

import com.cleannrooster.visceral_combat.VisceralCombat;
import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.client.combat.SlashEffectManager;
import com.cleannrooster.visceral_combat.config.ConfigSync;
import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.PacketByteBuf;

@Environment(EnvType.CLIENT)
public class ClientNetworkHandler {

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ConfigSync.ID, (buf, context) -> {
            var config = ConfigSync.read(buf);
            context.queue(() -> {
                VisceralCombatClient.clientConfig = config;
                VisceralCombat.config = config;
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, Packet.HolsterAssert.ID, (buf, context) -> {
            Packet.HolsterAssert payload = Packet.HolsterAssert.read(buf);
            context.queue(() -> {
                if (context.getPlayer() instanceof HitstopAccessor accessor) {
                    accessor.setHolster(payload.bool());
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, Packet.LungeAck.ID, (buf, context) -> {
            Packet.LungeAck.read(buf);
            context.queue(() -> VisceralCombatClient.lungePending = false);
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, Packet.Swing.S2C_ID, (buf, context) -> {
            Packet.Swing payload = Packet.Swing.read(buf);
            // Someone else swung: draw the same ribbon over the same volume they see.
            context.queue(() -> SlashEffectManager.spawn(payload.attackerId(), payload.swing()));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, Packet.ChargeSync.ID, (buf, context) -> {
            Packet.ChargeSync payload = Packet.ChargeSync.read(buf);
            // Snap the predicted HUD state to the server's authoritative ledger.
            context.queue(() -> VisceralCombatClient.charges.setState(payload.startTick(), payload.readyTick()));
        });
    }
}
