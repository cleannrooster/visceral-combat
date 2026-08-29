package com.cleannrooster.visceral_combat.client.targeting;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.config.TargetAssistMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws the acquisition highlight: a thin, faintly pulsing ring on the ground under the enemy the
 * player is considered to intend to attack.
 *
 * <p>The visual is deliberately quiet. It answers exactly one question — "if I click now, which enemy
 * does Visceral Combat think I mean?" — and must not read as a lock-on: no floating marker, no camera
 * involvement, no persistence once the crosshair moves away. A ground ring was chosen over an entity
 * outline because it marks a spot without decorating the enemy, and because it needs no access to the
 * entity rendering pipeline (glow flags are shared entity state and not safely writable client-side).
 *
 * <p>Untextured additive geometry ({@code RenderLayer.getLightning()}), matching the warm off-white of
 * the slash ribbons so the two read as one system. Alpha fades to zero at both edges of the band, so
 * the ring has no hard outline anywhere.
 */
@Environment(EnvType.CLIENT)
public final class TargetHighlightRenderer {

    private static final int SEGMENTS = 40;
    /** Radial width of the ring band, blocks. */
    private static final float BAND_WIDTH = 0.14f;
    /** Lift above the entity's feet, so the ring doesn't z-fight the ground it sits on. */
    private static final double GROUND_OFFSET = 0.04;

    /** Same warm off-white as the slash ribbons. */
    private static final float RED = 1.0f;
    private static final float GREEN = 0.97f;
    private static final float BLUE = 0.90f;
    /** Peak alpha at the band's centreline: visible, but never louder than the world. */
    private static final float BASE_ALPHA = 0.30f;
    /** Slow pulse so the ring reads as live state rather than a decal. Subtle by design. */
    private static final float PULSE_AMPLITUDE = 0.20f;
    private static final float PULSE_PERIOD_TICKS = 30.0f;

    /** Hit-flash hue and drain time, matching the slash ribbon's (see {@code SlashRenderer} /
     * {@code SlashEffect}) so ring and ribbon confirm the same hit in the same voice. */
    private static final float FLASH_RED = 1.0f;
    private static final float FLASH_GREEN = 0.12f;
    private static final float FLASH_BLUE = 0.08f;
    private static final float FLASH_TICKS = 3.0f;
    /** The flash also lifts the ring's alpha, overriding the idle pulse: a hit reads as one clean
     * snap, not a brighter shade of the ambient shimmer. */
    private static final float FLASH_ALPHA = 0.55f;

    /** Entity id whose marker is flashing, and the world tick the flash starts draining from. */
    private static int flashedEntityId = -1;
    private static long flashStartTime;

    /** Own immediate buffer, same reasoning as the slash ribbons: the shared entity source is
     * already mid-batch during world-render callbacks. The ring is a few hundred vertices. */
    private static final VertexConsumerProvider.Immediate BUFFERS =
        VertexConsumerProvider.immediate(new BufferAllocator(16 * 1024));

    private TargetHighlightRenderer() {
    }

    /**
     * Flash the marker red if the enemy it sits under is among the entities a swing just connected
     * with. Local hit feedback only, exactly like the ribbon's flash: the server hears nothing, and a
     * hit on some <em>other</em> enemy (a sweep clipping a bystander) leaves the marker unmoved —
     * the flash confirms "the enemy you meant was hit", not "something was hit".
     *
     * @param hitEntities the entities Better Combat's {@code ATTACK_HIT} reported
     * @param now         the world tick the hit resolved on
     */
    public static void flashHit(List<Entity> hitEntities, long now) {
        LivingEntity target = TargetAcquisition.acquiredTarget();
        if (target != null && hitEntities.contains(target)) {
            flashedEntityId = target.getId();
            // One tick ahead, same as the ribbon's markHit: the world clock has already advanced to
            // the tick this hit resolved in, so starting the drain one tick later keeps the first
            // rendered frames at full strength instead of already partly drained.
            flashStartTime = now + 1;
        }
    }

    /** Draw the ring under the acquired target, if any. Called from each loader's world-render hook. */
    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        var config = VisceralCombatClient.clientConfig;
        LivingEntity target = TargetAcquisition.acquiredTarget();
        if (config == null || !config.assistHighlight
                || config.targetAssistMode == TargetAssistMode.OFF || target == null) {
            return;
        }

