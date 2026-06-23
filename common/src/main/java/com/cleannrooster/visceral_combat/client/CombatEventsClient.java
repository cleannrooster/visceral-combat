package com.cleannrooster.visceral_combat.client;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.config.LungeMode;
import com.cleannrooster.visceral_combat.networking.Packet;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class CombatEventsClient {

    public static void register() {
        BetterCombatClientEvents.ATTACK_START.register((player, attackHand) -> {
            var config = VisceralCombatClient.clientConfig;
            if (config == null) return; // not synced from the server yet: do nothing
            if (config.moveAttack
                    && !VisceralCombatClient.lungePending) {
                var cap = player.getMovementSpeed() * 2.0;
                var speed = 1.6F / player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED);
                var clamped = Math.max(-cap / 2, Math.min(cap, speed));
                var lookDir = player.getRotationVec(1.0F);
                var lookHoriz = new Vec3d(lookDir.x, 0, lookDir.z).normalize();
                var mode = config.lungeMode;

                Vec3d lungeDir;
                boolean shouldBrake = true;

                if (mode == LungeMode.DUELING) {
                    lungeDir = lookHoriz;
                    shouldBrake = false;
                } else if (mode == LungeMode.HYBRID) {
                    var movementInp = new Vec3d(player.input.movementSideways, 0, player.input.movementForward);
                    var movement = movementInp.rotateY((float) -(player.getYaw() * Math.PI / 180));
                    var forward = lookHoriz;
                    var right = new Vec3d(-forward.z, 0, forward.x);
                    var sideComponent = right.multiply(movement.dotProduct(right));
                    var sideCoeff = config.hybridSideCoeff;
                    lungeDir = forward.add(sideComponent.multiply(sideCoeff)).normalize();
                } else {
                    var movementInp = new Vec3d(player.input.movementSideways, 0, player.input.movementForward);
                    var movement = movementInp.rotateY((float) -(player.getYaw() * Math.PI / 180));
                    var backwardsCoeff = movement.dotProduct(lookHoriz) < 0
                        ? config.backwardsLungeCoeff : 1.0;
                    lungeDir = movement.multiply(backwardsCoeff);
                }

                var vecMoveEnemy = lungeDir.multiply(clamped).multiply(config.lungeSpeed, 0, config.lungeSpeed);

                var currentVel = player.getVelocity();
                var horizSpeedSq = currentVel.x * currentVel.x + currentVel.z * currentVel.z;
                var lungeSpeedCap = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)*1.4 * config.lungeSpeedCap;
                var lungeSpeedCapSq = lungeSpeedCap * lungeSpeedCap;

                if (horizSpeedSq < lungeSpeedCapSq) {
                    var addX = vecMoveEnemy.x;
                    var addZ = vecMoveEnemy.z;
                    var newX = currentVel.x + addX;
                    var newZ = currentVel.z + addZ;
                    var newHorizSpeedSq = newX * newX + newZ * newZ;
                    if (newHorizSpeedSq > lungeSpeedCapSq) {
                        var scale = lungeSpeedCap / Math.sqrt(newHorizSpeedSq);
                        addX = newX * scale - currentVel.x;
                        addZ = newZ * scale - currentVel.z;
                    }

                    // Predict locally for instant feel; server will ACK and own the authoritative result
                    player.addVelocity(addX, 0, addZ);
                    player.velocityDirty = true;
                    VisceralCombatClient.lungePending = true;
                    VisceralCombatClient.lungeExpiry = player.getWorld().getTime() + 40L;

                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                    new Packet.Impulse(player.getId(), 1F, 0.8F,
                        (float) vecMoveEnemy.x, (float) vecMoveEnemy.y, (float) vecMoveEnemy.z, true).write(buf);
                    NetworkManager.sendToServer(Packet.Impulse.ID, buf);
                }
            }

            if (config.particles
                    && attackHand.attack() != null) {
                float yaw = (float) (!attackHand.attack().hitbox().equals(WeaponAttributes.HitBoxShape.HORIZONTAL_PLANE) ? 0
                    : (!attackHand.isOffHand() && (!attackHand.attack().animation().contains("left") || attackHand.attack().animation().contains("right"))
                        ? 180 - (60 + player.getRandom().nextBetween(0, 60))
                        : 240 + player.getRandom().nextBetween(0, 60)));
                float pitch = attackHand.attack().hitbox().equals(WeaponAttributes.HitBoxShape.FORWARD_BOX) ? 0.25F : 1.0F;
                float range = (float) (attackHand.attributes().attackRange() == 0.0
                    ? 4.5
                    : attackHand.attributes().attackRange());
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                new Packet.Packets(yaw, pitch, range).write(buf);
                NetworkManager.sendToServer(Packet.Packets.ID, buf);
            }
        });

        BetterCombatClientEvents.ATTACK_HIT.register((clientPlayerEntity, attackHand, list, entity) -> {
            var config = VisceralCombatClient.clientConfig;
            if (config == null) return; // not synced from the server yet: do nothing
            Vec3d vecMove = clientPlayerEntity.getRotationVec(1.0F)
                .crossProduct(new Vec3d(0,
                    !attackHand.attack().hitbox().equals(WeaponAttributes.HitBoxShape.HORIZONTAL_PLANE) ? 0
                        : (!attackHand.isOffHand() && (!attackHand.attack().animation().contains("left") || attackHand.attack().animation().contains("right")) ? 1 : -1),
                    0))
                .add(0, attackHand.attack().hitbox().equals(WeaponAttributes.HitBoxShape.VERTICAL_PLANE) ? 1 : 0, 0)
                .normalize()
                .multiply(0.2 * Math.max(0.5, 1.6F / clientPlayerEntity.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED)));

            for (Entity entityMove : list) {
                Vec3d vecMoveEnemy = vecMove.multiply(-1F);
                if (entityMove instanceof LivingEntity livingEntity) {
                    vecMoveEnemy = vecMoveEnemy.multiply(1F - livingEntity.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE));
                }
                if (config.impactEnemy) {
                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                    new Packet.Impulse(entityMove.getId(), 1.1F,
                        Math.min(1F, (float) (clientPlayerEntity.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) / 4)),
                        (float) vecMoveEnemy.x, (float) vecMoveEnemy.y, (float) vecMoveEnemy.z, false).write(buf);
                    NetworkManager.sendToServer(Packet.Impulse.ID, buf);
                }
            }

            if (config.impactRecoil && !list.isEmpty()) {
                var vecRecoil = ((HitstopAccessor) clientPlayerEntity).getVelocityHitstop() != null
                    ? ((HitstopAccessor) clientPlayerEntity).getVelocityHitstop()
                    : Vec3d.ZERO;
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                new Packet.Impulse(clientPlayerEntity.getId(), 1F,
                    Math.min(1F, (float) (clientPlayerEntity.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) / 4)),
                    (float) vecRecoil.x, (float) vecRecoil.y, (float) vecRecoil.z, false).write(buf);
                NetworkManager.sendToServer(Packet.Impulse.ID, buf);
                clientPlayerEntity.velocityDirty = true;
            }

            LivingEntity living = (LivingEntity) (Object) clientPlayerEntity;
            if (config.hitstopSelf
                    && living instanceof HitstopAccessor hitstopAccessor && !list.isEmpty()) {
                hitstopAccessor.setHitstop((int) Math.ceil(2 * (1.6F / living.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED))));
                hitstopAccessor.setHitstopTime((int) living.getWorld().getTime());
                if (hitstopAccessor.getVelocityHitstop() == null) {
                    hitstopAccessor.setVelocityHitstop(living.getVelocity());
                    living.setVelocity(Vec3d.ZERO);
                    living.velocityDirty = true;
                }
            }
        });
    }
}
