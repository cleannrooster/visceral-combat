package com.cleannrooster.visceral_combat.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = "server_v1")
public class ServerConfig implements ConfigData {
    @Comment("Forward Lunge on attack")
    public boolean moveAttack = true;
    @Comment("Coefficient applied to lunge impulse when moving in a direction more than 90 degrees away from look direction (0 = no backwards lunge, 1 = full lunge)")
    public float backwardsLungeCoeff = 0.4F;
    @Comment("Forward Lunge decay coefficient")
    public float impulseCoeff = 0.2F;
    @Comment("Forward Lunge max speed coefficient")
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
    public boolean holster = true;
    @Comment("Holster Sprint Speed Boost multiplier")
    public float holsterBoost = 1.4000F;
}
