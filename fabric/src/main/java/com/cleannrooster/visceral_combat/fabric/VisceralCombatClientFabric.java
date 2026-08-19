package com.cleannrooster.visceral_combat.fabric;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.client.ChargeHudRenderer;
import com.cleannrooster.visceral_combat.client.combat.SlashEffectManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class VisceralCombatClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(VisceralCombatClient.holsterBinding = new KeyBinding(
            "visceral_combat.binds.holster",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "visceral_combat.binds.category"
        ));

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> ChargeHudRenderer.render(drawContext));

        // Loader shim for the shared slash renderer. Drawn after translucent terrain and particles so the
        // arcs blend over the world; all the geometry lives in common.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
            SlashEffectManager.render(context.matrixStack(), context.camera(), context.tickDelta()));

        VisceralCombatClient.clientInit();
    }
}