        Vec3d position = target.getLerpedPos(tickDelta);
        float innerRadius = target.getWidth() * 0.75f + 0.25f;
        float age = target.age + tickDelta;
        float pulse = 1.0f - PULSE_AMPLITUDE * 0.5f
            * (1.0f + MathHelper.sin(age * (MathHelper.TAU / PULSE_PERIOD_TICKS)));

        // Hit feedback: snap to the ribbon's red and drain back to the idle look over FLASH_TICKS.
        float flash = flashStrength(target, tickDelta);
        float red = MathHelper.lerp(flash, RED, FLASH_RED);
        float green = MathHelper.lerp(flash, GREEN, FLASH_GREEN);
        float blue = MathHelper.lerp(flash, BLUE, FLASH_BLUE);
        float alpha = MathHelper.lerp(flash, BASE_ALPHA * pulse, FLASH_ALPHA);

        Vec3d cameraPos = camera.getPos();
        matrices.push();
        matrices.translate(position.x - cameraPos.x, position.y - cameraPos.y + GROUND_OFFSET,
            position.z - cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = BUFFERS.getBuffer(RenderLayer.getLightning());

        // Two concentric bands of quads: transparent inner edge -> bright centreline -> transparent
        // outer edge. Both faces of each quad via reversed winding, so the ring survives odd camera
        // angles (looking up at a ledge-standing target from below).
        float mid = innerRadius + BAND_WIDTH * 0.5f;
        float outer = innerRadius + BAND_WIDTH;
        for (int i = 0; i < SEGMENTS; i++) {
            float angle0 = (float) i / SEGMENTS * MathHelper.TAU;
            float angle1 = (float) (i + 1) / SEGMENTS * MathHelper.TAU;
            band(consumer, matrix, angle0, angle1, innerRadius, mid, 0.0f, alpha, red, green, blue);
            band(consumer, matrix, angle0, angle1, mid, outer, alpha, 0.0f, red, green, blue);
        }

        BUFFERS.draw();
        matrices.pop();
    }

    /**
     * Strength of the hit flash on {@code target}'s marker right now: 1 at impact, draining to 0 over
     * {@link #FLASH_TICKS}. Zero for any entity other than the one whose hit was recorded, so a flash
     * can never carry over to a different enemy the marker moves to.
     */
    private static float flashStrength(LivingEntity target, float tickDelta) {
        if (target.getId() != flashedEntityId) {
            return 0.0f;
        }
        float elapsed = Math.max(0.0f, target.getWorld().getTime() + tickDelta - flashStartTime);
        return MathHelper.clamp(1.0f - elapsed / FLASH_TICKS, 0.0f, 1.0f);
    }

    /** One segment of an annular band, with per-edge alpha. Emitted double-sided. */
    private static void band(VertexConsumer consumer, Matrix4f matrix, float angle0, float angle1,
                             float radiusA, float radiusB, float alphaA, float alphaB,
                             float red, float green, float blue) {
        float cos0 = MathHelper.cos(angle0);
        float sin0 = MathHelper.sin(angle0);
        float cos1 = MathHelper.cos(angle1);
        float sin1 = MathHelper.sin(angle1);
        vertex(consumer, matrix, cos0 * radiusA, sin0 * radiusA, alphaA, red, green, blue);
        vertex(consumer, matrix, cos1 * radiusA, sin1 * radiusA, alphaA, red, green, blue);
        vertex(consumer, matrix, cos1 * radiusB, sin1 * radiusB, alphaB, red, green, blue);
        vertex(consumer, matrix, cos0 * radiusB, sin0 * radiusB, alphaB, red, green, blue);
        // Reverse winding: visible from below as well.
        vertex(consumer, matrix, cos0 * radiusB, sin0 * radiusB, alphaB, red, green, blue);
        vertex(consumer, matrix, cos1 * radiusB, sin1 * radiusB, alphaB, red, green, blue);
        vertex(consumer, matrix, cos1 * radiusA, sin1 * radiusA, alphaA, red, green, blue);
        vertex(consumer, matrix, cos0 * radiusA, sin0 * radiusA, alphaA, red, green, blue);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float z, float alpha,
                               float red, float green, float blue) {
        consumer.vertex(matrix, x, 0.0f, z).color(red, green, blue, alpha);
    }
}
