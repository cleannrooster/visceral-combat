package com.cleannrooster.visceral_combat.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.stream.Collectors;

public class EntityHelper {

    private static final double HALF_ANGLE_DEGREES = 75.0; // 150° total cone

    public static List<Entity> getEntitiesInFront(LivingEntity entity, float radius) {
        Box box = entity.getBoundingBox().expand(radius);
        Vec3d forward = entity.getRotationVec(1.0f);
        double cosAngle = Math.cos(Math.toRadians(HALF_ANGLE_DEGREES));

        return entity.getWorld().getEntitiesByClass(LivingEntity.class, box, e -> {
            if (e == entity) return false;
            Vec3d dir = e.getPos().subtract(entity.getPos());
            if (dir.length() < 0.001) return true;
            return dir.normalize().dotProduct(forward) >= cosAngle;
        }).stream().map(e -> (Entity) e).collect(Collectors.toList());
    }
}
