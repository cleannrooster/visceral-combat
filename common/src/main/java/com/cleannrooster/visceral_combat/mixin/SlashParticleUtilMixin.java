package com.cleannrooster.visceral_combat.mixin;

import com.cleannrooster.visceral_combat.VisceralCombatClient;
import net.bettercombat.client.particle.SlashParticleUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses Better Combat's own weapon trail particles while this mod's slash ribbons are standing
 * in for them, so a swing doesn't draw two competing trails.
 *
 * <p>A mixin because Better Combat offers no event around its trail spawning — every trail it emits,
 * from every call site (including the {@code SpawnArgs} overload, which delegates here), funnels
 * through this one static method, making it the single narrowest interception point. Nothing else of
 * BC's is touched: its {@code isShowingWeaponTrails} client setting keeps working and its config is
 * never written to.
 *
 * <p>The suppression is conditional on the ribbons actually replacing the trails: it needs the synced
 * server config present, the override enabled, <em>and</em> the ribbons turned on — a server that
 * disables the ribbons gets Better Combat's trails back rather than no trail at all.
 */
@Mixin(SlashParticleUtil.class)
public class SlashParticleUtilMixin {

    @Inject(
        method = "spawnParticles(Lnet/minecraft/client/network/AbstractClientPlayerEntity;ZFLjava/util/List;Lnet/bettercombat/api/fx/TrailAppearance;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void visceralCombat$suppressTrails(CallbackInfo ci) {
        var config = VisceralCombatClient.clientConfig;
        if (config != null && config.overrideWeaponTrails && config.particles) {
            ci.cancel();
        }
    }
}
