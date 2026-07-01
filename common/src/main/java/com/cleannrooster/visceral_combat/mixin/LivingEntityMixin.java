package com.cleannrooster.visceral_combat.mixin;

import com.cleannrooster.visceral_combat.VisceralCombat;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.util.EntityHelper;
import com.cleannrooster.visceral_combat.util.LungeCharges;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements HitstopAccessor {
    protected int hitstopTicks = 0;
    protected Vec3d velocityHitstop = Vec3d.ZERO;
    protected int hitstopTime = 0;
    protected boolean holster = false;
    private Vec3d impulseVector = Vec3d.ZERO;
    // Directional knockback redirect (swing-sweep direction) stamped per hit, applied once on release.
    private Vec3d impulseDir = null;
    private long lastAttackedTemporary = 0;
    private long lastHitstopAppliedTime = 0;
    protected boolean shouldClamp = false;
    // Authoritative lunge charge ledger for this entity (server-side enforcement); lazily created.
    private LungeCharges lungeCharges;

    @Inject(at = @At("RETURN"), method = "getOffHandStack", cancellable = true)
    public void getOffHandStackHolster(CallbackInfoReturnable<ItemStack> returnable) {
        if (this.holster) returnable.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(at = @At("HEAD"), method = "swapHandStacks", cancellable = true)
    private void swapHandStacksHolster(CallbackInfo callbackInfo) {
        if (this.holster) callbackInfo.cancel();
    }

    @Inject(at = @At("RETURN"), method = "getMainHandStack", cancellable = true)
    public void getMainHandStackHolster(CallbackInfoReturnable<ItemStack> returnable) {
        if (this.holster) returnable.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(at = @At("TAIL"), method = "tick")
    public void tickHitstop(CallbackInfo info) {
        LivingEntity living = (LivingEntity) (Object) this;

        List<Entity> list  = EntityHelper.getEntitiesInFront(living, (living instanceof PlayerEntity player ? (float) player.getEntityInteractionRange() : 4F) * 0.6F);
        List<Entity> list2 = EntityHelper.getEntitiesInFront(living, living.getWidth() + 0.5F);

        boolean trueBool = !list2.isEmpty() || !list.isEmpty();

        // While frozen, nothing may write velocity (the freeze owns it). Holding the impulse
        // here instead of decaying it through the freeze also preserves lunge momentum until release.
        if (this.getHitstopTicks() == 0 && !living.getWorld().isClient() && getImpulseVector() != null && getImpulseVector().length() > 0.02) {
            var shouldSub   = this.shouldClamp() && trueBool;
            if (!living.isOnGround()) {
                setImpulseVector(getImpulseVector().multiply(0.8F));
            }
            var toAdd            = getImpulseVector().multiply(1.0F);
            var totalVelocity    = living.getVelocity().add(toAdd);
            var defaultMoveSpeed = 0.1F;
            var entityMoveSpeed  = living.getMovementSpeed();
            var coeff            = 4F * VisceralCombat.config.maxImpulse;
            var cap              = (defaultMoveSpeed + entityMoveSpeed * 0.5F) * coeff;
            var capSq            = cap * cap;
            var finalVel = (living.getVelocity().lengthSquared() > capSq
                    ? totalVelocity.normalize().multiply(living.getVelocity().length())
                    : totalVelocity.lengthSquared() > capSq
                        ? totalVelocity.normalize().multiply(cap)
                        : totalVelocity);
            var sub = shouldSub ? totalVelocity.normalize().multiply(cap * 0.5) : Vec3d.ZERO;
            if (finalVel.lengthSquared() < sub.lengthSquared()) {
                finalVel = Vec3d.ZERO;
            } else {
                finalVel = finalVel.subtract(sub);
            }
            living.setVelocity(finalVel);
            setImpulseVector(getImpulseVector().multiply(VisceralCombat.config.impulseCoeff));
            living.velocityModified = true;
        }
        if (getImpulseVector() == null || getImpulseVector().length() < 0.02) {
            this.setShouldClamp(false);
            this.setImpulseVector(Vec3d.ZERO);
        }
        // Drop a stamped redirect that never reached a freeze, so it can't steer a later hit.
        if (this.getHitstopTicks() == 0 && getVelocityHitstop() == null && getImpulseDir() != null) {
            this.setImpulseDir(null);
        }

        if (this.getHitstopTicks() > 0) {
            // Pure freeze: hold the accumulated launch vector untouched. It is written only by hit
            // events (see applyDamageHitstop), so its magnitude is independent of hitstop duration.
            living.setVelocity(0, 0, 0);
            living.velocityDirty = true;
            living.velocityModified = true;
            living.limbAnimator.updateLimbs(2, 0.25F);
            setHitstop(getHitstopTicks() - 1);
        } else if (getVelocityHitstop() != null) {
            living.setVelocity(directionalRelease(getVelocityHitstop(), living));
            setVelocityHitstop(null);
            setImpulseDir(null);
            living.velocityDirty = true;
            living.velocityModified = true;
        }
    }

    /**
     * Clamps a hitstop launch vector to the same ceiling the impulse system uses, so compounded
     * knockback can grow with each hit but never runs away.
     */
    private Vec3d clampHitstopVelocity(Vec3d v, LivingEntity living) {
        double cap = (0.1F + living.getMovementSpeed() * 0.5F) * 4F * VisceralCombat.config.maxImpulse;
        return v.lengthSquared() > cap * cap ? v.normalize().multiply(cap) : v;
    }

    /**
     * Applies the directional redirect (swing-sweep direction) exactly once, on release. The
     * horizontal magnitude of the accumulated knockback is repointed along the latest hit's
     * direction; the vertical component is preserved. With no recorded direction the raw vector is
     * released. The dirCoeff factor is applied here a single time, never re-applied per tick or per
     * hit, which is what kept the old scheduled-overwrite path order-dependent.
     */
    private Vec3d directionalRelease(Vec3d v, LivingEntity living) {
        Vec3d dir = getImpulseDir();
        Vec3d result = v;
        if (dir != null) {
            double horizMag = 0.4 * VisceralCombat.config.dirCoeff
                * Math.sqrt(v.x * v.x + v.z * v.z);
            result = dir.multiply(horizMag).add(0, v.y, 0);
        }
        return clampHitstopVelocity(result, living);
    }

    @Inject(at = @At("RETURN"), method = "damage", cancellable = true)
    protected void applyDamageHitstop(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
        LivingEntity living = (LivingEntity) (Object) this;
        if (VisceralCombat.config.hitstopEnemies
                && source.isDirect()
                && source.getAttacker() instanceof HitstopAccessor
                && source.getAttacker() instanceof PlayerEntity hurt
                && hurt.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED) != null) {
            int hitstunCooldown = (int) Math.min(10, Math.ceil(20.0 / hurt.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED)));
            boolean canRefresh = living.getWorld().getTime() - this.getLastHitstopAppliedTime() >= hitstunCooldown;
            boolean frozen = this.getHitstopTicks() > 0 || this.getVelocityHitstop() != null;
            if (canRefresh || frozen) {
                // Refreshing the freeze duration stays throttled so one fast weapon can't extend it
                // every tick; knockback compounding below is not throttled, so distinct hits stack.
                if (canRefresh) {
                    this.setHitstop(Math.max(this.getHitstopTicks(),
                        (int) Math.ceil(2 * (1.6F / hurt.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED)))));
                    this.setLastHitstopAppliedTime(living.getWorld().getTime());
                }
                // Compound this hit's knockback once, at the damage event. Because it is tied to the
                // hit rather than the tick loop, distinct hits — even from a different source during
                // the freeze — stack, while a single hit can never inflate with hitstop duration.
                Vec3d kb = living.getVelocity();
                if (kb.lengthSquared() > 1.0E-8) {
                    this.setVelocityHitstop(this.getVelocityHitstop() == null
                        ? kb
                        : clampHitstopVelocity(this.getVelocityHitstop().add(kb), living));
                    living.setVelocity(Vec3d.ZERO);
                    living.velocityDirty = true;
                    living.velocityModified = true;
                }
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "tickMovement", cancellable = true)
    public void tickMovementHitstop(CallbackInfo info) {
        LivingEntity living = (LivingEntity) (Object) this;
        if (living instanceof HitstopAccessor accessor && accessor.getHitstopTicks() > 0) {
            info.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "updateLimbs", cancellable = true)
    public void updateLimbsHitstop(boolean flutter, CallbackInfo info) {
        if (getHitstopTicks() > 0) info.cancel();
    }

    @Inject(at = @At("HEAD"), method = "updatePostDeath", cancellable = true)
    protected void updatePostDeathHitstop(CallbackInfo info) {
        if (getHitstopTicks() > 0) info.cancel();
    }

    // --- HitstopAccessor impl ---

    @Override public int getHitstopTicks() { return hitstopTicks; }
    @Override public void setHitstop(int hitstop) { this.hitstopTicks = hitstop; }
    @Override public void setVelocityHitstop(Vec3d vec3d) { this.velocityHitstop = vec3d; }
    @Override public Vec3d getVelocityHitstop() { return velocityHitstop; }
    @Override public void setHitstopTime(int hitstop) { this.hitstopTime = hitstop; }
    @Override public int getHitstopTime() { return hitstopTime; }
    @Override public void setImpulseVector(Vec3d vec3d) { this.impulseVector = vec3d; }
    @Override public Vec3d getImpulseVector() { return impulseVector; }
    @Override public void setImpulseDir(Vec3d vec3d) { this.impulseDir = vec3d; }
    @Override public Vec3d getImpulseDir() { return impulseDir; }
    @Override public void setLastAttackedTemporary(long time) { lastAttackedTemporary = time; }
    @Override public long getLastHitstopAppliedTime() { return lastHitstopAppliedTime; }
    @Override public void setLastHitstopAppliedTime(long time) { lastHitstopAppliedTime = time; }
    @Override public boolean isHolster() { return holster; }
    @Override public void setHolster(boolean holster) { this.holster = holster; }
    @Override public boolean shouldClamp() { return shouldClamp; }
    @Override public void setShouldClamp(boolean shouldClamp) { this.shouldClamp = shouldClamp; }
    @Override public LungeCharges getLungeCharges() {
        if (lungeCharges == null) {
            lungeCharges = new LungeCharges(VisceralCombat.config != null
                ? VisceralCombat.config.maxCharges : LungeCharges.DEFAULT_MAX_CHARGES);
        }
        return lungeCharges;
    }
}
