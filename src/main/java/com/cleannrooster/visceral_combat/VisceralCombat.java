package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.config.ServerConfig;
import com.cleannrooster.visceral_combat.config.ServerConfigWrapper;
import com.cleannrooster.visceral_combat.networking.ServerNetworkHandler;
import com.cleannrooster.visceral_combat.particle.ModParticles;
import com.cleannrooster.visceral_combat.util.TickScheduler;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VisceralCombat implements ModInitializer {
    public static final String MOD_ID = "visceral_combat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ServerConfig config;
    public static ServerConfig clientConfig;

    @Override
    public void onInitialize() {
        AutoConfig.register(ServerConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ServerConfigWrapper.class).getConfig().server;

        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) ->
            ServerPlayNetworking.send(player, new com.cleannrooster.visceral_combat.config.ConfigSync(config))
        );

        ModParticles.register();
        TickScheduler.register();
        ServerNetworkHandler.register();
        ServerEventHandlers.register();

        LOGGER.info("Visceral Combat initialized.");
    }
}
