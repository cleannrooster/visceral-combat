package com.cleannrooster.visceral_combat.client.combat;

import com.cleannrooster.visceral_combat.combat.AttackFrame;
import com.cleannrooster.visceral_combat.combat.AttackGeometry;
import com.cleannrooster.visceral_combat.combat.AttackSwing;
import com.cleannrooster.visceral_combat.combat.SlashProfile;
import com.cleannrooster.visceral_combat.combat.SlashShape;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Draws an attack's swept surface as a textured ribbon.
 *
 * <p>The mesh is generated entirely from {@link AttackGeometry} — the same description of the volume
 * Better Combat tests against when deciding whether you were hit. A horizontal sweep therefore renders
 * as a horizontal crescent of exactly its real reach and arc, a vertical slash as a vertical arc, a
 * thrust as a lane the length of the weapon's actual reach. Nothing here is tuned per weapon; a new
 * weapon gets a correct visual by existing.
 *
 * <p>The ribbon is also animated through the swing rather than shown whole: the leading edge advances
 * with the attack's progress toward the moment the hit resolves, so the arc is drawn being cut.
 */
@Environment(EnvType.CLIENT)
public final class SlashRenderer {

    /** Emissive slashes are drawn fullbright so they read at night and in caves. */
    private static final int FULL_BRIGHT = LightmapTextureManager.pack(15, 15);

    /** Radial subdivisions of the outer bloom. Few: it is a gradient, not a shape. */
    private static final int OVERREACH_SEGMENTS = 3;

    /** The color a connecting swing snaps to before draining back to the profile's own tint. */
    private static final float FLASH_RED = 1.0f;
    private static final float FLASH_GREEN = 0.12f;
    private static final float FLASH_BLUE = 0.08f;

    private SlashRenderer() {
    }

    static void render(SlashEffect effect, MatrixStack matrices, VertexConsumerProvider buffers,
                       float tickDelta) {
        SlashProfile profile = effect.profile();
        if (!profile.isVisible()) {
            return;
        }
        float alpha = profile.alpha() * effect.fade(tickDelta);
        if (alpha <= 0.01f) {
            return;
        }

        AttackSwing swing = effect.swing();
        AttackFrame frame = effect.resolveFrame(tickDelta);
        Ribbon grid = buildGrid(effect, swing, frame, tickDelta);
        if (grid == null) {
            return;
        }

        RenderLayer layer = profile.emissive()
            ? RenderLayer.getEntityTranslucentEmissive(profile.texture())
            : RenderLayer.getEntityTranslucent(profile.texture());
        VertexConsumer consumer = buffers.getBuffer(layer);

        Vec3d normal = AttackGeometry.planeNormal(swing, frame);
        // Thickness is clamped to the attack's real half-extent, so however solid the blade is made to
        // look it can never occupy space the damage volume does not.
        float thickness = (float) Math.min(profile.thickness(), AttackGeometry.halfThickness(swing));

        // Hit feedback: a swing that connected snaps to red and drains back to the profile's tint.
        // Purely local state — the flash exists only on the attacker's own client, so unlike the
        // geometry it never has to be validated. It also overrides the leading-edge alpha ramp below,
        // so the whole visible arc flashes as one confirmed shape instead of flickering at the tip.
        float flash = effect.flashStrength(tickDelta);
        float red = MathHelper.lerp(flash, profile.red(), FLASH_RED);
        float green = MathHelper.lerp(flash, profile.green(), FLASH_GREEN);
        float blue = MathHelper.lerp(flash, profile.blue(), FLASH_BLUE);

        MatrixStack.Entry entry = matrices.peek();

        // The bloom goes down first, underneath the real arc.
        //
        // This is the only geometry drawn outside the damage volume, and it is drawn in a deliberately
        // different register so it cannot be mistaken for the volume: a fraction of the alpha, fading to
        // nothing at its outer limit, and with no thickness of its own. Drawing it first also means it
        // can never occlude the honest shape — the arc's own edge stays the brightest thing on screen,
        // which is what the player actually reads reach from.
        if (profile.hasOverreach()) {
            Ribbon bloom = buildOverreach(effect, swing, frame, tickDelta);
            if (bloom != null) {
                // The bloom shares the flash hue but never its alpha punch: it marks space the attack
                // cannot hit, and a hit must not brighten it.
                emit(consumer, entry, bloom, red, green, blue,
                    alpha * SlashProfile.OVERREACH_ALPHA, 0.0f, normal);
            }
        }

        if (thickness > 0.001f) {
            // Two parallel sheets read as a slab with real presence, and the slab is the hitbox's own
            // thickness rather than a decorative one.
            emit(consumer, entry, offsetGrid(grid, normal, thickness), red, green, blue, alpha, flash, normal);
            emit(consumer, entry, offsetGrid(grid, normal, -thickness), red, green, blue, alpha, flash,
                normal.multiply(-1.0));
        } else {
            emit(consumer, entry, grid, red, green, blue, alpha, flash, normal);
        }
    }

