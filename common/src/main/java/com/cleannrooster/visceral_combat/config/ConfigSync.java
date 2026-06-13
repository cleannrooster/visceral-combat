package com.cleannrooster.visceral_combat.config;

import com.google.gson.Gson;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ConfigSync(ServerConfig config) implements CustomPayload {
    public static final Identifier ID = Identifier.of("visceral_combat", "config_sync");
    public static final CustomPayload.Id<ConfigSync> PACKET_ID = new CustomPayload.Id<>(ID);
    public static final PacketCodec<PacketByteBuf, ConfigSync> CODEC =
        PacketCodec.of(ConfigSync::write, ConfigSync::read);

    private static final Gson GSON = new Gson();

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }

    public void write(PacketByteBuf buf) {
        buf.writeString(GSON.toJson(this.config));
    }

    public static ConfigSync read(PacketByteBuf buf) {
        return new ConfigSync(GSON.fromJson(buf.readString(), ServerConfig.class));
    }
}
