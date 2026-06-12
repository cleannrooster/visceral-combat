package com.cleannrooster.visceral_combat.config;

import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class ConfigSync {
    public static final Identifier ID = new Identifier("visceral_combat", "config_sync");
    private static final Gson GSON = new Gson();

    public static PacketByteBuf write(ServerConfig config) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(GSON.toJson(config));
        return buf;
    }

    public static ServerConfig read(PacketByteBuf buf) {
        return GSON.fromJson(buf.readString(), ServerConfig.class);
    }
}
