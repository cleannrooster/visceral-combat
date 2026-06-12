package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.client.CombatEventsClient;
import com.cleannrooster.visceral_combat.config.ServerConfig;
import com.cleannrooster.visceral_combat.networking.ClientNetworkHandler;
import com.cleannrooster.visceral_combat.networking.Packet;
import com.cleannrooster.visceral_combat.particle.ModParticles;
import com.cleannrooster.visceral_combat.particle.SlashFlashParticle;
import com.cleannrooster.visceral_combat.particle.SlashGlintParticle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class VisceralCombatClient implements ClientModInitializer {
    public static ServerConfig clientConfig;
    public static KeyBinding holsterBinding;

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(ModParticles.SLASH_FLASH, SlashFlashParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.SLASH_GLINT, SlashGlintParticle.Factory::new);

        KeyBindingHelper.registerKeyBinding(holsterBinding = new KeyBinding(
            "visceral_combat.binds.holster",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "visceral_combat.binds.category"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (holsterBinding.wasPressed() && client.player instanceof HitstopAccessor accessor) {
                accessor.setHolster(!accessor.isHolster());
                PacketByteBuf buf = PacketByteBufs.create();
                new Packet.Holster(false).write(buf);
                ClientPlayNetworking.send(Packet.Holster.ID, buf);
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
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
