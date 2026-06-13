package com.cleannrooster.visceral_combat.neoforge;

import com.cleannrooster.visceral_combat.VisceralCombat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(VisceralCombat.MOD_ID)
public class VisceralCombatNeoForge {
    public VisceralCombatNeoForge(IEventBus modEventBus) {
        VisceralCombat.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            VisceralCombatClientNeoForge.init(modEventBus);
        }
    }
}
