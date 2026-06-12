package com.cleannrooster.visceral_combat.networking;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class Packet {

    public record HolsterAssert(boolean bool) implements CustomPayload {
        public static Identifier ID = Identifier.of("visceral_combat", "holster_assert");
        public static final CustomPayload.Id<HolsterAssert> PACKET_ID;
        public static final PacketCodec<RegistryByteBuf, HolsterAssert> CODEC;

        public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }

        public void write(RegistryByteBuf buffer) { buffer.writeBoolean(this.bool); }

        public static HolsterAssert readPacket(RegistryByteBuf buffer) { return new HolsterAssert(buffer.readBoolean()); }

        static {
            PACKET_ID = new CustomPayload.Id<>(ID);
            CODEC = PacketCodec.of(HolsterAssert::write, HolsterAssert::readPacket);
        }
    }

    public record Holster(boolean bool) implements CustomPayload {
        public static Identifier ID = Identifier.of("visceral_combat", "holster");
        public static final CustomPayload.Id<Holster> PACKET_ID;
        public static final PacketCodec<RegistryByteBuf, Holster> CODEC;

        public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }

        public void write(RegistryByteBuf buffer) { buffer.writeBoolean(this.bool); }

        public static Holster readPacket(RegistryByteBuf buffer) { return new Holster(buffer.readBoolean()); }

        static {
            PACKET_ID = new CustomPayload.Id<>(ID);
            CODEC = PacketCodec.of(Holster::write, Holster::readPacket);
        }
    }

    public record Packets(float yaw, float pitch, float range) implements CustomPayload {
        public static Identifier ID = Identifier.of("visceral_combat", "particle");
        public static final CustomPayload.Id<Packets> PACKET_ID;
        public static final PacketCodec<RegistryByteBuf, Packets> CODEC;

        public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }

        public void write(RegistryByteBuf buffer) {
            buffer.writeFloat(this.yaw);
            buffer.writeFloat(this.pitch);
            buffer.writeFloat(this.range);
        }

        public static Packets readPacket(RegistryByteBuf buffer) {
            return new Packets(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }

        static {
            PACKET_ID = new CustomPayload.Id<>(ID);
            CODEC = PacketCodec.of(Packets::write, Packets::readPacket);
        }
    }

    public record Impulse(int id, float mag, float mag2, float x, float y, float z, boolean shouldCheck) implements CustomPayload {
        public static Identifier ID = Identifier.of("visceral_combat", "move_enemy");
        public static final CustomPayload.Id<Impulse> PACKET_ID;
        public static final PacketCodec<RegistryByteBuf, Impulse> CODEC;

        public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }

        public void write(RegistryByteBuf buffer) {
            buffer.writeInt(id);
            buffer.writeFloat(this.mag);
            buffer.writeFloat(this.mag2);
            buffer.writeFloat(this.x);
            buffer.writeFloat(this.y);
            buffer.writeFloat(this.z);
            buffer.writeBoolean(this.shouldCheck);
        }

        public static Impulse readPacket(RegistryByteBuf buffer) {
            int id = buffer.readInt();
            float mag = buffer.readFloat();
            float mag2 = buffer.readFloat();
            float x = buffer.readFloat();
            float y = buffer.readFloat();
            float z = buffer.readFloat();
            boolean shouldCheck = buffer.readBoolean();
            return new Impulse(id, mag, mag2, x, y, z, shouldCheck);
        }

        static {
            PACKET_ID = new CustomPayload.Id<>(ID);
            CODEC = PacketCodec.of(Impulse::write, Impulse::readPacket);
        }
    }
}
