package com.cleannrooster.visceral_combat.particle;

import com.cleannrooster.visceral_combat.util.TickScheduler;
import com.cleannrooster.visceral_combat.util.VectorHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public class SlashParticleHandler {

    private static final int   TICKS           = 5;
    private static final int   EDGE_PER_TICK   = 11;
    private static final int   FILL_PER_TICK   = 13;
    private static final float BASE_HALF_SWEEP = 62.0f;

    public static void spawnParticlesSlash(LivingEntity entity, ServerWorld world,
                                           float offset, float yaw, float pitch, float range) {
        buildSlash(entity, world,
            entity.getYaw() - 90.0f + offset, yaw - 90.0f,
            pitch, range, false);
    }

    public static void spawnParticlesSlash(LivingEntity entity, ServerWorld world,
                                           float yaw, float pitch, float range) {
        buildSlash(entity, world,
            entity.getYaw() - 90.0f, yaw - 90.0f,
            pitch, range, true);
    }

    private static void buildSlash(LivingEntity entity, ServerWorld world,
                                   float f7, float f, float pitchScale,
                                   float range, boolean applyEntityPitch) {
        float radiusBase  = range * 0.9f;
        float entityPitch = applyEntityPitch ? (float) Math.toRadians(entity.getPitch()) : 0.0f;

        float halfSweep = BASE_HALF_SWEEP + (entity.getRandom().nextFloat() - 0.5f) * 15.0f;
        float arcBias   = (entity.getRandom().nextFloat() - 0.5f) * 8.0f;
        float sweepRad  = (float) Math.toRadians(2.0f * halfSweep);

        for (int tick = 0; tick < TICKS; tick++) {
            int finalTick = tick;
            TickScheduler.schedule(world, tick + 1, () ->
                spawnBand(entity, world, finalTick, halfSweep, arcBias, sweepRad,
                    radiusBase, pitchScale, f7, f, entityPitch)
            );
        }
    }

    private static void spawnBand(LivingEntity entity, ServerWorld world,
                                  int tick, float halfSweep, float arcBias, float sweepRad,
                                  float radiusBase, float pitchScale,
                                  float f7, float f, float entityPitch) {

        float halfSweepRad = sweepRad * 0.5f;
        float bandStep     = sweepRad / TICKS;
        float tStart       = (float) Math.toRadians(-halfSweep + arcBias) + bandStep * tick;
        float tEnd         = tStart + bandStep;
        float tCenter      = (tStart + tEnd) * 0.5f;
        float tHalf        = bandStep * 0.5f;
        float sweepStart   = (float) Math.toRadians(-halfSweep + arcBias);

        // Edge pass
        int edgeCount = EDGE_PER_TICK + entity.getRandom().nextInt(5) - 2;
        for (int p = 0; p < edgeCount; p++) {
            float t = tCenter + (float) (entity.getRandom().nextGaussian() * tHalf * 0.65f);
            float cosT      = (float) Math.max(0.0, Math.cos(t / halfSweepRad * (Math.PI / 2.0)));
            float edgeTaper = cosT * cosT;
            if (entity.getRandom().nextFloat() > edgeTaper * 0.88f + 0.12f) continue;

            float radius = (float) (radiusBase * (0.98 + entity.getRandom().nextGaussian() * 0.04));
            radius = Math.clamp(radius, radiusBase * 0.88f, radiusBase * 1.10f);

            Vec3d pos;
            Vec3d tangent;
            {
                double lx  = -Math.cos(t) * radius;
                double lz  =  Math.sin(t) * radius * pitchScale;
                double ly  = (entity.getRandom().nextDouble() - 0.5) * radiusBase * 0.03;
                double ttx =  Math.sin(t) * radius * 0.04;
                double ttz =  Math.cos(t) * radius * pitchScale * 0.04;

                Vec3d lp = rotate(lx, ly, lz, Math.toRadians(-f7), Math.toRadians(f), 0.0);
                Vec3d lt = rotate(ttx, 0.0, ttz, Math.toRadians(-f7), Math.toRadians(f), 0.0);
                Vec3d wo = VectorHelper.rotateTowards(lp, new Vec3d(0.0, -1.0, 0.0), entityPitch);
                tangent  = VectorHelper.rotateTowards(lt, new Vec3d(0.0, -1.0, 0.0), entityPitch)
                                       .normalize().multiply(0.04);
                pos = wo.add(entity.getEyePos());
            }
            dispatchEdge(entity, world, pos, tangent);
        }

        // Fill pass
        int fillCount = FILL_PER_TICK + entity.getRandom().nextInt(5) - 2;
        for (int p = 0; p < fillCount; p++) {
            float t = tCenter + (float) (entity.getRandom().nextGaussian() * tHalf * 0.65f);
            float arcProgress = Math.clamp((t - sweepStart) / sweepRad, 0.0f, 1.0f);
            float sinTaper    = (float) Math.sin(arcProgress * Math.PI);
            if (entity.getRandom().nextFloat() > sinTaper * 0.75f + 0.25f) continue;

            float radius = radiusBase * (0.15f + entity.getRandom().nextFloat() * 0.78f);
            radius = Math.min(radius, radiusBase * 0.87f);

            Vec3d pos;
            Vec3d tangent;
            {
                double lx  = -Math.cos(t) * radius;
                double lz  =  Math.sin(t) * radius * pitchScale;
                double ly  = (entity.getRandom().nextDouble() - 0.5) * radiusBase * 0.07;
                double ttx =  Math.sin(t) * radius * 0.04;
                double ttz =  Math.cos(t) * radius * pitchScale * 0.04;

                Vec3d lp = rotate(lx, ly, lz, Math.toRadians(-f7), Math.toRadians(f), 0.0);
                Vec3d lt = rotate(ttx, 0.0, ttz, Math.toRadians(-f7), Math.toRadians(f), 0.0);
                Vec3d wo = VectorHelper.rotateTowards(lp, new Vec3d(0.0, -1.0, 0.0), entityPitch);
                tangent  = VectorHelper.rotateTowards(lt, new Vec3d(0.0, -1.0, 0.0), entityPitch)
                                       .normalize().multiply(0.045);
                pos = wo.add(entity.getEyePos());
            }
            dispatchFill(entity, world, pos, tangent);
        }
    }

    private static void dispatchEdge(LivingEntity entity, ServerWorld world, Vec3d pos, Vec3d tangent) {
        for (ServerPlayerEntity player : world.getPlayers()) spawnEdge(player, world, pos, tangent);
    }

    private static void dispatchFill(LivingEntity entity, ServerWorld world, Vec3d pos, Vec3d tangent) {
        for (ServerPlayerEntity player : world.getPlayers()) spawnFill(player, world, pos, tangent);
    }

    private static void spawnEdge(ServerPlayerEntity player, ServerWorld world, Vec3d pos, Vec3d tangent) {
        world.spawnParticles(player, ModParticles.SLASH_GLINT.get(), true,
            pos.x, pos.y, pos.z, 1, tangent.x, tangent.y, tangent.z, 0.015);
        if (player.getRandom().nextFloat() < 0.65f) {
            world.spawnParticles(player, ModParticles.SLASH_FLASH.get(), true,
                pos.x, pos.y, pos.z, 1, tangent.x, tangent.y, tangent.z, 0.02);
        }
    }

    private static void spawnFill(ServerPlayerEntity player, ServerWorld world, Vec3d pos, Vec3d tangent) {
        world.spawnParticles(player, ModParticles.SLASH_FLASH.get(), true,
            pos.x, pos.y, pos.z, 1, tangent.x, tangent.y, tangent.z, 0.02);
        if (player.getRandom().nextFloat() < 0.22f) {
            world.spawnParticles(player, ModParticles.SLASH_GLINT.get(), true,
                pos.x, pos.y, pos.z, 1,
                tangent.x * 0.5, tangent.y * 0.5, tangent.z * 0.5, 0.02);
        }
    }

    public static Vec3d rotate(double x, double y, double z, double pitch, double roll, double yaw) {
        double cosa = Math.cos(yaw),  sina = Math.sin(yaw);
        double cosb = Math.cos(pitch), sinb = Math.sin(pitch);
        double cosc = Math.cos(roll),  sinc = Math.sin(roll);
        return new Vec3d(
            (cosa * cosb) * x + (cosa * sinb * sinc - sina * cosc) * y + (cosa * sinb * cosc + sina * sinc) * z,
            (sina * cosb) * x + (sina * sinb * sinc + cosa * cosc) * y + (sina * sinb * cosc - cosa * sinc) * z,
            (-sinb)       * x + (cosb * sinc)                      * y + (cosb * cosc)                      * z
        );
    }
}
