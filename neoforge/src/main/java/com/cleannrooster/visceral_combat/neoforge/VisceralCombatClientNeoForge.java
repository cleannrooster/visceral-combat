package com.cleannrooster.visceral_combat.neoforge;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.client.ChargeHudRenderer;
import com.cleannrooster.visceral_combat.client.combat.SlashEffectManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
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
        NeoForge.EVENT_BUS.addListener(VisceralCombatClientNeoForge::onRenderGui);
        NeoForge.EVENT_BUS.addListener(VisceralCombatClientNeoForge::onRenderLevelStage);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        ChargeHudRenderer.render(event.getGuiGraphics());
    }

    /**
     * Loader shim for the shared slash renderer. AFTER_PARTICLES is NeoForge's counterpart to Fabric's
     * AFTER_TRANSLUCENT, so the arcs land at the same point in the frame on both loaders.
     */
    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            SlashEffectManager.render(event.getPoseStack(), event.getCamera(),
                event.getPartialTick().getTickDelta(false));
        }
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(VisceralCombatClient.holsterBinding);
    }
}
