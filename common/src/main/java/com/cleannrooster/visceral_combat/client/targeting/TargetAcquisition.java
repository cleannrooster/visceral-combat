package com.cleannrooster.visceral_combat.client.targeting;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.client.combat.SwingReader;
import com.cleannrooster.visceral_combat.combat.AttackFrame;
import com.cleannrooster.visceral_combat.combat.AttackSwing;
import com.cleannrooster.visceral_combat.config.TargetAssistMode;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.logic.PlayerAttackHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides, once per client tick, which enemy the player <em>appears to intend</em> to attack.
 *
 * <p>This is a statement of intent, not of geometry: the acquired target is the enemy the highlight
 * sits on and the one an {@link AttackCommitment} will be built around when the next swing starts.
 * Whether the swing actually connects stays entirely Better Combat's call — acquisition deliberately
 * reaches slightly beyond the weapon's true range (by the configured range bonus), because a committed
 * attack may step that far toward its target, but it never widens or substitutes for the real hitbox.
 *
 * <p>Candidates are scored rather than nearest-picked, and the score is dominated by angular offset
 * from the crosshair: an enemy under the cursor beats a nearer enemy off to the side, because pointing
 * at something is the clearest statement of intent the mouse can make. The reigning target keeps a
 * configurable score advantage (hysteresis) so the highlight doesn't flicker between two adjacent mobs
 * that score within noise of each other.
 *
 * <p>Everything here is local-player, client-side state. The server never needs to know which enemy
 * was highlighted; it only ever sees the ordinary movement and attacks that result.
 */
@Environment(EnvType.CLIENT)
public final class TargetAcquisition {

    /**
     * Score weights. Angular intent dominates distance by design: the crosshair is the player's
     * statement of which enemy they mean, while distance mostly breaks ties between enemies in the
     * same direction. Each term below is normalised to 0..1 before weighting.
     */
    private static final double ANGLE_WEIGHT = 3.0;
    private static final double DISTANCE_WEIGHT = 1.0;
    /** Flat bonus for a target already inside the weapon's true reach — no step needed to hit it. */
    private static final double IN_REACH_BONUS = 0.5;

    /** How many of the best-scored candidates get a line-of-sight raycast before giving up. */
    private static final int LOS_CHECK_LIMIT = 3;

    private static @Nullable LivingEntity acquired;

    private TargetAcquisition() {
    }

    /** The enemy currently considered the player's intended target, or null. */
    public static @Nullable LivingEntity acquiredTarget() {
        return acquired;
    }

    public static void clear() {
        acquired = null;
    }

    /**
     * Re-evaluate the intended target. Runs every client tick (CLIENT_POST, so entity positions are
     * fresh); cheap enough not to need throttling — one entity query plus at most a few raycasts.
     */
    public static void tick(MinecraftClient client) {
        var config = VisceralCombatClient.clientConfig;
        ClientPlayerEntity player = client.player;
        if (config == null || config.targetAssistMode == TargetAssistMode.OFF
                || player == null || client.world == null
                || !player.isAlive() || player.isSpectator()
                || (player instanceof HitstopAccessor accessor && accessor.isHolster())) {
            clear();
            return;
        }

        // The attack the player would throw next: the same lookup Better Combat's startUpswing does
        // (current combo step against the held weapon's attributes). Deliberately NOT
        // getCurrentAttackHand(), which reads the in-flight swing and is null while idle — and idle
        // is exactly when acquisition matters. Null here means no Better Combat weapon in hand.
        int comboCount = ((MinecraftClient_BetterCombat) (Object) client).getComboCount();
        AttackHand hand = PlayerAttackHelper.getCurrentAttack(player, comboCount);
        AttackSwing swing = hand != null ? SwingReader.describe(player, hand) : null;
        if (swing == null) {
            clear();
            return;
        }

        double acquisitionRange = swing.range() + config.assistAcquisitionRangeBonus;
        AttackFrame frame = AttackFrame.of(player, 1.0f);
        Vec3d origin = frame.origin();
        Vec3d forward = frame.forward();

        List<TargetCandidate> candidates = new ArrayList<>();
        Box searchBox = player.getBoundingBox().expand(acquisitionRange);
        for (LivingEntity entity : player.getWorld().getEntitiesByClass(
                LivingEntity.class, searchBox, e -> isValidTarget(player, e))) {
            double distance = distanceToBox(origin, entity.getBoundingBox());
            if (distance > acquisitionRange) {
                continue;
            }
            double angle = angleOffCrosshair(origin, forward, entity);
            if (angle > config.assistAcquisitionAngle) {
                continue;
            }
            candidates.add(new TargetCandidate(entity, distance, angle,
                score(config.assistAcquisitionAngle, acquisitionRange, swing.range(),
                    distance, angle, entity == acquired ? config.assistTargetStickiness : 1.0f)));
        }
        candidates.sort(Comparator.comparingDouble(TargetCandidate::score).reversed()
            // Deterministic order between equal scores, so ties can't alternate frame to frame.
            .thenComparingInt(c -> c.entity().getId()));

        // Only the winners pay for a raycast: an obstructed best candidate falls through to the next.
        acquired = null;
        int checked = 0;
        for (TargetCandidate candidate : candidates) {
            if (checked++ >= LOS_CHECK_LIMIT) {
                break;
            }
            if (hasLineOfSight(player, candidate.entity())) {
                acquired = candidate.entity();
                break;
            }
        }
    }

