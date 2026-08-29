package com.cleannrooster.visceral_combat.client.targeting;

import com.cleannrooster.visceral_combat.combat.AttackSwing;
import com.cleannrooster.visceral_combat.combat.SlashShape;
import com.cleannrooster.visceral_combat.config.ServerConfig;
import com.cleannrooster.visceral_combat.config.TargetAssistMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The target and assistance budget of one swing, frozen at {@code ATTACK_START}.
 *
 * <p>A commitment is a promise about <em>this</em> swing only: the target it names never changes
 * (no mid-swing retargeting, even if a better candidate appears), and once its tracking breaks —
 * target died, escaped the envelope, went behind a wall — it stays broken for the remainder of the
 * swing. What can still happen after a break is exactly what would happen with assistance off: the
 * attack resolves wherever the player is actually aiming.
 *
 * <p>All the numbers that bound the assistance are derived once, here, in {@link #of}: from the
 * server config's base values and from the real Better Combat attack ({@link AttackSwing}) — its
 * shape, arc, and duration. That factory is the intended seam for future RPG attributes: an
 * "accuracy" stat would scale these budgets, an enemy "evasion" stat effectively shrinks them by
 * moving harder, and neither needs to touch the per-tick tracking code.
 */
@Environment(EnvType.CLIENT)
public final class AttackCommitment {

    /**
     * Swing length (ticks) at which the per-tick correction rate is exactly the configured value.
     * Slower, heavier attacks get proportionally less correction per tick — a windup you committed
     * to two seconds ago should not steer like a dagger jab — and very fast attacks slightly more,
     * since they have so few ticks to correct in.
     */
    private static final float BASELINE_SWING_TICKS = 8.0f;
    /** Rate scale floor/ceiling from swing duration, so no attack becomes untrackable or twitchy. */
    private static final float MIN_DURATION_SCALE = 0.5f;
    private static final float MAX_DURATION_SCALE = 1.25f;

    /**
     * A thrust concentrates its damage in a narrow lane, so its assistance is narrow but fine: a
     * smaller total envelope than a sweep, corrected at a slightly higher rate. A sweep's broad arc
     * already tolerates lateral movement, so it keeps the full envelope at the base rate.
     */
    private static final float THRUST_ENVELOPE_SCALE = 0.6f;
    private static final float THRUST_RATE_SCALE = 1.3f;

    /** Pitch runs on half the yaw envelope: vertical misaim is smaller and over-correction nauseating. */
    private static final float PITCH_ENVELOPE_FRACTION = 0.5f;

    public final LivingEntity target;
    public final AttackSwing swing;
    /** Ticks of upswing this commitment covers; assistance ends when they run out. */
    public final int swingTicks;

    /** The direction the attack started in — the centre of every correction envelope. */
    public final float initialYaw;
    public final float initialPitch;
    /** Where the target stood at commitment, kept for future accuracy/evasion style modifiers. */
    public final double initialDistance;
    public final double initialAngularOffset;

    /** Maximum total correction (degrees) away from the initial direction, after shape scaling. */
    public final float yawEnvelope;
    public final float pitchEnvelope;
    /** Maximum correction speed (degrees per tick), after shape and duration scaling. */
    public final float yawRatePerTick;
    public final float pitchRatePerTick;

    /** Beyond this distance (blocks) the target has escaped and tracking breaks. */
    public final double breakDistance;

    /** False for full-circle attacks: they hit all around, so steering them helps nobody. */
    public final boolean tracksAngle;
    /** True only in RPG mode: whether the attack lunge may bend toward and step at the target. */
    public final boolean approachAllowed;

    private int ticksElapsed;
    private boolean broken;

    /**
     * Build the commitment for a swing that is starting right now, or null when no assistance
     * applies (assist off, no acquired target, or nothing about the attack worth assisting).
     */
    public static AttackCommitment of(ClientPlayerEntity player, AttackSwing swing,
                                      LivingEntity target, ServerConfig config) {
        TargetAssistMode mode = config.targetAssistMode;
        if (mode == TargetAssistMode.OFF || target == null || swing == null) {
            return null;
        }
        return new AttackCommitment(player, swing, target, config, mode);
    }

    private AttackCommitment(ClientPlayerEntity player, AttackSwing swing, LivingEntity target,
                             ServerConfig config, TargetAssistMode mode) {
        this.target = target;
        this.swing = swing;
        this.swingTicks = swing.swingTicks();
        this.initialYaw = player.getYaw();
        this.initialPitch = player.getPitch();
        this.initialDistance = TargetAcquisition.distanceToBox(
            player.getCameraPosVec(1.0f), target.getBoundingBox());
        this.initialAngularOffset = angleBetween(player.getRotationVec(1.0f),
            target.getBoundingBox().getCenter().subtract(player.getCameraPosVec(1.0f)));

        boolean thrust = swing.shape() == SlashShape.THRUST;
        float envelopeScale = thrust ? THRUST_ENVELOPE_SCALE : 1.0f;
        float durationScale = MathHelper.clamp(BASELINE_SWING_TICKS / this.swingTicks,
            MIN_DURATION_SCALE, MAX_DURATION_SCALE);
        float rateScale = durationScale * (thrust ? THRUST_RATE_SCALE : 1.0f);

        this.yawEnvelope = config.assistMaxTrackingAngle * envelopeScale;
        this.pitchEnvelope = this.yawEnvelope * PITCH_ENVELOPE_FRACTION;
        this.yawRatePerTick = config.assistMaxYawPerTick * rateScale;
        this.pitchRatePerTick = config.assistMaxPitchPerTick * rateScale;

        // The target may legitimately drift out to the edge of what a full approach step can still
        // cover; only beyond that has it escaped. The slack term absorbs one tick of ordinary motion
        // so tracking doesn't break on a boundary jitter.
        this.breakDistance = swing.range() + config.assistMaxApproachDistance
            + AttackTracking.ESCAPE_DISTANCE_SLACK;

        this.tracksAngle = !swing.fullCircle();
        this.approachAllowed = mode == TargetAssistMode.RPG;
    }

    /**
     * Ticks over which the correction rate ramps in. Two, not more: the ease-in only has to remove
     * the velocity jump at the start of the turn, and windups are short — a longer ramp would eat a
     * fast weapon's whole correction window.
     */
    private static final float RATE_RAMP_TICKS = 2.0f;

    /** Advance the swing clock. @return true while the commitment still covers the current tick */
    boolean advanceTick() {
        this.ticksElapsed++;
        return this.ticksElapsed <= this.swingTicks + 1;
    }

    /**
     * Ease-in: the fraction of the per-tick correction rate available this tick — half on the first
     * tracked tick, full from the second on. Paired with the tracker's ease-out gain, the whole turn
     * is an S-curve: it starts gently, is fastest mid-correction, and settles asymptotically.
     */
    float rateRamp() {
        return Math.min(1.0f, this.ticksElapsed / RATE_RAMP_TICKS);
    }

    /** Tracking has failed for this swing; assistance is over, but the attack itself proceeds. */
    void markBroken() {
        this.broken = true;
    }

    public boolean isBroken() {
        return this.broken;
    }

    /** Angle in degrees between two directions; 0 when either has no length to speak of. */
    private static double angleBetween(Vec3d a, Vec3d b) {
        double lengths = a.length() * b.length();
        if (lengths < 1.0e-6) {
            return 0.0;
        }
        return Math.toDegrees(Math.acos(MathHelper.clamp(a.dotProduct(b) / lengths, -1.0, 1.0)));
    }
}
