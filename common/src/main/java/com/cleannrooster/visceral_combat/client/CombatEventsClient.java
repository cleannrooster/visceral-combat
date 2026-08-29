package com.cleannrooster.visceral_combat.client;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.client.combat.SlashEffectManager;
import com.cleannrooster.visceral_combat.client.combat.SwingReader;
import com.cleannrooster.visceral_combat.client.targeting.AttackTracking;
import com.cleannrooster.visceral_combat.client.targeting.TargetAcquisition;
import com.cleannrooster.visceral_combat.client.targeting.TargetHighlightRenderer;
import com.cleannrooster.visceral_combat.combat.AttackSwing;
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

/**
 * Wires Better Combat's client attack events into Visceral Combat's subsystems: the lunge, the slash
 * ribbon, hit feedback (hitstop/recoil/impact), and the target-assist commitment. This class decides
 * <em>when</em> each subsystem runs; the how lives in the subsystems — {@link SwingReader} reads the
 * swing off the attack, {@link AttackTracking} owns commitment and correction, {@link SlashEffectManager}
 * owns the visuals.
 */
@Environment(EnvType.CLIENT)
public class CombatEventsClient {

    public static void register() {
        BetterCombatClientEvents.ATTACK_START.register((player, attackHand) -> {
            var config = VisceralCombatClient.clientConfig;
            if (config == null) return; // not synced from the server yet: do nothing
            var now = player.getWorld().getTime();

            // The swing about to happen, read once off Better Combat's own attack data; the ribbon,
            // the commitment, and the lunge bias below all describe this same volume.
            AttackSwing swing = attackHand.attack() != null ? SwingReader.describe(player, attackHand) : null;

            // Commit to whatever target is acquired at the moment the swing starts. From here until
            // the hit resolves, AttackTracking (ticked from VisceralCombatClient) may make small
            // clamped facing corrections toward this — and only this — entity.
            AttackTracking.begin(player, swing, TargetAcquisition.acquiredTarget(), config);
            if (config.moveAttack
                    && (!config.requireSprint || player.isSprinting())
                    && !VisceralCombatClient.lungePending
                    && (!config.chargesEnabled || VisceralCombatClient.hasCharge(now))) {
                var attackSpeed = Math.clamp(player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED),
                    config.minAttackSpeed, config.maxAttackSpeed);
                // Lunge magnitude vs. attack speed: a bump that peaks at PEAK_ATTACK_SPEED and falls off
                // for weapons both faster and slower, so ~0.8-attack-speed weapons lunge hardest.
                // ATTACK_SPEED_INFLUENCE is how much of the magnitude attack speed drives (0 = flat,
                // 1 = fully): it was effectively 0.5 before, now 1.0 -> attack speed is twice as effective.
                final double PEAK_ATTACK_SPEED = 0.8;
                final double ATTACK_SPEED_INFLUENCE = 1.0;
                var ratio = attackSpeed / PEAK_ATTACK_SPEED;
                var bump = 2.0 * ratio / (ratio * ratio + 1.0); // 1.0 at the peak, less away from it
                var speed = 1.0 - ATTACK_SPEED_INFLUENCE * (1.0 - bump);
                var lookDir = player.getRotationVec(1.0F);
                var lookHoriz = new Vec3d(lookDir.x, 0, lookDir.z).normalize();
                var mode = config.lungeMode;

                Vec3d lungeDir;
                boolean shouldBrake = true;

                if (mode == LungeMode.DUELING) {
                    lungeDir = lookHoriz;
                    shouldBrake = false;
                } else if (mode == LungeMode.HYBRID) {
                    var movementInp = new Vec3d(player.input.getMovementInput().x, 0, player.input.getMovementInput().y);
                    var movement = movementInp.rotateY((float) -(player.getYaw() * Math.PI / 180));
                    var forward = lookHoriz;
                    var right = new Vec3d(-forward.z, 0, forward.x);
                    var sideComponent = right.multiply(movement.dotProduct(right));
                    var sideCoeff = config.hybridSideCoeff;
                    lungeDir = forward.add(sideComponent.multiply(sideCoeff)).normalize();
                } else {
                    var movementInp = new Vec3d(player.input.getMovementInput().x, 0, player.input.getMovementInput().y);
                    var movement = movementInp.rotateY((float) -(player.getYaw() * Math.PI / 180));
                    var backwardsCoeff = movement.dotProduct(lookHoriz) < 0
                        ? config.backwardsLungeCoeff : 1.0;
                    lungeDir = movement.multiply(backwardsCoeff);
                }

                // RPG assist: bend the mode's lunge toward the committed target and scale it to the
                // range gap that actually needs closing. A no-op in OFF/SOFT or with no commitment.
                lungeDir = AttackTracking.biasLunge(lungeDir, player, config);

                // Halved code-side so existing configs keep their original lungeSpeed scale.
                var lungeSpeed = config.lungeSpeed * 0.5;
                var vecMoveEnemy = lungeDir.multiply(speed).multiply(lungeSpeed, 0, lungeSpeed);

                var currentVel = player.getVelocity();
                var horizSpeedSq = currentVel.x * currentVel.x + currentVel.z * currentVel.z;
                // Doubled code-side so existing configs keep their original lungeSpeedCap scale.
                var lungeSpeedCap = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * 1.4 * config.lungeSpeedCap * 2.0;
                var lungeSpeedCapSq = lungeSpeedCap * lungeSpeedCap;

                if (horizSpeedSq < lungeSpeedCapSq) {
                    // Instant client-side prediction (all modes); server will ACK and gate the charge.
                    VisceralCombatClient.applyLungeVelocity(player, config, vecMoveEnemy.x, vecMoveEnemy.z);
                    if (mode == LungeMode.DUELING) {
                        // DUELING adds a short decaying tail applied client-side over the next few ticks
                        // (see VisceralCombatClient CLIENT_PRE) for its smooth "surge". This lives client-
                        // side now instead of the server accumulator, so it doesn't fight movement
                        // prediction — which is what made the server-applied version stutter.
                        VisceralCombatClient.lungeImpulse = vecMoveEnemy.multiply(config.impulseCoeff);
                    }
                    VisceralCombatClient.lungePending = true;
                    VisceralCombatClient.lungeExpiry = now + 40L;
                    // The lunge committed: spend a charge (when the charge system is on), recovering over
                    // config.chargeRecoveryTime attack cooldowns of this weapon.
                    if (config.chargesEnabled) {
                        VisceralCombatClient.consumeCharge(now, attackSpeed, config.chargeRecoveryTime);
                    }

                    NetworkManager.sendToServer(new Packet.Impulse(player.getId(), 1F, 0.8F,
                        (float) vecMoveEnemy.x, (float) vecMoveEnemy.y, (float) vecMoveEnemy.z, shouldBrake));
                }
            }

            if (config.particles) {
                if (swing != null) {
                    // Draw it here and now rather than waiting for the server to echo it back: the
                    // ribbon has to start on the same frame the animation does.
                    SlashEffectManager.spawn(player, swing);
                    // In self-only mode the server would drop the relay anyway (it enforces this
                    // regardless); not sending saves the packet.
                    if (config.ribbonsVisibleToOthers) {
                        NetworkManager.sendToServer(new Packet.SwingC2S(swing));
                    }
                }
            }
        });

