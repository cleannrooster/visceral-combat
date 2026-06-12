package com.cleannrooster.visceral_combat.config;

import com.google.gson.Gson;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ConfigSync(ServerConfig config) implements CustomPayload {
    public static Identifier ID = Identifier.of("visceral_combat", "config_sync");
    public static final CustomPayload.Id<ConfigSync> PACKET_ID;
    public static final PacketCodec<RegistryByteBuf, ConfigSync> CODEC;
    private static final Gson gson;

    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }

    public void write(PacketByteBuf buffer) {
        buffer.writeString(gson.toJson(this.config));
    }

    public static ConfigSync read(PacketByteBuf buffer) {
        return new ConfigSync(new Gson().fromJson(buffer.readString(), ServerConfig.class));
    }

    static {
        PACKET_ID = new CustomPayload.Id<>(ID);
        CODEC = PacketCodec.of(ConfigSync::write, ConfigSync::read);
        gson = new Gson();
    }
}
