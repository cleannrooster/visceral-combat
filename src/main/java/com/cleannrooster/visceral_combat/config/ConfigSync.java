package com.cleannrooster.visceral_combat.config;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class ConfigSync {
    public static final Identifier ID = new Identifier("visceral_combat", "config_sync");
    private static final Gson GSON = new Gson();

    public static PacketByteBuf write(ServerConfig config) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(GSON.toJson(config));
        return buf;
    }

    public static ServerConfig read(PacketByteBuf buf) {
        return GSON.fromJson(buf.readString(), ServerConfig.class);
    }
}
