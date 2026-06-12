package com.cleannrooster.visceral_combat.mixin;

import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public class PlayerMixin {
    @Shadow
    public PlayerEntity player;

    @Inject(at = @At("RETURN"), method = "getMainHandStack", cancellable = true)
    public void getMainHandStack(CallbackInfoReturnable<ItemStack> returnable) {
        if (player instanceof HitstopAccessor hit && hit.isHolster()) {
            returnable.setReturnValue(ItemStack.EMPTY);
        }
    }
}