    /**
     * The composite intent score. All inputs are known-in-range (the hard gates ran first), so each
     * normalised term lies in 0..1.
     *
     * <p>This is the single place future accuracy-style attributes would plug in: anything that should
     * make a character "better at picking targets" — wider effective angle, stronger reach bonus —
     * belongs as a modifier to the values feeding this method, not as a new selection path.
     *
     * @param stickiness multiplier for the currently acquired target ({@code 1.0} for everyone else) —
     *                   the hysteresis that keeps the highlight from flickering between near-equals
     */
    private static double score(double maxAngle, double maxDistance, double trueReach,
                                double distance, double angle, double stickiness) {
        double angleScore = 1.0 - angle / Math.max(1.0e-3, maxAngle);
        double distanceScore = 1.0 - MathHelper.clamp(distance / Math.max(1.0e-3, maxDistance), 0.0, 1.0);
        double inReach = distance <= trueReach ? 1.0 : 0.0;
        return (angleScore * ANGLE_WEIGHT + distanceScore * DISTANCE_WEIGHT + inReach * IN_REACH_BONUS)
            * stickiness;
    }

    /**
     * A plausible melee victim: alive, attackable, visible-world entity that isn't the player, their
     * mount, or a spectator. Finer friend-or-foe judgement is left to Better Combat at hit time — a
     * highlight on something BC then refuses to hit costs nothing, while duplicating BC's rules here
     * would drift out of sync with them.
     */
    private static boolean isValidTarget(ClientPlayerEntity player, LivingEntity entity) {
        return entity != player
            && entity.isAlive()
            && entity.isAttackable()
            && !entity.isSpectator()
            && !entity.isInvisibleTo(player)
            && entity.getVehicle() != player && player.getVehicle() != entity;
    }

    /** Distance from {@code point} to the closest point of {@code box} (0 when inside). */
    static double distanceToBox(Vec3d point, Box box) {
        double dx = MathHelper.clamp(point.x, box.minX, box.maxX) - point.x;
        double dy = MathHelper.clamp(point.y, box.minY, box.maxY) - point.y;
        double dz = MathHelper.clamp(point.z, box.minZ, box.maxZ) - point.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Angle in degrees between the look direction and the candidate's centre. */
    private static double angleOffCrosshair(Vec3d origin, Vec3d forward, LivingEntity entity) {
        Vec3d toTarget = entity.getBoundingBox().getCenter().subtract(origin);
        double length = toTarget.length();
        if (length < 1.0e-4) {
            return 0.0; // effectively on top of us: no angular disagreement possible
        }
        double cos = MathHelper.clamp(toTarget.multiply(1.0 / length).dotProduct(forward), -1.0, 1.0);
        return Math.toDegrees(Math.acos(cos));
    }

    /**
     * True when no block stands between the player's eyes and the candidate's centre. An enemy around
     * a corner or behind a wall is "obviously invalid" as an intended target, however well it scores.
     */
    static boolean hasLineOfSight(ClientPlayerEntity player, LivingEntity entity) {
        Vec3d eye = player.getCameraPosVec(1.0f);
        Vec3d aim = entity.getBoundingBox().getCenter();
        return player.getWorld().raycast(new RaycastContext(eye, aim,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player))
            .getType() == HitResult.Type.MISS;
    }
}
