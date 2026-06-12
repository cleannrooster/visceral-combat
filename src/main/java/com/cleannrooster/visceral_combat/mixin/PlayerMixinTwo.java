package com.cleannrooster.visceral_combat.mixin;

import com.cleannrooster.visceral_combat.VisceralCombat;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerMixinTwo {

    @Inject(at = @At("HEAD"), method = "equipStack", cancellable = true)
    public void equipStackHolster(EquipmentSlot slot, ItemStack stack, CallbackInfo returnable) {
        var player = (PlayerEntity) (Object) this;
        if (player instanceof HitstopAccessor accessor && slot.getType().equals(EquipmentSlot.Type.HAND)) {
            if (accessor.isHolster()) returnable.cancel();
        }
    }

    @ModifyReturnValue(at = @At("RETURN"), method = "getMovementSpeed")
    public float getMovementSpeedHolster(float orig) {
        var player = (PlayerEntity) (Object) this;
        if (player instanceof HitstopAccessor accessor && accessor.isHolster()) {
            orig *= player.getAttackCooldownProgress(-1F) < 0.98
                    ? player.getAttackCooldownProgress(-1F) * 0.5F
                    : !player.isSprinting() ? 1.0F : VisceralCombat.config.holsterBoost;
        }
        return orig;
    }

    @Inject(at = @At("RETURN"), method = "getEquippedStack", cancellable = true)
    public void getEquippedStackHolster(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> returnable) {
        var player = (PlayerEntity) (Object) this;
        if (player instanceof HitstopAccessor accessor && accessor.isHolster()) {
            if (slot.getType().equals(EquipmentSlot.Type.HAND)) {
                returnable.setReturnValue(ItemStack.EMPTY);
            }
        }
    }
}
