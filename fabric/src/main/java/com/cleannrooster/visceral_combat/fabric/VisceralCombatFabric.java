package com.cleannrooster.visceral_combat.fabric;

import com.cleannrooster.visceral_combat.VisceralCombat;
import net.fabricmc.api.ModInitializer;

public class VisceralCombatFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        VisceralCombat.init();
    }
}
