package com.cleannrooster.visceral_combat.fabric;

import com.cleannrooster.visceral_combat.config.ServerConfigWrapper;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;

/**
 * Optional Mod Menu integration: hands Mod Menu the AutoConfig-generated screen for the mod's config,
 * so it can be opened from the mods list.
 *
 * <p>This class is only ever loaded through the {@code modmenu} entrypoint in {@code fabric.mod.json},
 * which Fabric Loader resolves solely when Mod Menu is installed — with Mod Menu absent, nothing here
 * (including the Mod Menu API types) is touched, which is what keeps the dependency compile-only.
 *
 * <p>The screen edits the local {@code visceral_combat/server_v2.json5} — authoritative for worlds
 * this player hosts (singleplayer/LAN), where saving also re-syncs the running config to connected
 * clients (see the save listener in {@code VisceralCombat}). On a remote server the values shown are
 * the local defaults, not the server's, and gameplay keeps following what that server syncs.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfig.getConfigScreen(ServerConfigWrapper.class, parent).get();
    }
}
