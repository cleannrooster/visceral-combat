package com.cleannrooster.visceral_combat.networking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class Packet {

    public record HolsterAssert(boolean bool) {
        public static final Identifier ID = new Identifier("visceral_combat", "holster_assert");
        public void write(PacketByteBuf buf) { buf.writeBoolean(this.bool); }
        public static HolsterAssert read(PacketByteBuf buf) { return new HolsterAssert(buf.readBoolean()); }
    }

    public record Holster(boolean bool) {
        public static final Identifier ID = new Identifier("visceral_combat", "holster");
        public void write(PacketByteBuf buf) { buf.writeBoolean(this.bool); }
        public static Holster read(PacketByteBuf buf) { return new Holster(buf.readBoolean()); }
    }

    public record Packets(float yaw, float pitch, float range) {
        public static final Identifier ID = new Identifier("visceral_combat", "particle");
        public void write(PacketByteBuf buf) {
            buf.writeFloat(this.yaw);
            buf.writeFloat(this.pitch);
            buf.writeFloat(this.range);
        }
        public static Packets read(PacketByteBuf buf) {
            return new Packets(buf.readFloat(), buf.readFloat(), buf.readFloat());
        }
    }

    public record Impulse(int id, float mag, float mag2, float x, float y, float z, boolean shouldCheck) {
        public static final Identifier ID = new Identifier("visceral_combat", "move_enemy");
        public void write(PacketByteBuf buf) {
            buf.writeInt(this.id);
            buf.writeFloat(this.mag);
            buf.writeFloat(this.mag2);
            buf.writeFloat(this.x);
            buf.writeFloat(this.y);
            buf.writeFloat(this.z);
            buf.writeBoolean(this.shouldCheck);
        }
        public static Impulse read(PacketByteBuf buf) {
            return new Impulse(buf.readInt(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
        }
    }

    public record LungeAck() {
        public static final Identifier ID = new Identifier("visceral_combat", "lunge_ack");
        public void write(PacketByteBuf buf) {}
        public static LungeAck read(PacketByteBuf buf) { return new LungeAck(); }
    }
}
