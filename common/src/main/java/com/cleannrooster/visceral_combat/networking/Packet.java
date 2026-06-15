package com.cleannrooster.visceral_combat.networking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class Packet {

    public record HolsterAssert(boolean bool) implements CustomPayload {
        public static final Identifier ID = Identifier.of("visceral_combat", "holster_assert");
        public static final CustomPayload.Id<HolsterAssert> PACKET_ID = new CustomPayload.Id<>(ID);
        public static final PacketCodec<PacketByteBuf, HolsterAssert> CODEC =
            PacketCodec.of((val, buf) -> buf.writeBoolean(val.bool), buf -> new HolsterAssert(buf.readBoolean()));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }
    }

    public record Holster(boolean bool) implements CustomPayload {
        public static final Identifier ID = Identifier.of("visceral_combat", "holster");
        public static final CustomPayload.Id<Holster> PACKET_ID = new CustomPayload.Id<>(ID);
        public static final PacketCodec<PacketByteBuf, Holster> CODEC =
            PacketCodec.of((val, buf) -> buf.writeBoolean(val.bool), buf -> new Holster(buf.readBoolean()));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }
    }

    public record Packets(float yaw, float pitch, float range) implements CustomPayload {
        public static final Identifier ID = Identifier.of("visceral_combat", "particle");
        public static final CustomPayload.Id<Packets> PACKET_ID = new CustomPayload.Id<>(ID);
        public static final PacketCodec<PacketByteBuf, Packets> CODEC = PacketCodec.of(
            (val, buf) -> { buf.writeFloat(val.yaw); buf.writeFloat(val.pitch); buf.writeFloat(val.range); },
            buf -> new Packets(buf.readFloat(), buf.readFloat(), buf.readFloat())
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }
    }

    public record Impulse(int id, float mag, float mag2, float x, float y, float z, boolean shouldCheck) implements CustomPayload {
        public static final Identifier ID = Identifier.of("visceral_combat", "move_enemy");
        public static final CustomPayload.Id<Impulse> PACKET_ID = new CustomPayload.Id<>(ID);
        public static final PacketCodec<PacketByteBuf, Impulse> CODEC = PacketCodec.of(
            (val, buf) -> {
                buf.writeInt(val.id); buf.writeFloat(val.mag); buf.writeFloat(val.mag2);
                buf.writeFloat(val.x); buf.writeFloat(val.y); buf.writeFloat(val.z);
                buf.writeBoolean(val.shouldCheck);
            },
            buf -> new Impulse(buf.readInt(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean())
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }
    }

    public record LungeAck() implements CustomPayload {
        public static final Identifier ID = Identifier.of("visceral_combat", "lunge_ack");
        public static final CustomPayload.Id<LungeAck> PACKET_ID = new CustomPayload.Id<>(ID);
        public static final PacketCodec<PacketByteBuf, LungeAck> CODEC =
            PacketCodec.of((val, buf) -> {}, buf -> new LungeAck());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }
    }
}
