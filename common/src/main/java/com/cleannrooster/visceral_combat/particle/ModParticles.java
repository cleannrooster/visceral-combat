package com.cleannrooster.visceral_combat.particle;

import com.cleannrooster.visceral_combat.VisceralCombat;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.RegistryKeys;

public class ModParticles {
    public static final DeferredRegister<net.minecraft.particle.ParticleType<?>> PARTICLES =
        DeferredRegister.create(VisceralCombat.MOD_ID, RegistryKeys.PARTICLE_TYPE);

    public static final RegistrySupplier<SimpleParticleType> SLASH_FLASH =
        PARTICLES.register("slash_flash", () -> new SimpleParticleType(false) {});

    public static final RegistrySupplier<SimpleParticleType> SLASH_GLINT =
        PARTICLES.register("slash_glint", () -> new SimpleParticleType(false) {});

    public static void register() {
        PARTICLES.register();
    }
}
