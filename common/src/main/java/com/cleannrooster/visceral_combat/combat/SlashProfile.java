package com.cleannrooster.visceral_combat.combat;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The look of a slash: a textured ribbon laid directly onto the attack's own swept surface, so what the
 * player sees is the volume that hurts them.
 *
 * <p>The renderer that consumes this knows nothing about weapons — it takes a profile plus an
 * {@link AttackSwing} and draws the surface {@link AttackGeometry} defines. Adding a new look means a
 * new profile; nothing about the geometry is tunable from here, which is the point.
 *
 * @param texture       ribbon texture (a soft streak; U runs along the swing, V across the blade)
 * @param red           tint, 0-1
 * @param green         tint, 0-1
 * @param blue          tint, 0-1
 * @param alpha         peak opacity
 * @param sweepSegments subdivisions along the swing direction; more = smoother crescent
 * @param bladeSegments subdivisions from root to tip
 * @param trail         how much of the already-swept arc stays visible behind the leading edge, as a
 *                      fraction of the full arc. 1 keeps all of it, which is what makes the finished
 *                      ribbon cover exactly the arc the hit test will sweep
 * @param innerFraction where the drawn ribbon starts along the blade, as a fraction of the reach — a
 *                      slash reads better when the innermost part near the shoulder is left empty, and
 *                      erring inward under-draws the volume rather than over-drawing it
 * @param thickness     visual half-thickness perpendicular to the swing plane, in blocks. Clamped by
 *                      the renderer to the hitbox's real half-extent
 * @param fadeOutTicks  how long the ribbon lingers and fades after the hit resolves
 * @param emissive      draw fullbright, so the arc reads at night and indoors
 * @param overreach     how far a dim outer bloom projects past the real reach, as a fraction of the
 *                      reach. 0 draws nothing extra — see {@link #overreach()}
 */
public record SlashProfile(
    Identifier texture,
    float red,
    float green,
    float blue,
    float alpha,
    int sweepSegments,
    int bladeSegments,
    float trail,
    float innerFraction,
    float thickness,
    int fadeOutTicks,
    boolean emissive,
    float overreach
) {

    /** Default ribbon texture: a crescent streak that fades in from the root and out at the tip. */
    public static final Identifier SWEEP_TEXTURE =
        new Identifier("visceral_combat", "textures/effect/slash.png");
    /** Narrower, harder-edged texture for thrusts. */
    public static final Identifier THRUST_TEXTURE =
        new Identifier("visceral_combat", "textures/effect/thrust.png");

    /**
     * Hard ceiling on the bloom, as a fraction of the attack's reach.
     *
     * <p>Every block it extends is a block where the player sees an effect and takes no damage, and past
     * a certain point that stops reading as pressure and starts reading as a lie.
     */
    public static final float MAX_OVERREACH = 0.34f;

    /**
     * Opacity of the bloom relative to the arc's own alpha.
     *
     * <p>The bloom has to stay faint enough that the bright core of the ribbon — which is the damage
     * volume, exactly — remains the obvious shape.
     */
    public static final float OVERREACH_ALPHA = 0.3f;

    public boolean hasOverreach() {
        return this.overreach > 0.001f && isVisible();
    }

    public boolean isVisible() {
        return this.alpha > 0.0f;
    }

    /**
     * The look used for each shape.
     *
     * <p>Both default to no overreach: this mod's arcs mark their reach honestly, and the bloom exists
     * only for callers that decide otherwise.
     */
    public static SlashProfile forShape(SlashShape shape) {
        return shape == SlashShape.THRUST ? THRUST : SWEEP;
    }

    /** Pale steel crescent for the two swept shapes. */
    public static final SlashProfile SWEEP = builder()
        .texture(SWEEP_TEXTURE)
        .color(1.0f, 0.97f, 0.90f)
        .alpha(0.75f)
        .segments(28, 6)
        .trail(1.0f)
        .innerFraction(0.14f)
        .thickness(0.08f)
        .fadeOutTicks(4)
        .build();

    /** Tighter and briefer for a thrust: the lane is short-lived and reads as a jab. */
    public static final SlashProfile THRUST = builder()
        .texture(THRUST_TEXTURE)
        .color(1.0f, 0.97f, 0.90f)
        .alpha(0.7f)
        .segments(28, 8)
        .trail(1.0f)
        .innerFraction(0.0f)
        .thickness(0.06f)
        .fadeOutTicks(3)
        .build();

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Identifier texture = SWEEP_TEXTURE;
        private float red = 1.0f;
        private float green = 0.97f;
        private float blue = 0.90f;
        private float alpha = 0.75f;
        private int sweepSegments = 24;
        private int bladeSegments = 6;
        private float trail = 1.0f;
        private float innerFraction = 0.14f;
        private float thickness = 0.08f;
        private int fadeOutTicks = 4;
        private boolean emissive = true;
        private float overreach = 0.0f;

        public Builder texture(Identifier texture) {
            this.texture = texture;
            return this;
        }

        public Builder color(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            return this;
        }

        public Builder alpha(float alpha) {
            this.alpha = alpha;
            return this;
        }

        public Builder segments(int sweepSegments, int bladeSegments) {
            this.sweepSegments = sweepSegments;
            this.bladeSegments = bladeSegments;
            return this;
        }

        public Builder trail(float trail) {
            this.trail = trail;
            return this;
        }

        public Builder innerFraction(float innerFraction) {
            this.innerFraction = innerFraction;
            return this;
        }

        public Builder thickness(float thickness) {
            this.thickness = thickness;
            return this;
        }

        public Builder fadeOutTicks(int ticks) {
            this.fadeOutTicks = ticks;
            return this;
        }

        public Builder emissive(boolean emissive) {
            this.emissive = emissive;
            return this;
        }

        /**
         * Project a dim bloom this far past the attack's real reach, as a fraction of the reach.
         *
         * <p>Clamped to {@link SlashProfile#MAX_OVERREACH}. The bloom is drawn at a fraction of the
         * arc's alpha and fades to nothing at its outer limit, so the solid part of the ribbon still
         * marks exactly where the damage stops.
         */
        public Builder overreach(float fraction) {
            this.overreach = MathHelper.clamp(fraction, 0.0f, MAX_OVERREACH);
            return this;
        }

        public SlashProfile build() {
            return new SlashProfile(this.texture, this.red, this.green, this.blue, this.alpha,
                this.sweepSegments, this.bladeSegments, this.trail, this.innerFraction,
                this.thickness, this.fadeOutTicks, this.emissive, this.overreach);
        }
    }
}
