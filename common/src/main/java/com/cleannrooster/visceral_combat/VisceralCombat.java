package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.config.ConfigSync;
import com.cleannrooster.visceral_combat.config.ServerConfig;
import com.cleannrooster.visceral_combat.config.ServerConfigWrapper;
import com.cleannrooster.visceral_combat.networking.ServerNetworkHandler;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VisceralCombat {
    public static final String MOD_ID = "visceral_combat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ServerConfig config;

    /** The server running in this JVM (integrated or dedicated), for the config save re-sync. */
    private static MinecraftServer runningServer;

    public static void init() {
        AutoConfig.register(ServerConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        var holder = AutoConfig.getConfigHolder(ServerConfigWrapper.class);
        config = holder.getConfig().server;

        LifecycleEvent.SERVER_STARTED.register(server -> runningServer = server);
        LifecycleEvent.SERVER_STOPPED.register(server -> runningServer = null);

        // A save from the config screen (reachable via Mod Menu) applies immediately: the running
        // server reads the same object the GUI mutated, and connected clients get the same re-sync
        // they'd get on join — no rejoin needed. Saving on a pure client (remote server) only writes
        // the local file; that server's own synced config keeps governing gameplay.
        holder.registerSaveListener((h, wrapper) -> {
            config = wrapper.server;
            MinecraftServer server = runningServer;
            if (server != null) {
                server.execute(() -> {
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        NetworkManager.sendToPlayer(player, new ConfigSync(wrapper.server));
                    }
                });
            }
            return ActionResult.SUCCESS;
        });

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
