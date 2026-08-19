package com.cleannrooster.visceral_combat.combat;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.MathHelper;

/**
 * The parameters of one swing, as the damage volume defines them.
 *
 * <p>These are read straight off the Better Combat attack that is about to resolve — nothing here is a
 * visual tuning value. {@link AttackGeometry} turns them into the swept surface, and the renderer draws
 * that surface, so a weapon whose reach or arc changes gets a correspondingly changed visual with no
 * further work.
 *
 * @param shape      which surface the attack sweeps
 * @param range      the attack's reach in blocks — Better Combat's {@code attackRange} with any
 *                   registered range extensions folded in, so it is the number its own target finder
 *                   ends up using rather than the weapon's unmodified attribute
 * @param angle      the full arc in degrees ({@code attack.angle()}); over 180 makes the attack a full
 *                   circle, which is also what widens Better Combat's own hitbox
 * @param swingTicks ticks from the start of the animation to the moment the hit resolves, so the ribbon
 *                   finishes cutting exactly when damage is evaluated
 * @param reversed   which way the blade travels through the arc — cosmetic, since the damage volume is
 *                   symmetric about the look direction, but it has to match the animation
 */
public record AttackSwing(SlashShape shape, float range, float angle, int swingTicks, boolean reversed) {

    /** Beyond this the visual would be describing a volume no reasonable weapon has. */
    private static final float MAX_RANGE = 64.0f;
    private static final int MAX_SWING_TICKS = 100;

    /**
     * Better Combat treats an arc wider than 180 degrees as a full circle: the hitbox is centred on the
     * attacker instead of projected forward, and doubles in length. See {@code TargetFinder}.
     */
    public boolean fullCircle() {
        return this.angle > 180.0f;
    }

    /**
     * True when the attack places no angular limit on what it can reach.
     *
     * <p>An angle of zero is not a zero-width attack: Better Combat's radial filter skips the angle test
     * outright when the angle is zero, leaving the oriented box and the reach as the only limits. Every
     * stock thrust is authored this way, so reading zero literally would draw nothing at all for spears.
     */
    public boolean unrestrictedAngle() {
        return this.angle <= 0.0f;
    }

    /**
     * Half the drawn arc, in degrees.
     *
     * <p>Capped at 90 degrees for anything but a full circle, because the forward-projected hitbox only
     * occupies the half-space in front of the attacker — past a quarter turn the arc would leave the box
     * that has to contain a victim for the hit to land. That cap is also the answer for an attack with
     * no angular limit of its own: the box is still the limit.
     */
    public float halfAngle() {
        float bound = fullCircle() ? 180.0f : 90.0f;
        return unrestrictedAngle() ? bound : Math.min(this.angle * 0.5f, bound);
    }

    /** Clamp anything that arrived over the network into a range the renderer can honour. */
    public AttackSwing sanitised() {
        return new AttackSwing(
            this.shape,
            MathHelper.clamp(this.range, 0.1f, MAX_RANGE),
            MathHelper.clamp(this.angle, 0.0f, 360.0f),
            MathHelper.clamp(this.swingTicks, 1, MAX_SWING_TICKS),
            this.reversed);
    }

    public void write(PacketByteBuf buf) {
        buf.writeByte(this.shape.ordinal());
        buf.writeFloat(this.range);
        buf.writeFloat(this.angle);
        buf.writeVarInt(this.swingTicks);
        buf.writeBoolean(this.reversed);
    }

    public static AttackSwing read(PacketByteBuf buf) {
        return new AttackSwing(
            SlashShape.byId(buf.readByte()),
            buf.readFloat(),
            buf.readFloat(),
            buf.readVarInt(),
            buf.readBoolean()).sanitised();
    }
}
