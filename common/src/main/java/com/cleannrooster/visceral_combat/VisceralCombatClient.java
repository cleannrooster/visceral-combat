package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.client.CombatEventsClient;
import com.cleannrooster.visceral_combat.config.ServerConfig;
import com.cleannrooster.visceral_combat.networking.ClientNetworkHandler;
import com.cleannrooster.visceral_combat.networking.Packet;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class VisceralCombatClient {
    public static ServerConfig clientConfig;
    public static KeyBinding holsterBinding;
    public static boolean lungePending = false;
    public static long lungeExpiry = 0L;

    public static void clientInit() {
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (lungePending && client.world != null && client.world.getTime() > lungeExpiry) {
                lungePending = false;
            }
            if (holsterBinding != null && holsterBinding.wasPressed()
                    && client.player instanceof HitstopAccessor accessor) {
                accessor.setHolster(!accessor.isHolster());
                NetworkManager.sendToServer(new Packet.Holster(false));
            }
        });

        ClientTickEvent.CLIENT_PRE.register(client -> {
            if (client.player == null) return;
            if (client.player instanceof HitstopAccessor hitstopAccessor
                    && hitstopAccessor.isHolster()
                    && client.options.attackKey.isPressed()) {
                client.player.sendMessage(
                    Text.translatable("text.holster.error").append(holsterBinding.getDefaultKey().getLocalizedText()), true
                );
            }
        });

        ClientNetworkHandler.register();
        CombatEventsClient.register();
    }
}