    /**
     * The ribbon as a grid of world-space vertices plus a per-row brightness ramp.
     *
     * @param points   {@code [row][column]}; rows run along the long axis of the attack, columns across
     * @param rowAlpha per-row opacity — the trailing end of a swing fades out behind the leading edge
     * @param colAlpha per-column opacity, or null for fully opaque across the blade. Only the outer
     *                 bloom uses it, to dissolve as it projects past the real reach
     */
    private record Ribbon(Vec3d[][] points, float[] rowAlpha, float @Nullable [] colAlpha) {

        Ribbon(Vec3d[][] points, float[] rowAlpha) {
            this(points, rowAlpha, null);
        }

        float alphaAt(int row, int column) {
            float across = this.colAlpha == null ? 1.0f : this.colAlpha[column];
            return this.rowAlpha[row] * across;
        }
    }

    private static @Nullable Ribbon buildGrid(SlashEffect effect, AttackSwing swing, AttackFrame frame,
                                              float tickDelta) {
        return swing.shape() == SlashShape.THRUST
            ? buildLane(effect, swing, frame, tickDelta)
            : buildArc(effect, swing, frame, tickDelta);
    }

    /**
     * A crescent: rows step through the swept angles, columns step from the inner radius out to the tip.
     * Leaving the innermost fraction empty is what turns a pie wedge into a blade — and it errs inward,
     * so the drawn shape stays inside the volume rather than overstating it.
     */
    private static @Nullable Ribbon buildArc(SlashEffect effect, AttackSwing swing, AttackFrame frame,
                                             float tickDelta) {
        SlashProfile profile = effect.profile();
        float lead = effect.leadingEdge(tickDelta);
        float tail = effect.trailingEdge(tickDelta);
        if (lead - tail < 1.0e-4f) {
            return null;
        }

        int rows = Math.max(2, profile.sweepSegments());
        int cols = Math.max(1, profile.bladeSegments());
        float inner = effect.innerFraction();
        Vec3d[][] points = new Vec3d[rows + 1][cols + 1];
        float[] rowAlpha = new float[rows + 1];

        for (int r = 0; r <= rows; r++) {
            float rowFraction = (float) r / rows;
            float s = MathHelper.lerp(rowFraction, tail, lead);
            // Brightest at the leading edge, dissolving toward the tail of the trail.
            rowAlpha[r] = rowFraction * rowFraction * (3.0f - 2.0f * rowFraction);
            for (int c = 0; c <= cols; c++) {
                float t = MathHelper.lerp((float) c / cols, inner, 1.0f);
                points[r][c] = AttackGeometry.surfacePoint(swing, frame, s, t);
            }
        }
        return new Ribbon(points, rowAlpha);
    }

