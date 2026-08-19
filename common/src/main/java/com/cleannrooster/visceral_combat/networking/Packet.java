package com.cleannrooster.visceral_combat.networking;

import com.cleannrooster.visceral_combat.combat.AttackSwing;
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

    /**
     * The attacker's own report of a swing's damage-volume parameters, sent the moment the attack
     * starts — only that client knows which Better Combat attack fired. Carries no attacker id: the
     * server stamps the sender's entity id from the connection when it relays, so a modified client
     * cannot attribute a swing to someone else.
     */
    public record SwingC2S(AttackSwing swing) implements CustomPayload {
        public static final Identifier ID = Identifier.of("visceral_combat", "swing");
        public static final CustomPayload.Id<SwingC2S> PACKET_ID = new CustomPayload.Id<>(ID);
        public static final PacketCodec<PacketByteBuf, SwingC2S> CODEC = PacketCodec.of(
            (val, buf) -> val.swing.write(buf),
            buf -> new SwingC2S(AttackSwing.read(buf))
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }
    }

    /**
     * A swing relayed to everyone who can see the attacker, so every client draws the same ribbon over
     * the same volume. The swing's parameters were clamped server-side on the way through.
     */
    public record SwingS2C(int attackerId, AttackSwing swing) implements CustomPayload {
        public static final Identifier ID = Identifier.of("visceral_combat", "swing_broadcast");
        public static final CustomPayload.Id<SwingS2C> PACKET_ID = new CustomPayload.Id<>(ID);
        public static final PacketCodec<PacketByteBuf, SwingS2C> CODEC = PacketCodec.of(
            (val, buf) -> { buf.writeVarInt(val.attackerId); val.swing.write(buf); },
            buf -> new SwingS2C(buf.readVarInt(), AttackSwing.read(buf))
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

    /**
     * Authoritative lunge-charge state pushed from server to client so the HUD reflects the server's
     * ledger. Carries each slot's recovery start/ready world tick; the client compares them against its
     * own (server-synced) world clock, so the values stay correct regardless of when the packet lands.
     */
    public record ChargeSync(long[] startTick, long[] readyTick) implements CustomPayload {
        public static final Identifier ID = Identifier.of("visceral_combat", "charge_sync");
        public static final CustomPayload.Id<ChargeSync> PACKET_ID = new CustomPayload.Id<>(ID);
        public static final PacketCodec<PacketByteBuf, ChargeSync> CODEC = PacketCodec.of(
            (val, buf) -> {
                int n = Math.min(val.startTick.length, val.readyTick.length);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeLong(val.startTick[i]);
                    buf.writeLong(val.readyTick[i]);
                }
            },
            buf -> {
                int n = buf.readVarInt();
                long[] start = new long[n];
                long[] ready = new long[n];
                for (int i = 0; i < n; i++) {
                    start[i] = buf.readLong();
                    ready[i] = buf.readLong();
                }
                return new ChargeSync(start, ready);
            }
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return PACKET_ID; }
    }
}
