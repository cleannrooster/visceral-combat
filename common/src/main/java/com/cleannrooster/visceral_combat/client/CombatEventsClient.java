package com.cleannrooster.visceral_combat.client;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.client.combat.SlashEffectManager;
import com.cleannrooster.visceral_combat.combat.AttackSwing;
import com.cleannrooster.visceral_combat.combat.SlashShape;
import com.cleannrooster.visceral_combat.config.LungeMode;
import com.cleannrooster.visceral_combat.networking.Packet;
import dev.architectury.networking.NetworkManager;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.api.client.AttackRangeExtensions;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.bettercombat.logic.PlayerAttackHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class CombatEventsClient {

    public static void register() {
        BetterCombatClientEvents.ATTACK_START.register((player, attackHand) -> {
            var config = VisceralCombatClient.clientConfig;
            if (config == null) return; // not synced from the server yet: do nothing
            var now = player.getWorld().getTime();
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

            if (config.particles && attackHand.attack() != null) {
                AttackSwing swing = describeSwing(player, attackHand);
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

    /**
     * Read the swing about to happen off the Better Combat attack that fired it.
     *
     * <p>Every value here is one Better Combat itself will use when the hit resolves: the reach its
     * target finder ends up with (see {@link #effectiveRange}), the arc its radial filter tests against,
     * the hitbox shape that decides which plane the volume lies in, and the tick the damage lands on.
     * Nothing is invented for the visual's benefit, which is what lets the ribbon be the hitbox rather
     * than a decoration near it.
     *
     * @return null when the attack has no reach to draw — Better Combat would hit nothing either
     */
    private static AttackSwing describeSwing(ClientPlayerEntity player, AttackHand attackHand) {
        WeaponAttributes.Attack attack = attackHand.attack();
        // Better Combat 2.x composes reach out of the player's entity_interaction_range attribute, the
        // weapon's range_bonus / attack_range override, any range attribute on the item itself, and the
        // per-attack range_multiplier — getRangeForItem is BC's own public funnel for the first three,
        // read the same way its client attack hook reads it. Stock 2.x weapons author range_bonus and
        // leave attack_range at 0, so the old "attackRange() is the reach" assumption reads 0 for every
        // vanilla weapon now.
        double reach = PlayerAttackHelper.getRangeForItem(player, attackHand.itemStack())
            * attack.rangeMultiplier();
        float range = (float) effectiveRange(player, reach);
        if (range <= 0.0f) {
            return null;
        }
        SlashShape shape = SlashShape.from(attack.hitbox());
        // The damage is evaluated when the upswing completes, so that is when the ribbon finishes its
        // cut. Same expression Better Combat uses to schedule the hit (see its client attack hook).
        int swingTicks = Math.max(1, Math.round(
            PlayerAttackHelper.getAttackCooldownTicksCapped(player) * (float) attackHand.upswingRate()));
        // Which way the blade travels is the animation's business — the volume is symmetric about the
        // look direction — but the arc has to sweep the way the arms do.
        boolean reversed = swingReversed(shape, attack.animation(), attackHand.isOffHand());
        return new AttackSwing(shape, range, (float) attack.angle(), swingTicks, reversed);
    }


    /**
     * Which way the blade travels through its arc, read off the animation's name.
     *
     * <p>Animation packs state direction in the name, but not in one vocabulary. Better Combat and
     * simplyswords write the words {@code right}/{@code left} (the side the swing <em>starts</em> from);
     * the Malfu pack writes travel as letter pairs — {@code rl}/{@code lr} for single cuts,
     * {@code rlr}/{@code lrl} for combos (whose ribbon shows the leading cut), {@code updown}/
     * {@code downup} for vertical travel. The tokens have to be matched whole, split on the
     * separators: {@code one_handed_lr_rleg_lead} contains the letters "rl" inside "rleg", and a
     * substring match would read that left-to-right swing as right-to-left.
     *
     * <p>In {@link com.cleannrooster.visceral_combat.combat.AttackGeometry}'s convention, a
     * non-reversed sweep travels toward the plane axis: left-to-right for horizontal arcs, rising
     * for vertical ones.
     *
     * <p>Off-hand attacks play the same animation mirrored, which flips the lateral direction — and
     * only the lateral one: a mirrored overhead chop still falls.
     */
    private static boolean swingReversed(SlashShape shape, String animation, boolean offHand) {
        String[] tokens = animation.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        if (shape == SlashShape.VERTICAL_SWEEP) {
            for (String token : tokens) {
                switch (token) {
                    case "downup": return false; // rising cut
                    case "updown": return true;  // falling cut
                    default: break;
                }
            }
            return true; // chops fall unless the name says otherwise
        }
        Boolean rightToLeft = null;
        for (String token : tokens) {
            switch (token) {
                case "right", "rl", "rlr" -> rightToLeft = true;
                case "left", "lr", "lrl" -> rightToLeft = false;
                default -> { continue; }
            }
            break;
        }
        // Stock packs lead with a right-hand swing, so an unmarked name reads right-to-left.
        boolean rl = rightToLeft == null || rightToLeft;
        return rl != offHand;
    }

    /**
     * The reach Better Combat will actually test with, once any registered range extensions have been
     * applied.
     *
     * <p>Mirrors {@code TargetFinder.applyAttackRangeModifiers}, which is private: every source is asked
     * about the weapon's <em>base</em> reach rather than a progressively modified one, the modifiers it
     * returns are ordered additions before multiplications, and then folded in that order.
     *
     * <p>Without this, a mod that extends reach — an enchantment, a buff — would move the damage volume
     * and leave the ribbon drawn on the weapon's unmodified range, which is exactly the kind of quiet
     * disagreement between visual and hitbox this whole system exists to prevent.
     *
     * <p>The base handed in is already the composed reach (player attribute + weapon bonus + attack
     * multiplier); extensions apply after all of that, exactly where BC applies them.
     */
    private static double effectiveRange(ClientPlayerEntity player, double baseRange) {
        var sources = AttackRangeExtensions.sources();
        if (sources.isEmpty()) {
            return baseRange;
        }
        var context = new AttackRangeExtensions.Context(player, baseRange);
        var modifiers = sources.stream()
            .map(source -> source.apply(context))
            .sorted(Comparator.comparingInt(AttackRangeExtensions.Modifier::operationOrder))
            .toList();
        double range = baseRange;
        for (var modifier : modifiers) {
            switch (modifier.operation()) {
                case ADD -> range += modifier.value();
                case MULTIPLY -> range *= modifier.value();
            }
        }
        return range;
    }
}
