package com.cleannrooster.visceral_combat.client;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.config.LungeMode;
import com.cleannrooster.visceral_combat.networking.Packet;
import dev.architectury.networking.NetworkManager;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class CombatEventsClient {

    public static void register() {
        BetterCombatClientEvents.ATTACK_START.register((player, attackHand) -> {
            if (VisceralCombatClient.clientConfig != null && VisceralCombatClient.clientConfig.moveAttack) {
                var cap = player.getMovementSpeed() * 2.0;
                var speed = 1.6F / player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED);
                var clamped = Math.clamp(speed, -cap / 2, cap);
                var lookDir = player.getRotationVec(1.0F);
                var lookHoriz = new Vec3d(lookDir.x, 0, lookDir.z).normalize();
                var mode = VisceralCombatClient.clientConfig.lungeMode;

                Vec3d lungeDir;
                boolean shouldBrake = true;

                if (mode == LungeMode.DUELING) {
                    // Always lunge straight forward regardless of input
                    lungeDir = lookHoriz;
                    shouldBrake = false;
                } else if (mode == LungeMode.HYBRID) {
                    // Forward lunge with a configurable left/right influence from input
                    var movementInp = new Vec3d(player.input.getMovementInput().x, 0, player.input.getMovementInput().y);
                    var movement = movementInp.rotateY((float) -(player.getYaw() * Math.PI / 180));
                    // Isolate only the lateral (side) component of movement relative to look direction
                    var forward = lookHoriz;
                    var right = new Vec3d(-forward.z, 0, forward.x);
                    var sideComponent = right.multiply(movement.dotProduct(right));
                    var sideCoeff = VisceralCombatClient.clientConfig.hybridSideCoeff;
                    lungeDir = forward.add(sideComponent.multiply(sideCoeff)).normalize();
                } else {
                    // ARCADE — lunge in movement input direction
                    var movementInp = new Vec3d(player.input.getMovementInput().x, 0, player.input.getMovementInput().y);
                    var movement = movementInp.rotateY((float) -(player.getYaw() * Math.PI / 180));
                    var backwardsCoeff = movement.dotProduct(lookHoriz) < 0
                        ? VisceralCombatClient.clientConfig.backwardsLungeCoeff : 1.0;
                    lungeDir = movement.multiply(backwardsCoeff);
                }

                var vecMoveEnemy = lungeDir.multiply(clamped).multiply(4F, 0, 4F);
                NetworkManager.sendToServer(new Packet.Impulse(player.getId(), 1F, 0.8F,
                    (float) vecMoveEnemy.x, (float) vecMoveEnemy.y, (float) vecMoveEnemy.z, shouldBrake));
            }

            if (VisceralCombatClient.clientConfig != null && VisceralCombatClient.clientConfig.particles
                    && attackHand.attack() != null) {
                float yaw = (float) (!attackHand.attack().hitbox().equals(WeaponAttributes.HitBoxShape.HORIZONTAL_PLANE) ? 0
                    : (!attackHand.isOffHand() && (!attackHand.attack().animation().contains("left") || attackHand.attack().animation().contains("right"))
                        ? 180 - (60 + player.getRandom().nextBetween(0, 60))
                        : 240 + player.getRandom().nextBetween(0, 60)));
                float pitch = attackHand.attack().hitbox().equals(WeaponAttributes.HitBoxShape.FORWARD_BOX) ? 0.25F : 1.0F;
                float range = (float) (attackHand.attributes().attackRange() == 0.0
                    ? player.getEntityInteractionRange()
                    : attackHand.attributes().attackRange() + attackHand.attributes().rangeBonus());
                NetworkManager.sendToServer(new Packet.Packets(yaw, pitch, range));
            }
        });

        BetterCombatClientEvents.ATTACK_HIT.register((clientPlayerEntity, attackHand, list, entity) -> {
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
                if (VisceralCombatClient.clientConfig.impactEnemy) {
                    NetworkManager.sendToServer(new Packet.Impulse(entityMove.getId(), 1.1F,
                        Math.min(1F, (float) (clientPlayerEntity.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) / 4)),
                        (float) vecMoveEnemy.x, (float) vecMoveEnemy.y, (float) vecMoveEnemy.z, false));
                }
            }

            if (VisceralCombatClient.clientConfig.impactRecoil && !list.isEmpty()) {
                var vecRecoil = ((HitstopAccessor) clientPlayerEntity).getVelocityHitstop() != null
                    ? ((HitstopAccessor) clientPlayerEntity).getVelocityHitstop()
                    : Vec3d.ZERO;
                NetworkManager.sendToServer(new Packet.Impulse(clientPlayerEntity.getId(), 1F,
                    Math.min(1F, (float) (clientPlayerEntity.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) / 4)),
                    (float) vecRecoil.x, (float) vecRecoil.y, (float) vecRecoil.z, false));
                clientPlayerEntity.velocityDirty = true;
            }

            LivingEntity living = (LivingEntity) (Object) clientPlayerEntity;
            if (VisceralCombatClient.clientConfig.hitstopSelf
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
