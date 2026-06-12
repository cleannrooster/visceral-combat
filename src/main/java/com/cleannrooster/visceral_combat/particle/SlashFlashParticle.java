package com.cleannrooster.visceral_combat.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;

@Environment(EnvType.CLIENT)
public class SlashFlashParticle extends SpriteBillboardParticle {

    private final SpriteProvider spriteProvider;
    private final float startScale;

    protected SlashFlashParticle(ClientWorld world, double x, double y, double z,
                                 double velocityX, double velocityY, double velocityZ,
                                 SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.spriteProvider = spriteProvider;

        this.maxAge = 3 + this.random.nextInt(2);
        this.scale = 0.12F + this.random.nextFloat() * 0.06F;
        this.startScale = this.scale;

        float colorVariance = 0.9F + this.random.nextFloat() * 0.1F;
        this.red = colorVariance;
        this.green = colorVariance;
        this.blue = 0.95F + this.random.nextFloat() * 0.05F;
        this.alpha = 1.0F;

        this.velocityX = velocityX * 0.3;
        this.velocityY = velocityY * 0.3;
        this.velocityZ = velocityZ * 0.3;

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

        if (lifeRatio < 0.3F) {
            this.scale = this.startScale * (1.0F + lifeRatio * 4.0F);
        } else {
            this.scale = this.startScale * (1.0F + 1.2F) * (1.0F - (lifeRatio - 0.3F) / 0.7F);
        }

        float ageFade = 1.0F - (lifeRatio * lifeRatio);
        this.alpha = ageFade * proximityFade();

        this.red = Math.max(0.7F, this.red - lifeRatio * 0.15F);
        this.green = Math.max(0.75F, this.green - lifeRatio * 0.1F);

        this.velocityX *= 0.7;
        this.velocityY *= 0.7;
        this.velocityZ *= 0.7;

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
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();
        double hideThresh  = firstPerson ? 4.00 : 0.64;
        double fadeThresh  = firstPerson ? 9.00 : 4.00;
        if (distSq >= fadeThresh) return 1.0f;
        if (distSq <= hideThresh) return 0.0f;
        float t = (float) ((distSq - hideThresh) / (fadeThresh - hideThresh));
        return t * t;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new SlashFlashParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.spriteProvider);
        }
    }
}
