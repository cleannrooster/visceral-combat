package com.cleannrooster.visceral_combat.particle;

import com.cleannrooster.visceral_combat.VisceralCombat;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.RegistryKeys;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
        DeferredRegister.create(VisceralCombat.MOD_ID, RegistryKeys.PARTICLE_TYPE);

    public static final RegistrySupplier<DefaultParticleType> SLASH_FLASH =
        PARTICLES.register("slash_flash", () -> new DefaultParticleType(false) {});
    public static final RegistrySupplier<DefaultParticleType> SLASH_GLINT =
        PARTICLES.register("slash_glint", () -> new DefaultParticleType(false) {});

    public static void register() {
        PARTICLES.register();
    }
}
