package com.cleannrooster.visceral_combat.client.combat;

import com.cleannrooster.visceral_combat.combat.AttackFrame;
import com.cleannrooster.visceral_combat.combat.AttackSwing;
import com.cleannrooster.visceral_combat.combat.SlashProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/**
 * One slash currently playing on the client.
 *
 * <p>It holds no geometry of its own — just the swing's parameters, who is swinging, and how far
 * through it is. The ribbon mesh is regenerated from {@link com.cleannrooster.visceral_combat.combat.AttackGeometry}
 * every frame using the numbers Better Combat will use for hit detection, which is what keeps the drawn
 * arc honest.
 */
@Environment(EnvType.CLIENT)
public final class SlashEffect {

    /** Blocks of the arc left undrawn in front of the viewer's own camera. */
    private static final float FIRST_PERSON_CLEARANCE = 1.25f;

    private final AttackSwing swing;
    private final SlashProfile profile;
    private final LivingEntity attacker;
    private final int lifetime;

    private AttackFrame frame;
    private int age;

    SlashEffect(AttackSwing swing, SlashProfile profile, LivingEntity attacker, AttackFrame frame) {
        this.swing = swing;
        this.profile = profile;
        this.attacker = attacker;
        this.frame = frame;
        this.lifetime = swing.swingTicks() + profile.fadeOutTicks();
    }

    public AttackSwing swing() {
        return this.swing;
    }

    public SlashProfile profile() {
        return this.profile;
    }

    /**
     * Where the drawn ribbon starts along the blade, as a fraction of the reach.
     *
     * <p>The profile's own value, except when the viewer is the one swinging and the camera is sitting
     * at the attack's own origin — then the innermost blocks of the arc would be smeared across the
     * whole screen. Pushing the start outward only ever removes ribbon, so the drawn shape stays inside
     * the volume; the outer edge, which is what reach is actually read from, is untouched.
     */
    float innerFraction() {
        float base = this.profile.innerFraction();
        MinecraftClient client = MinecraftClient.getInstance();
        if (this.attacker == client.getCameraEntity() && client.options.getPerspective().isFirstPerson()) {
            return Math.max(base, FIRST_PERSON_CLEARANCE / this.swing.range());
        }
        return base;
    }

    /**
     * The frame to draw from, re-read from the attacker while they are still around.
     *
     * <p>Better Combat evaluates the whole volume once, at the end of the swing, against wherever the
     * attacker is pointing then — so following them is what keeps the arc showing the volume that will
     * be queried. The last frame is kept as a fallback so a slash finishes its fade rather than
     * snapping away if the attacker leaves the client's view mid-swing.
     */
    AttackFrame resolveFrame(float tickDelta) {
        if (this.attacker != null && !this.attacker.isRemoved()) {
            this.frame = AttackFrame.of(this.attacker, tickDelta);
        }
        return this.frame;
    }

    void tick() {
        this.age++;
    }

    boolean isExpired() {
        return this.age >= this.lifetime;
    }

    /**
     * Leading edge of the swing, 0-1. Advancing this every frame is what animates the arc through its
     * sweep instead of popping a finished crescent into existence.
     */
    public float leadingEdge(float tickDelta) {
        return MathHelper.clamp((this.age + tickDelta) / this.swing.swingTicks(), 0.0f, 1.0f);
    }

    /**
     * Trailing edge of the visible ribbon — the leading edge minus the profile's trail length. At the
     * default trail of 1 nothing is dropped, so by the time the hit resolves the ribbon spans the whole
     * arc that was tested.
     */
    public float trailingEdge(float tickDelta) {
        return Math.max(0.0f, leadingEdge(tickDelta) - this.profile.trail());
    }

    /** Fade applied once the hit has resolved, so the arc dissipates rather than vanishing. */
    public float fade(float tickDelta) {
        int fadeTicks = this.profile.fadeOutTicks();
        float elapsed = this.age + tickDelta - this.swing.swingTicks();
        if (elapsed <= 0.0f || fadeTicks <= 0) {
            return 1.0f;
        }
        return MathHelper.clamp(1.0f - elapsed / fadeTicks, 0.0f, 1.0f);
    }
}
