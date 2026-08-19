package com.cleannrooster.visceral_combat.forge;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.client.ChargeHudRenderer;
import com.cleannrooster.visceral_combat.client.combat.SlashEffectManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
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
        MinecraftForge.EVENT_BUS.addListener(VisceralCombatClientForge::onRenderGui);
        MinecraftForge.EVENT_BUS.addListener(VisceralCombatClientForge::onRenderLevelStage);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        ChargeHudRenderer.render(event.getGuiGraphics());
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(VisceralCombatClient.holsterBinding);
    }

    /**
     * Loader shim for the shared slash renderer. AFTER_PARTICLES is Forge's counterpart to Fabric's
     * AFTER_TRANSLUCENT, so the arcs land at the same point in the frame on both loaders.
     */
    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            SlashEffectManager.render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
        }
    }
}
