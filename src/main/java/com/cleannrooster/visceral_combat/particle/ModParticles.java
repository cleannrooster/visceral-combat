package com.cleannrooster.visceral_combat.particle;

import com.cleannrooster.visceral_combat.VisceralCombat;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModParticles {
    public static final DefaultParticleType SLASH_FLASH = FabricParticleTypes.simple(false);
    public static final DefaultParticleType SLASH_GLINT = FabricParticleTypes.simple(false);

    public static void register() {
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(VisceralCombat.MOD_ID, "slash_flash"), SLASH_FLASH);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(VisceralCombat.MOD_ID, "slash_glint"), SLASH_GLINT);
    }
}