        BetterCombatClientEvents.ATTACK_HIT.register((clientPlayerEntity, attackHand, list, entity) -> {
            var config = VisceralCombatClient.clientConfig;
            if (config == null) return; // not synced from the server yet: do nothing
            if (!list.isEmpty()) {
                // Hit feedback on the actor's own ribbon. Local only, on purpose: bystanders learn
                // about the hit from the victim, and the relayed swing stays pure geometry.
                SlashEffectManager.flashLocalHit(clientPlayerEntity);
                // And on the target marker, if the enemy under it is among the hit — same red, same
                // drain, so ring and ribbon confirm the connect as one gesture.
                TargetHighlightRenderer.flashHit(list, clientPlayerEntity.getWorld().getTime());
            }
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
                    NetworkManager.sendToServer(new Packet.Impulse(entityMove.getId(), 1.1F,
                        Math.min(1F, (float) (clientPlayerEntity.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) / 4)),
                        (float) vecMoveEnemy.x, (float) vecMoveEnemy.y, (float) vecMoveEnemy.z, false));
                }
            }

            if (config.impactRecoil && !list.isEmpty()) {
                var vecRecoil = ((HitstopAccessor) clientPlayerEntity).getVelocityHitstop() != null
                    ? ((HitstopAccessor) clientPlayerEntity).getVelocityHitstop()
                    : Vec3d.ZERO;
                // One-shot raw velocity change, client-predicted like the lunge (previously routed
                // through the server impulse accumulator; that accumulator now serves only DUELING lunges).
                clientPlayerEntity.addVelocity(vecRecoil.x, vecRecoil.y, vecRecoil.z);
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
