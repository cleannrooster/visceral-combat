package com.cleannrooster.visceral_combat.particle;

import com.cleannrooster.visceral_combat.VisceralCombat;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModParticles {
    public static final SimpleParticleType SLASH_FLASH = FabricParticleTypes.simple();
    public static final SimpleParticleType SLASH_GLINT = FabricParticleTypes.simple();

    public static void register() {
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(VisceralCombat.MOD_ID, "slash_flash"), SLASH_FLASH);
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(VisceralCombat.MOD_ID, "slash_glint"), SLASH_GLINT);
    }
}
