package com.cleannrooster.visceral_combat.client;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Draws the lunge-charge meter: a centered row of small horizontal bars sitting immediately underneath
 * the crosshair, one per configured charge slot. Each bar tracks its slot's recovery, filling
 * left-to-right from empty (just spent) to full (ready). A bar that has been full for {@link
 * #FULL_HOLD_TICKS} fades out over {@link #FADE_TICKS}, so idle full bars disappear individually. The
 * row stays centered regardless of how many charges are configured. Loader-agnostic: Fabric calls this
 * from a HudRenderCallback and NeoForge from a RenderGuiEvent.Post, both handing over the same DrawContext.
 */
@Environment(EnvType.CLIENT)
public class ChargeHudRenderer {

    // Each bar is a small horizontal strip; a row of them sits just below the 15px vanilla crosshair.
    private static final int BAR_WIDTH = 10;
    private static final int BAR_HEIGHT = 3;
    private static final int BAR_GAP = 2;
    private static final int GAP_BELOW_CROSSHAIR = 3; // pixels between crosshair bottom and the bars

    // A full bar stays visible this long, then fades out over the following window.
    private static final int FULL_HOLD_TICKS = 40; // 2 seconds
    private static final int FADE_TICKS = 20;      // 1 second fade-out

    // Same color as the crosshair (white) once ready; a darker shade while still filling. A dim
    // translucent track shows the un-recovered remainder.
    private static final int FILL_COLOR = 0xFFFFFFFF;
    private static final int FILL_COLOR_RECOVERING = 0xFF808080;
    private static final int TRACK_COLOR = 0x66000000;

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        // Nothing to show when the charge system is off (or config not synced yet).
        var config = VisceralCombatClient.clientConfig;
        if (config == null || !config.chargesEnabled) return;
        // Only when the crosshair itself would show.
        if (!client.options.getPerspective().isFirstPerson()) return;

        int count = VisceralCombatClient.maxCharges();
        if (count <= 0) return;

        long now = client.player.getWorld().getTime();

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        // Center the whole row under the crosshair for any charge count.
        int totalWidth = count * BAR_WIDTH + (count - 1) * BAR_GAP;
        int startX = centerX - totalWidth / 2;
        // The crosshair is a 15px icon centered on centerY, so its bottom edge is ~centerY + 7.
        int top = centerY + 8 + 8 + GAP_BELOW_CROSSHAIR;

        for (int i = 0; i < count; i++) {
            float alpha = VisceralCombatClient.chargeAlpha(i, now, FULL_HOLD_TICKS, FADE_TICKS);
            if (alpha <= 0f) continue; // fully faded: skip
            int barX = startX + i * (BAR_WIDTH + BAR_GAP);
            // Track (full width, dim), then the recovered portion (white) on top.
            context.fill(barX, top, barX + BAR_WIDTH, top + BAR_HEIGHT, withAlpha(TRACK_COLOR, alpha));
            float progress = VisceralCombatClient.chargeProgress(i, now);
            int fillWidth = Math.round(BAR_WIDTH * progress);
            if (fillWidth > 0) {
                // Darker while still filling, full crosshair-white once ready.
                int fillColor = progress >= 1.0f ? FILL_COLOR : FILL_COLOR_RECOVERING;
                context.fill(barX, top, barX + fillWidth, top + BAR_HEIGHT, withAlpha(fillColor, alpha));
            }
        }
    }

    /** Scale an ARGB color's alpha channel by {@code alphaMul} in [0, 1]. */
    private static int withAlpha(int argb, float alphaMul) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int a = Math.round(baseAlpha * alphaMul);
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
