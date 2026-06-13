package com.cleannrooster.visceral_combat.neoforge;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.particle.ModParticles;
import com.cleannrooster.visceral_combat.particle.SlashFlashParticle;
import com.cleannrooster.visceral_combat.particle.SlashGlintParticle;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class VisceralCombatClientNeoForge {
    public static void init(IEventBus modEventBus) {
        VisceralCombatClient.holsterBinding = new KeyBinding(
            "visceral_combat.binds.holster",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "visceral_combat.binds.category"
        );
        VisceralCombatClient.clientInit();
        modEventBus.addListener(VisceralCombatClientNeoForge::registerKeyMappings);
        modEventBus.addListener(VisceralCombatClientNeoForge::registerParticleFactories);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(VisceralCombatClient.holsterBinding);
    }

    private static void registerParticleFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SLASH_FLASH.get(), SlashFlashParticle.Factory::new);
        event.registerSpriteSet(ModParticles.SLASH_GLINT.get(), SlashGlintParticle.Factory::new);
    }
}
