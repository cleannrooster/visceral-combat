package com.cleannrooster.visceral_combat.networking;

import com.cleannrooster.visceral_combat.VisceralCombat;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.config.ConfigSync;
import com.cleannrooster.visceral_combat.particle.SlashParticleHandler;
import com.cleannrooster.visceral_combat.util.EntityHelper;
import com.cleannrooster.visceral_combat.util.TickScheduler;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public class ServerNetworkHandler {

    public static void register() {
        if(Platform.getEnvironment().equals(Env.SERVER)) {

            NetworkManager.registerS2CPayloadType(ConfigSync.PACKET_ID, ConfigSync.CODEC);
            NetworkManager.registerS2CPayloadType(Packet.HolsterAssert.PACKET_ID, Packet.HolsterAssert.CODEC);
            NetworkManager.registerS2CPayloadType(Packet.LungeAck.PACKET_ID, Packet.LungeAck.CODEC);

        }
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, Packet.Holster.PACKET_ID, Packet.Holster.CODEC,
            (payload, context) -> context.queue(() -> {
                if (context.getPlayer() instanceof HitstopAccessor hitstopAccessor) {
                    hitstopAccessor.setHolster(payload.bool() || !hitstopAccessor.isHolster());
                }
            })
        );

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, Packet.Impulse.PACKET_ID, Packet.Impulse.CODEC,
            (payload, context) -> context.queue(() -> {
                Entity entity = context.getPlayer().getWorld().getEntityById(payload.id());
                if (payload.shouldCheck()) {
                    var list = EntityHelper.getEntitiesInFront(context.getPlayer(),
                        (float) context.getPlayer().getEntityInteractionRange() * 1.4F);
                    if (context.getPlayer() instanceof HitstopAccessor accessor) {
                        accessor.setShouldClamp(!list.isEmpty());
                    }
                    NetworkManager.sendToPlayer((ServerPlayerEntity) context.getPlayer(), new Packet.LungeAck());
                }
                else
                if (entity instanceof HitstopAccessor hitstopAccessor) {
                    boolean isEnemy = entity != context.getPlayer() && !payload.shouldCheck();
                    if (isEnemy) {
                        Vec3d dir = new Vec3d(payload.x(), payload.y(), payload.z());
                        double dirLen = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                        if (dirLen > 0.001 && entity.getWorld() instanceof ServerWorld sWorld) {
                            final Vec3d horizDir = new Vec3d(dir.x / dirLen, 0, dir.z / dirLen);
                            TickScheduler.schedule(sWorld, 1, () -> {
                                if (!(entity instanceof HitstopAccessor ha)) return;
                                Vec3d stored = ha.getVelocityHitstop();
                                if (stored != null && (stored.x != 0.0 || stored.z != 0.0)) {
                                    double horizMag = 0.4 * VisceralCombat.config.dirCoeff
                                        * Math.sqrt(stored.x * stored.x + stored.z * stored.z);
                                    ha.setVelocityHitstop(horizDir.multiply(horizMag).add(0, stored.y, 0));
                                } else {
                                    Vec3d vel = entity.getVelocity();
                                    double horizMag = 0.4 * VisceralCombat.config.dirCoeff
                                        * Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                                    if (horizMag > 0.001) {
                                        entity.setVelocity(horizDir.multiply(horizMag).add(0, vel.y, 0));
                                        entity.velocityModified = true;
                                        entity.velocityDirty = true;
                                    }
                                }
                            });
                        }
                    } else {
                        hitstopAccessor.setImpulseVector(
                            hitstopAccessor.getImpulseVector()
                                .multiply(payload.mag2())
                                .add(new Vec3d(payload.x(), payload.y(), payload.z()))
                                .multiply(payload.mag())
                        );
                        entity.setVelocity(entity.getVelocity());
                        entity.velocityModified = true;
                        entity.velocityDirty = true;
                    }
                }
            })
        );

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, Packet.Packets.PACKET_ID, Packet.Packets.CODEC,
            (payload, context) -> context.queue(() -> {
                ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
                SlashParticleHandler.spawnParticlesSlash(player, player.getServerWorld(),
                    payload.yaw(), payload.pitch(), payload.range());
            })
        );
    }
}
