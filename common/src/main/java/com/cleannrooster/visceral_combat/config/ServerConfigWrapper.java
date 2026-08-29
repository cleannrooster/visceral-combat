package com.cleannrooster.visceral_combat.config;

import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;

@Config(name = "visceral_combat")
public class ServerConfigWrapper extends PartitioningSerializer.GlobalData {
    // Shown in the AutoConfig GUI (reachable via Mod Menu). Server-authoritative: on a remote server
    // the screen edits only this player's local file, and gameplay follows what the server syncs.
    // TransitiveObject is what makes the GUI descend into the partition's fields — without it the
    // object field has no GUI provider and the category renders empty.
    @ConfigEntry.Category("server")
    @ConfigEntry.Gui.TransitiveObject
    public ServerConfig server = new ServerConfig();
}
