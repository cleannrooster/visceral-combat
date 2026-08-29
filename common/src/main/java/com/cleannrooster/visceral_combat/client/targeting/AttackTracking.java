package com.cleannrooster.visceral_combat.client.targeting;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.combat.AttackSwing;
import com.cleannrooster.visceral_combat.config.ServerConfig;
import com.cleannrooster.visceral_combat.config.TargetAssistMode;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Applies the limited facing corrections an {@link AttackCommitment} allows, tick by tick, while the
 * committed swing winds up.
 *
 * <p>The mechanism is deliberately mundane: Better Combat resolves the hit off the player's actual
 * yaw and pitch when the upswing completes, so making the character "execute the attack competently"
 * means turning the player a few clamped degrees per tick toward the committed target — nothing else.
 * There is no second hitbox, no damage forcing, no camera lock. Corrections run in {@code CLIENT_PRE},
 * before the client tick in which Better Combat counts the upswing down, so the final tick's
 * correction still shapes the volume that gets tested.
 *
 * <p>Two clamps bound every correction, and both are centred on the direction the attack started in
 * ({@code initialYaw}/{@code initialPitch}), not on wherever tracking has drifted to:
 * <ul>
 *   <li>a per-tick rate ({@code yawRatePerTick}) — the character turns, it doesn't snap;</li>
 *   <li>a total envelope ({@code yawEnvelope}) — however long the windup, the swing can never end up
 *       more than this far from where it began.</li>
 * </ul>
 * Within those clamps the step itself is eased ({@link #SMOOTHING_GAIN} of the remaining error per
 * tick, ramped in over the first ticks by {@link AttackCommitment#rateRamp}), so the turn is an
 * S-curve rather than a constant-rate march that stops dead on arrival.
 * A target that moves outside what those clamps can cover has dodged, and tracking breaks for the
 * rest of the swing — this is the "evasion beats accuracy" half of the design, expressed as physical
 * movement instead of dice.
 */
@Environment(EnvType.CLIENT)
public final class AttackTracking {

    /**
     * The target may be tracked up to this factor past the envelope before it counts as escaped.
     * Between 1x (where correction saturates) and this, the swing keeps its best clamped aim — the
     * attack is not abandoned just because it is no longer perfectly on target.
     */
    private static final float ESCAPE_ANGLE_FACTOR = 1.5f;
    /** Blocks past the reach + approach envelope a target may drift before it counts as escaped. */
    static final double ESCAPE_DISTANCE_SLACK = 0.5;

    /**
     * Fraction of the remaining angular error corrected per tick, before the rate cap. This is what
     * shapes the turn: an exponential ease-out that is fastest while the error is large and settles
     * asymptotically as it closes, instead of marching at a constant rate and stopping dead on
     * arrival. The per-tick rate cap still bounds the worst case, so the gain changes the feel of a
     * correction but never its ceiling.
     */
    private static final float SMOOTHING_GAIN = 0.35f;

    /**
     * A target closer than this fraction of the weapon's reach needs no step at all; between here and
     * full reach the lunge scales up, so the approach never launches the attacker through an enemy
     * already at sword's length.
     */
    private static final double COMFORT_REACH_FRACTION = 0.8;
    /** Lunge magnitude kept when the target is already comfortably in reach. */
    private static final double CLOSE_RANGE_DAMP = 0.5;

    private static @Nullable AttackCommitment current;

    /**
     * The correction applied this tick, remembered so {@link #smoothCamera} can restore the render
     * interpolation the entity tick destroys. The entity tick copies yaw into prevYaw before moving,
     * so a CLIENT_PRE correction would otherwise render as an instant 20Hz step; subtracting the
     * applied delta back out of prevYaw in CLIENT_POST makes the frame interpolate through the turn
     * — the camera glides while Better Combat still saw the corrected value at tick time.
     */
    private static float appliedYawDelta;
    private static float appliedPitchDelta;

    private AttackTracking() {
    }

    public static @Nullable AttackCommitment current() {
        return current;
    }

    public static void clear() {
        current = null;
    }

    /**
     * Commit to the acquired target for the swing that is starting now. Called from the
     * {@code ATTACK_START} hook; replaces any stale previous commitment.
     *
     * @return the new commitment, or null when no assistance applies to this swing
     */
    public static @Nullable AttackCommitment begin(ClientPlayerEntity player, @Nullable AttackSwing swing,
                                                   @Nullable LivingEntity target, ServerConfig config) {
        current = swing == null || target == null ? null
            : AttackCommitment.of(player, swing, target, config);
        return current;
    }

    /**
     * One tick of tracking. Runs in {@code CLIENT_PRE} so the correction lands before Better Combat's
     * upswing countdown (and possible hit resolution) for the same tick.
     */
    public static void tick(MinecraftClient client) {
        appliedYawDelta = 0.0f;
        appliedPitchDelta = 0.0f;
        AttackCommitment commitment = current;
        if (commitment == null) {
            return;
        }
        var config = VisceralCombatClient.clientConfig;
        ClientPlayerEntity player = client.player;
        if (config == null || config.targetAssistMode == TargetAssistMode.OFF
                || player == null || client.world == null) {
            clear();
            return;
        }

        // The swing is over once Better Combat's upswing has run out (it resolved the hit, or the
        // player cancelled it) — our own tick budget is the fallback in case BC's counter was reused
        // by a follow-up attack before we noticed.
        int upswingTicks = ((MinecraftClient_BetterCombat) (Object) client).getUpswingTicks();
        if (upswingTicks <= 0 || !commitment.advanceTick()) {
            clear();
            return;
        }
        if (commitment.isBroken()) {
            return; // broken stays broken: no corrections, and no mid-swing retargeting
        }

        LivingEntity target = commitment.target;
        if (!target.isAlive() || target.isRemoved()
                || target.getWorld() != player.getWorld()
                // Mid-swing invalidation: the same "is this even a target" tests acquisition uses.
                // A target that turns untargetable, enters spectator, or vanishes entirely during
                // the windup stops being assisted — the swing continues wherever it was aimed.
                || !target.isAttackable() || target.isSpectator()
                || target.isInvisibleTo(player)) {
            commitment.markBroken();
            return;
        }

        Vec3d eye = player.getCameraPosVec(1.0f);
        Vec3d aim = target.getBoundingBox().getCenter();
        double distance = TargetAcquisition.distanceToBox(eye, target.getBoundingBox());
        if (distance > commitment.breakDistance || !TargetAcquisition.hasLineOfSight(player, target)) {
            commitment.markBroken(); // escaped or ducked behind cover: the dodge worked
            return;
        }

        if (!commitment.tracksAngle) {
            return; // full-circle attacks hit all around; only the approach (lunge) assists them
        }

        Vec3d toTarget = aim.subtract(eye);
        float desiredYaw = yawOf(toTarget);
        float desiredPitch = pitchOf(toTarget);

        // Escape test against the *initial* direction: however patiently the target circles, the
        // moment facing them would take more than the envelope allows (plus grace), they are out.
        if (Math.abs(MathHelper.wrapDegrees(desiredYaw - commitment.initialYaw))
                > commitment.yawEnvelope * ESCAPE_ANGLE_FACTOR) {
            commitment.markBroken();
            return;
        }

        float yawBefore = player.getYaw();
        float pitchBefore = player.getPitch();
        float ramp = commitment.rateRamp();
        player.setYaw(correct(yawBefore, desiredYaw, commitment.initialYaw,
            commitment.yawRatePerTick * ramp, commitment.yawEnvelope));
        player.setPitch(MathHelper.clamp(
            correct(pitchBefore, desiredPitch, commitment.initialPitch,
                commitment.pitchRatePerTick * ramp, commitment.pitchEnvelope),
            -90.0f, 90.0f));
        appliedYawDelta = player.getYaw() - yawBefore;
        appliedPitchDelta = player.getPitch() - pitchBefore;
    }

    /**
     * Restore render interpolation for this tick's correction — see {@link #appliedYawDelta}. Runs
     * in {@code CLIENT_POST}, after the entity tick has copied current rotation into prev.
     */
    public static void smoothCamera(MinecraftClient client) {
        if (client.player == null || (appliedYawDelta == 0.0f && appliedPitchDelta == 0.0f)) {
            return;
        }
        client.player.prevYaw -= appliedYawDelta;
        client.player.prevPitch -= appliedPitchDelta;
        appliedYawDelta = 0.0f;
        appliedPitchDelta = 0.0f;
    }

    /**
     * Move {@code angle} toward {@code desired} with an eased step, without ever stepping more than
     * {@code ratePerTick} degrees or aiming past {@code envelope} degrees from {@code initial}.
     *
     * <p>The goal is computed first — the desired offset from the initial direction, saturated at the
     * envelope edge — and the step is {@link #SMOOTHING_GAIN} of the remaining error toward that goal,
     * rate-capped. Easing toward the pre-clamped goal (rather than stepping and then clamping the
     * result) matters twice over: the approach to the envelope edge decelerates like any other
     * correction instead of hitting a wall, and a player who mouse-turns outside the envelope
     * mid-swing is pulled back at no more than the rate cap, never snapped. The mouse always out-runs
     * the assist; the assist only ever drifts.
     */
    static float correct(float angle, float desired, float initial, float ratePerTick, float envelope) {
        float goalOffset = MathHelper.clamp(MathHelper.wrapDegrees(desired - initial), -envelope, envelope);
        float currentOffset = MathHelper.wrapDegrees(angle - initial);
        float step = MathHelper.clamp((goalOffset - currentOffset) * SMOOTHING_GAIN,
            -ratePerTick, ratePerTick);
        return initial + currentOffset + step;
    }

    /**
     * Bend the attack lunge toward the committed target, in RPG mode.
     *
     * <p>Direction and magnitude are treated separately. The direction is a plain blend between where
     * the chosen {@code LungeMode} pointed the lunge and the horizontal line to the target, weighted
     * by {@code assistLungeStrength} — so DUELING still lunges, ARCADE still honours movement input,
     * and the assist only leans the result. The magnitude scales with how much distance actually
     * needs closing: a target beyond comfortable reach gets the full step (capped by how much of
     * {@code assistMaxApproachDistance} the gap represents), a target already at sword's length gets
     * a damped one so the attacker doesn't sail past them.
     *
     * @param lungeDir the mode-computed lunge vector (unit-ish; ARCADE may be zero when standing still)
     * @return the biased vector, or {@code lungeDir} unchanged when no approach assistance applies
     */
    public static Vec3d biasLunge(Vec3d lungeDir, ClientPlayerEntity player, ServerConfig config) {
        AttackCommitment commitment = current;
        if (commitment == null || commitment.isBroken() || !commitment.approachAllowed
                || config.assistLungeStrength <= 0.0f) {
            return lungeDir;
        }
        LivingEntity target = commitment.target;
        Vec3d toTarget = target.getBoundingBox().getCenter().subtract(player.getPos());
        Vec3d toTargetHoriz = new Vec3d(toTarget.x, 0, toTarget.z);
        if (toTargetHoriz.lengthSquared() < 1.0e-6) {
            return lungeDir; // directly above/below: no horizontal step makes sense
        }
        toTargetHoriz = toTargetHoriz.normalize();

        double magnitude = lungeDir.length();
        Vec3d baseDir = magnitude > 1.0e-4 ? lungeDir.multiply(1.0 / magnitude)
            : toTargetHoriz; // standing-still ARCADE attack: the step, if any, is toward the target
        Vec3d direction = baseDir.multiply(1.0 - config.assistLungeStrength)
            .add(toTargetHoriz.multiply(config.assistLungeStrength));
        if (direction.lengthSquared() < 1.0e-6) {
            return lungeDir; // opposed and cancelled out: keep the player's own lunge
        }
        direction = direction.normalize();

        // How much of the approach budget this gap needs, 0..1. Also the magnitude for a lunge the
        // mode gave no strength of its own (ARCADE, standing still): the "step into range".
        double comfortable = commitment.swing.range() * COMFORT_REACH_FRACTION;
        double distance = TargetAcquisition.distanceToBox(
            player.getCameraPosVec(1.0f), target.getBoundingBox());
        double gapFraction = config.assistMaxApproachDistance > 1.0e-4
            ? MathHelper.clamp((distance - comfortable) / config.assistMaxApproachDistance, 0.0, 1.0)
            : 0.0;
        double scale = MathHelper.lerp(gapFraction, CLOSE_RANGE_DAMP, 1.0);
        return direction.multiply(Math.max(magnitude, gapFraction) * scale);
    }

    /** Minecraft yaw (degrees) of a direction: {@code Vec3d.fromPolar}'s inverse, horizontally. */
    private static float yawOf(Vec3d direction) {
        return (float) Math.toDegrees(MathHelper.atan2(-direction.x, direction.z));
    }

    /** Minecraft pitch (degrees) of a direction: negative looks up, positive looks down. */
    private static float pitchOf(Vec3d direction) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return (float) Math.toDegrees(-MathHelper.atan2(direction.y, horizontal));
    }
}
