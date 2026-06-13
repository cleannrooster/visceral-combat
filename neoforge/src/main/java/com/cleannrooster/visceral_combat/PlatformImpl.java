package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.util.PlatformEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class PlatformImpl {
    public static PlatformEnvironment getEnvironment() {
        return FMLEnvironment.dist == Dist.CLIENT
                ? PlatformEnvironment.CLIENT
                : PlatformEnvironment.DEDICATED_SERVER;
    }
}