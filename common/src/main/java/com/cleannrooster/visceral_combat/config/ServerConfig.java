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
    @Comment("Attack Particles")
    public boolean particles = true;
    @Comment("Hitstop on Self")
    public boolean hitstopSelf = true;
    @Comment("Hitstop on Enemies")
    public boolean hitstopEnemies = true;
    @Comment("Enable Holster Mode (H key)")
    public boolean holster = false;
    @Comment("Holster Sprint Speed Boost multiplier")
    public float holsterBoost = 1.4000F;
}
