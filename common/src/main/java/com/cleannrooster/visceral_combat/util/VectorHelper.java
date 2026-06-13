package com.cleannrooster.visceral_combat.util;

import net.minecraft.util.math.Vec3d;

public class VectorHelper {

    /**
     * Tilts {@code v} towards {@code target} by {@code angle} radians using Rodrigues' formula.
     * The rotation axis is the axis perpendicular to both {@code v} and {@code target},
     * so the result stays in the plane spanned by the two vectors.
     */
    public static Vec3d rotateTowards(Vec3d v, Vec3d target, float angle) {
        Vec3d rotAxis = v.crossProduct(target);
        double len = rotAxis.length();
        if (len < 0.001) return v; // v and target are parallel; nothing to rotate
        rotAxis = rotAxis.multiply(1.0 / len);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return v.multiply(cos)
                .add(rotAxis.crossProduct(v).multiply(sin))
                .add(rotAxis.multiply(rotAxis.dotProduct(v) * (1.0 - cos)));
    }
}
