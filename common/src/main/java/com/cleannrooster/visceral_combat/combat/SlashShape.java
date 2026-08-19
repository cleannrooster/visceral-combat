package com.cleannrooster.visceral_combat.combat;

import net.bettercombat.api.WeaponAttributes;

/**
 * Which swept surface an attack traces.
 *
 * <p>One-to-one with Better Combat's {@link WeaponAttributes.HitBoxShape}, because the shape of the
 * damage volume is the shape of the visual. The mapping lives here and nowhere else, so the geometry
 * and the renderer never have to import Better Combat to know what they are drawing.
 */
public enum SlashShape {
    /** A crescent swept sideways through the plane containing the look direction and the lateral axis. */
    HORIZONTAL_SWEEP,
    /** The same crescent rotated a quarter turn: an overhead chop. */
    VERTICAL_SWEEP,
    /** A lane driven straight down the look direction — Better Combat's forward box. */
    THRUST;

    private static final SlashShape[] VALUES = values();

    public static SlashShape from(WeaponAttributes.HitBoxShape hitbox) {
        return switch (hitbox) {
            case HORIZONTAL_PLANE -> HORIZONTAL_SWEEP;
            case VERTICAL_PLANE -> VERTICAL_SWEEP;
            case FORWARD_BOX -> THRUST;
        };
    }

    public static SlashShape byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : HORIZONTAL_SWEEP;
    }

    /** True for the two shapes that sweep an arc; a thrust extends instead of sweeping. */
    public boolean isSweep() {
        return this != THRUST;
    }
}
