package com.cleannrooster.visceral_combat.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

@Environment(EnvType.CLIENT)
public class SlashGlintParticle extends SpriteBillboardParticle {

    private final SpriteProvider spriteProvider;
    private final float initialScale;

    protected SlashGlintParticle(ClientWorld world, double x, double y, double z,
                                 double velocityX, double velocityY, double velocityZ,
                                 SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.spriteProvider = spriteProvider;

        this.maxAge = 2 + this.random.nextInt(3);
        this.scale = 0.04F + this.random.nextFloat() * 0.04F;
        this.initialScale = this.scale;

        float type2 = this.random.nextFloat();
        if (type2 < 0.4F) {
            this.red = 1.0F; this.green = 1.0F; this.blue = 1.0F;
        } else if (type2 < 0.7F) {
            this.red = 0.85F; this.green = 0.9F; this.blue = 1.0F;
        } else {
            this.red = 1.0F; this.green = 0.95F; this.blue = 0.85F;
        }

        this.alpha = 1.0F;

        this.velocityX = velocityX * 0.6 + (this.random.nextDouble() - 0.5) * 0.02;
        this.velocityY = velocityY * 0.6 + (this.random.nextDouble() - 0.5) * 0.02;
        this.velocityZ = velocityZ * 0.6 + (this.random.nextDouble() - 0.5) * 0.02;

        this.collidesWithWorld = false;
        this.setSpriteForAge(spriteProvider);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;

        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        float lifeRatio = (float) this.age / (float) this.maxAge;
        float ageFade;
        if (lifeRatio < 0.2F) {
            this.scale = this.initialScale * 1.5F;
            ageFade = 1.0F;
        } else {
            float fadeProgress = (lifeRatio - 0.2F) / 0.8F;
            this.scale = this.initialScale * (1.5F - fadeProgress * 1.2F);
            ageFade = 1.0F - fadeProgress;
        }
        this.alpha = ageFade * proximityFade();

        this.velocityX *= 0.5;
        this.velocityY *= 0.5;
        this.velocityZ *= 0.5;

        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.setSpriteForAge(spriteProvider);
    }

    @Override
    public int getBrightness(float tint) {
        return 0xF000F0;
    }

    private float proximityFade() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 1.0f;
        double dx = mc.player.getX() - this.x;
        double dy = mc.player.getEyeY() - this.y;
        double dz = mc.player.getZ() - this.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq >= 4.00) return 1.0f;
        if (distSq <= 0.64) return 0.0f;
        float t = (float) ((distSq - 0.64) / (4.00 - 0.64));
        return t * t;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new SlashGlintParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.spriteProvider);
        }
    }
}
