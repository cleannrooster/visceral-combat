package com.cleannrooster.visceral_combat.mixin;

import com.cleannrooster.visceral_combat.VisceralCombat;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.util.LungeCharges;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class ClientPlayerMixin implements HitstopAccessor {
    protected int hitstopTicks = 0;
    protected int hitstopTime = 0;
    protected Vec3d velocityHitstop;
    protected long lastAttackedTemporary = 0;
    protected boolean holster = false;
    protected boolean shouldClamp;

    @Override
    public boolean isHolster() {
        return VisceralCombat.config.holster && holster;
    }

    @Override
    public boolean shouldClamp() { return shouldClamp; }

    @Override
    public void setShouldClamp(boolean shouldClamp) { this.shouldClamp = shouldClamp; }

    @Override
    public void setHolster(boolean holster) { this.holster = holster; }

    @Inject(at = @At("TAIL"), method = "tick")
    public void tickHitstop(CallbackInfo info) {
        LivingEntity living = (LivingEntity) (Object) this;

        if (this.getHitstopTicks() > 0) {
            if (this.getHitstopTicks() > living.getWorld().getTime() - this.getHitstopTime()) {
                living.limbAnimator.setSpeed(0);
                living.setVelocity(0, 0, 0);
                living.velocityDirty = true;
            } else {
                this.setHitstop(0);
                if (velocityHitstop != null) {
                    living.setVelocity(getVelocityHitstop());
                    setVelocityHitstop(null);
                    living.velocityDirty = true;
                }
            }
        }
    }

    @Inject(at = @At("TAIL"), method = "attack")
    public void onAttackingHitstopTail(Entity target, CallbackInfo info) {
        LivingEntity living = (LivingEntity) (Object) this;
        if (VisceralCombat.config.hitstopEnemies
                && target instanceof HitstopAccessor hitstopAccessor
                && target instanceof LivingEntity livingEntity) {
            int hitstunCooldown = (int) Math.min(10, Math.ceil(20.0 / living.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED)));
            if (target.getWorld().getTime() - hitstopAccessor.getLastHitstopAppliedTime() >= hitstunCooldown) {
                hitstopAccessor.setHitstop((int) Math.ceil(2 * (1.6F / living.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED))));
                hitstopAccessor.setLastHitstopAppliedTime(target.getWorld().getTime());
                if (hitstopAccessor.getVelocityHitstop() == null) {
                    hitstopAccessor.setVelocityHitstop(livingEntity.getVelocity());
                    livingEntity.setVelocity(Vec3d.ZERO);
                    livingEntity.velocityDirty = true;
                }
            }
        }
    }

    // --- HitstopAccessor impl ---

    @Override public int getHitstopTicks() { return hitstopTicks; }
    @Override public void setHitstop(int hitstop) { this.hitstopTicks = hitstop; }
    @Override public void setVelocityHitstop(Vec3d vec3d) { this.velocityHitstop = vec3d; }
    @Override public Vec3d getVelocityHitstop() { return velocityHitstop; }
    @Override public void setHitstopTime(int hitstop) { this.hitstopTime = hitstop; }
    @Override public int getHitstopTime() { return hitstopTime; }

    public Vec3d impulseVector = Vec3d.ZERO;
    @Override public void setImpulseVector(Vec3d vec3d) { impulseVector = vec3d; }
    @Override public Vec3d getImpulseVector() { return impulseVector; }

    public Vec3d impulseDir = null;
    @Override public void setImpulseDir(Vec3d vec3d) { impulseDir = vec3d; }
    @Override public Vec3d getImpulseDir() { return impulseDir; }

    @Override public void setLastAttackedTemporary(long time) { this.lastAttackedTemporary = time; }

    protected long lastHitstopAppliedTime = 0;
    @Override public long getLastHitstopAppliedTime() { return lastHitstopAppliedTime; }
    @Override public void setLastHitstopAppliedTime(long time) { this.lastHitstopAppliedTime = time; }

    private LungeCharges lungeCharges;
    @Override public LungeCharges getLungeCharges() {
        if (lungeCharges == null) {
            lungeCharges = new LungeCharges(VisceralCombat.config != null
                ? VisceralCombat.config.maxCharges : LungeCharges.DEFAULT_MAX_CHARGES);
        }
        return lungeCharges;
    }
}
