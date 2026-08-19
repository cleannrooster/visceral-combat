package com.cleannrooster.visceral_combat.combat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Where a swing is anchored and how it is oriented, for one instant.
 *
 * <p>The origin and the three axes are built exactly the way Better Combat builds them for hit
 * detection ({@code TargetFinder.getInitialTracingPoint} and {@code OrientedBoundingBox}), so an arc
 * drawn from this frame starts where the damage starts and points where the damage points.
 *
 * @param origin  the tracing point the damage volume radiates from
 * @param forward the look direction — the hitbox's local Z
 * @param up      the in-plane vertical — the hitbox's local Y
 * @param right   the lateral axis — the hitbox's local X
 */
public record AttackFrame(Vec3d origin, Vec3d forward, Vec3d up, Vec3d right) {

    /**
     * The frame Better Combat would have used for an attack resolving right now.
     *
     * <p>Rotation is read interpolated so the ribbon tracks a turning attacker smoothly; at a tick
     * boundary it is the same yaw and pitch the target finder reads. That matters more than it sounds:
     * the volume is evaluated once, at the end of the swing, against whatever the attacker is facing
     * then — so a ribbon that follows the live rotation is showing the volume that will actually be
     * queried, not the one that would have been queried had the player stood still.
     */
    public static AttackFrame of(LivingEntity attacker, float tickDelta) {
        // TargetFinder.getInitialTracingPoint: eye height dropped by 15% of the attacker's height.
        double drop = attacker.getHeight() * 0.15 * attacker.getScaleFactor();
        Vec3d origin = attacker.getCameraPosVec(tickDelta).subtract(0.0, drop, 0.0);
        return of(origin, attacker.getYaw(tickDelta), attacker.getPitch(tickDelta));
    }

    /** The same basis Better Combat's OrientedBoundingBox derives from a pitch and a yaw. */
    public static AttackFrame of(Vec3d origin, float yaw, float pitch) {
        Vec3d forward = Vec3d.fromPolar(pitch, yaw).normalize();
        Vec3d up = Vec3d.fromPolar(pitch + 90.0f, yaw).negate().normalize();
        return new AttackFrame(origin, forward, up, forward.crossProduct(up));
    }
}