    /**
     * A thrust: rows step along the lane, columns across its width. The lane only extends as far as the
     * swing has progressed, so a thrust visibly reaches out.
     *
     * <p>The width at each step is the narrower of the hitbox's own half-width and the attack's cone at
     * that distance, because near the attacker the box's corners lie outside the angle the radial filter
     * will accept. Taking the narrower of the two is what makes the drawn lane a spearhead that fits
     * inside what can actually be hit.
     */
    private static @Nullable Ribbon buildLane(SlashEffect effect, AttackSwing swing, AttackFrame frame,
                                              float tickDelta) {
        SlashProfile profile = effect.profile();
        float extension = effect.leadingEdge(tickDelta);
        if (extension <= 1.0e-4f) {
            return null;
        }

        int rows = Math.max(2, profile.bladeSegments());
        Vec3d across = AttackGeometry.planeAxis(swing, frame);
        double halfWidth = AttackGeometry.laneHalfWidth(swing);
        float inner = effect.innerFraction();
        Vec3d[][] points = new Vec3d[rows + 1][2];
        float[] rowAlpha = new float[rows + 1];

        for (int r = 0; r <= rows; r++) {
            float along = (float) r / rows;
            float t = MathHelper.lerp(along, inner, 1.0f);
            Vec3d centre = AttackGeometry.surfacePoint(swing, frame, extension, t);
            double reached = centre.distanceTo(frame.origin());
            // Taper toward the root so the lane reads as a spearhead rather than a plank, then take
            // whichever of that and the cone is narrower. Both only ever shrink it: the drawn lane
            // stays inside the box, never beside it.
            double width = Math.min(halfWidth * (0.35 + 0.65 * t),
                AttackGeometry.coneHalfWidthAt(swing, reached));
            Vec3d offset = across.multiply(width);
            points[r][0] = centre.subtract(offset);
            points[r][1] = centre.add(offset);
            rowAlpha[r] = 0.3f + 0.7f * along;
        }
        return new Ribbon(points, rowAlpha);
    }

    /**
     * The outer bloom: a band projecting past the attack's real reach, fading out as it goes.
     *
     * <p>It shares the arc's swept angles exactly, so it is the same shape pointing the same way — only
     * longer. It starts at the tip ({@code t = 1}, the real limit) and runs outward, so it never
     * overlaps or brightens the damaging region. Returns null for a thrust; a lane's reach is already
     * its whole visual.
     */
    private static @Nullable Ribbon buildOverreach(SlashEffect effect, AttackSwing swing, AttackFrame frame,
                                                   float tickDelta) {
        if (swing.shape() == SlashShape.THRUST) {
            return null;
        }
        SlashProfile profile = effect.profile();
        float lead = effect.leadingEdge(tickDelta);
        float tail = effect.trailingEdge(tickDelta);
        if (lead - tail < 1.0e-4f) {
            return null;
        }

        int rows = Math.max(2, profile.sweepSegments() / 2);
        int cols = OVERREACH_SEGMENTS;
        Vec3d[][] points = new Vec3d[rows + 1][cols + 1];
        float[] rowAlpha = new float[rows + 1];
        float[] colAlpha = new float[cols + 1];

        // Full strength where it meets the blade tip, nothing at its outer limit. Squared, so it falls
        // away quickly rather than lingering as a halo.
        for (int c = 0; c <= cols; c++) {
            float outward = (float) c / cols;
            colAlpha[c] = (1.0f - outward) * (1.0f - outward);
        }

        for (int r = 0; r <= rows; r++) {
            float rowFraction = (float) r / rows;
            float s = MathHelper.lerp(rowFraction, tail, lead);
            rowAlpha[r] = rowFraction * rowFraction * (3.0f - 2.0f * rowFraction);
            for (int c = 0; c <= cols; c++) {
                // t runs from 1 (the real tip) outward to 1 + overreach.
                float t = 1.0f + profile.overreach() * ((float) c / cols);
                points[r][c] = overreachPoint(swing, frame, s, t);
            }
        }
        return new Ribbon(points, rowAlpha, colAlpha);
    }

    /**
     * The arc's surface extended past {@code t = 1}.
     *
     * <p>{@link AttackGeometry#radiusAt} deliberately clamps {@code t} to the real reach — that clamp is
     * what guarantees nothing can be drawn outside the damage volume, and it is not something to relax.
     * So the bloom computes its own radius here instead, using the same angle and the same plane, and
     * reaches past the limit without asking the geometry to lie about where the limit is.
     */
    private static Vec3d overreachPoint(AttackSwing swing, AttackFrame frame, float s, float t) {
        double angle = Math.toRadians(AttackGeometry.sweepAngleDegrees(swing, s));
        Vec3d direction = frame.forward().multiply(Math.cos(angle))
            .add(AttackGeometry.planeAxis(swing, frame).multiply(Math.sin(angle)));
        return frame.origin().add(direction.multiply(swing.range() * t));
    }

