package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.config.ConfigSync;
import com.cleannrooster.visceral_combat.config.ServerConfig;
import com.cleannrooster.visceral_combat.config.ServerConfigWrapper;
import com.cleannrooster.visceral_combat.networking.ServerNetworkHandler;
import com.cleannrooster.visceral_combat.particle.ModParticles;
import com.cleannrooster.visceral_combat.util.TickScheduler;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VisceralCombat {
    public static final String MOD_ID = "visceral_combat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ServerConfig config;
    public static ServerConfig clientConfig;

    public static void init() {
        AutoConfig.register(ServerConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ServerConfigWrapper.class).getConfig().server;

        PlayerEvent.PLAYER_JOIN.register(player ->
            NetworkManager.sendToPlayer(player, ConfigSync.ID, ConfigSync.write(config))
        );

        ModParticles.register();
        TickScheduler.register();
        ServerNetworkHandler.register();
        ServerEventHandlers.register();

        LOGGER.info("Visceral Combat initialized.");
    }
}
