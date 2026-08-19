package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.config.ConfigSync;
import com.cleannrooster.visceral_combat.config.ServerConfig;
import com.cleannrooster.visceral_combat.config.ServerConfigWrapper;
import com.cleannrooster.visceral_combat.networking.ServerNetworkHandler;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VisceralCombat {
    public static final String MOD_ID = "visceral_combat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ServerConfig config;

    public static void init() {
        AutoConfig.register(ServerConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ServerConfigWrapper.class).getConfig().server;

        PlayerEvent.PLAYER_JOIN.register(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                NetworkManager.sendToPlayer(serverPlayer, new ConfigSync(config));
            }
        });

        ServerNetworkHandler.register();
        ServerEventHandlers.register();


        LOGGER.info("Visceral Combat initialized.");
    }
}
