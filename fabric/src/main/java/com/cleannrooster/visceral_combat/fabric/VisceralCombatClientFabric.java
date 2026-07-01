package com.cleannrooster.visceral_combat.fabric;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.client.ChargeHudRenderer;
import com.cleannrooster.visceral_combat.particle.ModParticles;
import com.cleannrooster.visceral_combat.particle.SlashFlashParticle;
import com.cleannrooster.visceral_combat.particle.SlashGlintParticle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class VisceralCombatClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(ModParticles.SLASH_FLASH.get(), SlashFlashParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.SLASH_GLINT.get(), SlashGlintParticle.Factory::new);

        KeyBindingHelper.registerKeyBinding(VisceralCombatClient.holsterBinding = new KeyBinding(
            "visceral_combat.binds.holster",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "visceral_combat.binds.category"
        ));

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> ChargeHudRenderer.render(drawContext));

        VisceralCombatClient.clientInit();
    }
}
