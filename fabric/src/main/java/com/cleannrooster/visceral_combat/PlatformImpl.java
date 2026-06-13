package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.util.PlatformEnvironment;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class PlatformImpl {
    public static PlatformEnvironment getEnvironment() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                ? PlatformEnvironment.CLIENT
                : PlatformEnvironment.DEDICATED_SERVER;
    }
}