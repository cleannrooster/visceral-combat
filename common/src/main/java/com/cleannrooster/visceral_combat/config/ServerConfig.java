package com.cleannrooster.visceral_combat.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = "server_v2")
public class ServerConfig implements ConfigData {
    @Comment("Forward Lunge on attack")
    public boolean moveAttack = true;
    @Comment("Require the player to be sprinting in order to lunge on attack")
    public boolean requireSprint = true;
    @Comment("Lunge mode: ARCADE (move in input direction), DUELING (always lunge forward), HYBRID (forward + configurable side influence)")
    public LungeMode lungeMode = LungeMode.HYBRID;
    @Comment("HYBRID mode only: how much left/right movement input is blended into the forward lunge (0 = pure forward, 1 = full sideways influence)")
    public float hybridSideCoeff = 0.4F;
    @Comment("ARCADE mode only: coefficient applied to lunge when moving more than 90 degrees away from look direction (0 = no backwards lunge, 1 = full)")
    public float backwardsLungeCoeff = 0.4F;
    @Comment("Forward Lunge speed coeff")
    public float lungeSpeed = 2.4F;
    @Comment("Forward Lunge speed cap")
    public float lungeSpeedCap = 4F;
    @Comment("Minimum attack speed used for lunge/charge calculations (attack speeds below this are treated as this)")
    public float minAttackSpeed = 0.8F;
    @Comment("Maximum attack speed used for lunge/charge calculations (attack speeds above this are treated as this)")
    public float maxAttackSpeed = 4.0F;
    @Comment("Enable the lunge charge system (if false, lunges never require or consume charges and the charge HUD is hidden)")
    public boolean chargesEnabled = true;
    @Comment("Number of lunge charges the player has")
    public int maxCharges = 4;
    @Comment("Lunge charge recovery time, as a multiple of the weapon's attack cooldown (e.g. 8 = a spent charge takes 8 full attack cooldowns to recover; higher = slower recovery)")
    public float chargeRecoveryTime = 8F;
    @Comment("Impulse decay coefficient")
    public float impulseCoeff = 0.2F;
    @Comment("Impulse speed coefficient")
    public float maxImpulse = 1.2F;
    @Comment("Impact recoil on attacker when hitting enemy")
    public boolean impactRecoil = true;
    @Comment("Enemy Directional Impact Move")
    public boolean impactEnemy = true;
    @Comment("Enemy directional coefficient")
    public float dirCoeff = 1F;
    @Comment("Draw the slash ribbon: an arc laid over the weapon's real Better Combat hitbox")
    public boolean particles = true;
    @Comment("Replace Better Combat's own weapon trail particles with this mod's slash ribbons (only suppresses them while the ribbons are enabled; if false, both are drawn)")
    public boolean overrideWeaponTrails = true;
    @Comment("Show each player's slash ribbons to everyone nearby (if false, players only see their own)")
    public boolean ribbonsVisibleToOthers = true;
    @Comment("Hitstop on Self")
    public boolean hitstopSelf = true;
    @Comment("Hitstop on Enemies")
    public boolean hitstopEnemies = true;
    @Comment("Enable Holster Mode (H key)")
    public boolean holster = false;
    @Comment("Holster Sprint Speed Boost multiplier")
    public float holsterBoost = 1.4000F;
    @Comment("Target assist mode: OFF (pure manual aim), SOFT (highlight + slight swing tracking), RPG (highlight + swing tracking + target-aware attack lunge)")
    public TargetAssistMode targetAssistMode = TargetAssistMode.RPG;
    @Comment("Target assist: how far off the crosshair (degrees) an enemy may be and still be acquired as the intended target")
    public float assistAcquisitionAngle = 30F;
    @Comment("Target assist: how far beyond the weapon's real reach (blocks) a target may be acquired — the attack lunge can close roughly this much distance")
    public float assistAcquisitionRangeBonus = 0.6F;
    @Comment("Target assist: maximum total facing correction over one swing, degrees away from the direction the attack started in")
    public float assistMaxTrackingAngle = 20F;
    @Comment("Target assist: maximum yaw correction per tick, degrees")
    public float assistMaxYawPerTick = 3.5F;
    @Comment("Target assist: maximum pitch correction per tick, degrees")
    public float assistMaxPitchPerTick = 1.5F;
    @Comment("Target assist (RPG): maximum distance (blocks) the attack lunge will try to close toward the committed target")
    public float assistMaxApproachDistance = 0.75F;
    @Comment("Target assist (RPG): how strongly the attack lunge bends toward the committed target (0 = lunge is unaffected, 1 = lunge goes straight at them)")
    public float assistLungeStrength = 0.65F;
    @Comment("Target assist: draw a subtle ground ring under the currently acquired target")
    public boolean assistHighlight = true;
    @Comment("Target assist: score advantage the currently highlighted target keeps, so the highlight doesn't flicker between two nearby enemies (1 = none)")
    public float assistTargetStickiness = 1.25F;
}
