package com.cleannrooster.visceral_combat.client.combat;

import com.cleannrooster.visceral_combat.combat.AttackSwing;
import com.cleannrooster.visceral_combat.combat.SlashShape;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.api.client.AttackRangeExtensions;
import net.bettercombat.logic.PlayerAttackHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;

/**
 * Reads an {@link AttackSwing} off the Better Combat attack that is (or would be) firing it.
 *
 * <p>Extracted from {@code CombatEventsClient} so that everything which needs to know what a swing
 * will actually do — the slash ribbon and the target-assist system — reads the identical description.
 * The ribbon uses it to draw the real damage volume; target acquisition uses it to judge whether an
 * enemy is within reasonable engagement distance of the attack the player would throw next. Both
 * therefore agree with Better Combat and with each other by construction.
 */
@Environment(EnvType.CLIENT)
public final class SwingReader {

    private SwingReader() {
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
    public static @Nullable AttackSwing describe(ClientPlayerEntity player, AttackHand attackHand) {
        WeaponAttributes.Attack attack = attackHand.attack();
        if (attack == null) {
            return null;
        }
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
