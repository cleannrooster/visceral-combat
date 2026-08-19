package com.cleannrooster.visceral_combat.combat;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The swept surface of an attack — the one place its shape is described.
 *
 * <p>Better Combat owns hit detection, so this class does not decide anything: it restates, in the form
 * the renderer needs, the volume {@code TargetFinder} already tests against. Everything below is
 * traceable to a specific piece of that code, and the references are named so the two can be checked
 * against each other when Better Combat changes:
 *
 * <ul>
 *   <li>{@code WeaponHitBoxes.createHitbox} gives the oriented box's dimensions — see
 *       {@link #hitboxSize}. That box is what bounds the arc's thickness and a thrust's width.</li>
 *   <li>{@code TargetFinder.findAttackTargetResult} centres that box on the tracing point and pushes it
 *       forward by half its length unless the attack is a full circle — which is why an arc is drawn in
 *       front of the attacker rather than around them.</li>
 *   <li>{@code TargetFinder.RadialFilter} then keeps only what is within {@code attackRange} of the
 *       origin <em>and</em> within half the attack's angle of the look direction. That pair is exactly
 *       the crescent {@link #surfacePoint} traces.</li>
 * </ul>
 *
 * <p>The parameters are the same two the divine_encounters ribbon uses: {@code s} runs 0 to 1 through
 * the swing, {@code t} runs 0 to 1 from the origin out to the attack's reach. {@code t} is clamped, so
 * no caller can ask for a point outside the damaging volume.
 */
public final class AttackGeometry {

    private AttackGeometry() {
    }

    /**
     * Dimensions of the oriented box Better Combat collides against, in the frame's local axes.
     *
     * <p>A transcription of {@code WeaponHitBoxes.createHitbox}. The renderer never draws this box —
     * it draws the surface swept inside it — but the box is what limits how thick an arc may be and
     * how wide a thrust may be, so it has to be stated somewhere the renderer can read it.
     */
    public static Vec3d hitboxSize(AttackSwing swing) {
        double range = swing.range();
        double length = range * (swing.fullCircle() ? 2.0 : 1.0);
        return switch (swing.shape()) {
            case THRUST -> new Vec3d(range * 0.5, range * 0.5, range);
            case VERTICAL_SWEEP -> new Vec3d(range / 3.0, range * 2.0, length);
            case HORIZONTAL_SWEEP -> new Vec3d(range * 2.0, range / 3.0, length);
        };
    }

    /**
     * The in-plane lateral axis: the direction positive swing angles point toward.
     *
     * <p>Picked so the drawn plane is the one the hitbox is flat in — a horizontal attack's box is
     * squashed in Y and stretched in X, so its arc sweeps through X.
     */
    public static Vec3d planeAxis(AttackSwing swing, AttackFrame frame) {
        return swing.shape() == SlashShape.VERTICAL_SWEEP ? frame.up() : frame.right();
    }

    /** The direction the volume has thickness in — the box's squashed axis. */
    public static Vec3d planeNormal(AttackSwing swing, AttackFrame frame) {
        return swing.shape() == SlashShape.VERTICAL_SWEEP ? frame.right() : frame.up();
    }

    /**
     * Half the volume's extent perpendicular to the drawn plane.
     *
     * <p>The renderer clamps its slab to this, so however solid a slash is made to look it can never
     * occupy space that would not have been checked for a victim.
     */
    public static double halfThickness(AttackSwing swing) {
        Vec3d size = hitboxSize(swing);
        return (swing.shape() == SlashShape.VERTICAL_SWEEP ? size.x : size.y) * 0.5;
    }

    /** Half the width of a thrust's lane — the hitbox's lateral half-extent. */
    public static double laneHalfWidth(AttackSwing swing) {
        return hitboxSize(swing).x * 0.5;
    }

    /** Distance from the origin at blade fraction {@code t}. Clamped: {@code t = 1} is the real reach. */
    public static double radiusAt(AttackSwing swing, float t) {
        return swing.range() * MathHelper.clamp(t, 0.0f, 1.0f);
    }

    /**
     * The angle of the blade at swing progress {@code s}, in degrees off the look direction.
     *
     * <p>Runs the full arc from one limit to the other. The damage volume is symmetric about the look
     * direction, so which end the swing starts from is the animation's business, not the hitbox's —
     * but the arc it covers by the end is the whole of what the hitbox will test.
     */
    public static float sweepAngleDegrees(AttackSwing swing, float s) {
        float half = swing.halfAngle();
        float start = swing.reversed() ? half : -half;
        float end = swing.reversed() ? -half : half;
        return MathHelper.lerp(MathHelper.clamp(s, 0.0f, 1.0f), start, end);
    }

    /**
     * World position on the attack surface at swing progress {@code s} and blade fraction {@code t}.
     *
     * <p>This is the function that makes the visual and the hitbox the same object.
     */
    public static Vec3d surfacePoint(AttackSwing swing, AttackFrame frame, float s, float t) {
        if (swing.shape() == SlashShape.THRUST) {
            // A thrust extends rather than sweeps: at progress s the lane reaches s of the way out, and
            // t runs along whatever is currently extended, so t = 1 is the advancing point.
            return frame.origin().add(frame.forward().multiply(radiusAt(swing, MathHelper.clamp(s, 0.0f, 1.0f) * t)));
        }
        double angle = Math.toRadians(sweepAngleDegrees(swing, s));
        Vec3d direction = frame.forward().multiply(Math.cos(angle))
            .add(planeAxis(swing, frame).multiply(Math.sin(angle)));
        return frame.origin().add(direction.multiply(radiusAt(swing, t)));
    }

    /**
     * How far off the centreline the lane may reach at {@code distance} from the origin without leaving
     * the attack's cone.
     *
     * <p>A thrust's box is as wide near the attacker as it is at the tip, but the radial filter's angle
     * applies at every distance — so near the origin the box's corners are outside what can actually be
     * hit. Clamping to the cone is what turns the drawn lane into a spearhead that matches.
     */
    public static double coneHalfWidthAt(AttackSwing swing, double distance) {
        float half = swing.halfAngle();
        if (swing.unrestrictedAngle() || half >= 89.0f) {
            return Double.MAX_VALUE; // no useful limit; the box is the binding constraint
        }
        return distance * Math.tan(Math.toRadians(half));
    }
}