    /** A copy of the ribbon displaced bodily along the swing plane's normal — one face of the slab. */
    private static Ribbon offsetGrid(Ribbon ribbon, Vec3d normal, float distance) {
        Vec3d[][] source = ribbon.points();
        Vec3d[][] shifted = new Vec3d[source.length][];
        Vec3d offset = normal.multiply(distance);
        for (int r = 0; r < source.length; r++) {
            shifted[r] = new Vec3d[source[r].length];
            for (int c = 0; c < source[r].length; c++) {
                shifted[r][c] = source[r][c].add(offset);
            }
        }
        return new Ribbon(shifted, ribbon.rowAlpha(), ribbon.colAlpha());
    }

    private static void emit(VertexConsumer consumer, MatrixStack.Entry entry, Ribbon ribbon,
                             float red, float green, float blue, float alpha, float flash, Vec3d normal) {
        Vec3d[][] points = ribbon.points();
        int rows = points.length - 1;
        int cols = points[0].length - 1;

        for (int r = 0; r < rows; r++) {
            float u0 = (float) r / rows;
            float u1 = (float) (r + 1) / rows;
            for (int c = 0; c < cols; c++) {
                float v0 = (float) c / cols;
                float v1 = (float) (c + 1) / cols;
                Vec3d p00 = points[r][c];
                Vec3d p10 = points[r + 1][c];
                Vec3d p11 = points[r + 1][c + 1];
                Vec3d p01 = points[r][c + 1];

                // Per-corner rather than per-row, so a ribbon that fades across the blade as well as
                // along the swing (the outer bloom) gets a smooth gradient in both directions. The
                // flash lifts each corner toward full: the confirmed arc shows itself whole.
                float a00 = alpha * MathHelper.lerp(flash, ribbon.alphaAt(r, c), 1.0f);
                float a01 = alpha * MathHelper.lerp(flash, ribbon.alphaAt(r, c + 1), 1.0f);
                float a11 = alpha * MathHelper.lerp(flash, ribbon.alphaAt(r + 1, c + 1), 1.0f);
                float a10 = alpha * MathHelper.lerp(flash, ribbon.alphaAt(r + 1, c), 1.0f);

                quad(consumer, entry, red, green, blue, normal,
                    p00, u0, v0, a00, p01, u0, v1, a01, p11, u1, v1, a11, p10, u1, v0, a10);
                // Reverse winding: the slash has to be visible from whichever side the player is on.
                quad(consumer, entry, red, green, blue, normal.multiply(-1.0),
                    p10, u1, v0, a10, p11, u1, v1, a11, p01, u0, v1, a01, p00, u0, v0, a00);
            }
        }
    }

    private static void quad(VertexConsumer consumer, MatrixStack.Entry entry,
                             float red, float green, float blue, Vec3d normal,
                             Vec3d a, float au, float av, float aa,
                             Vec3d b, float bu, float bv, float ba,
                             Vec3d c, float cu, float cv, float ca,
                             Vec3d d, float du, float dv, float da) {
        vertex(consumer, entry, red, green, blue, normal, a, au, av, aa);
        vertex(consumer, entry, red, green, blue, normal, b, bu, bv, ba);
        vertex(consumer, entry, red, green, blue, normal, c, cu, cv, ca);
        vertex(consumer, entry, red, green, blue, normal, d, du, dv, da);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry,
                               float red, float green, float blue,
                               Vec3d normal, Vec3d position, float u, float v, float alpha) {
        consumer.vertex(entry, (float) position.x, (float) position.y, (float) position.z)
            .color(red, green, blue, MathHelper.clamp(alpha, 0.0f, 1.0f))
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(FULL_BRIGHT)
            .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
