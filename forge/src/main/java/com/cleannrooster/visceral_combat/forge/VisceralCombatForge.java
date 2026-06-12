package com.cleannrooster.visceral_combat.forge;

import com.cleannrooster.visceral_combat.VisceralCombat;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(VisceralCombat.MOD_ID)
public class VisceralCombatForge {
    public VisceralCombatForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        EventBuses.registerModEventBus(VisceralCombat.MOD_ID, modEventBus);

        VisceralCombat.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            VisceralCombatClientForge.init(modEventBus);
        }
    }
}
