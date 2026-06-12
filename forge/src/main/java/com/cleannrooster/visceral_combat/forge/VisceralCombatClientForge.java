package com.cleannrooster.visceral_combat.forge;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.particle.ModParticles;
import com.cleannrooster.visceral_combat.particle.SlashFlashParticle;
import com.cleannrooster.visceral_combat.particle.SlashGlintParticle;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class VisceralCombatClientForge {

    public static void init(IEventBus modEventBus) {
        VisceralCombatClient.holsterBinding = new KeyBinding(
            "visceral_combat.binds.holster",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "visceral_combat.binds.category"
        );
        VisceralCombatClient.clientInit();

        modEventBus.addListener(VisceralCombatClientForge::registerKeyMappings);
        modEventBus.addListener(VisceralCombatClientForge::registerParticleFactories);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(VisceralCombatClient.holsterBinding);
    }

    private static void registerParticleFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SLASH_FLASH.get(), SlashFlashParticle.Factory::new);
        event.registerSpriteSet(ModParticles.SLASH_GLINT.get(), SlashGlintParticle.Factory::new);
    }
}
