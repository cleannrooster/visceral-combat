package com.cleannrooster.visceral_combat.networking;

import com.cleannrooster.visceral_combat.VisceralCombat;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.combat.AttackSwing;
import com.cleannrooster.visceral_combat.config.ServerConfig;
import com.cleannrooster.visceral_combat.util.EntityHelper;
import com.cleannrooster.visceral_combat.util.LungeCharges;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class ServerNetworkHandler {

    // Grace applied to the server's readiness check so network jitter never denies a lunge the client
    // legitimately predicted. It cancels across consecutive spends (applied to both check and timer).
    private static final long LUNGE_GRACE_TICKS = 3L;

    // Far enough that a swing is still legible on screen, near enough that a busy server is not
    // broadcasting every attack to everyone in the dimension.
    private static final double SWING_VISIBLE_RANGE_SQ = 96.0 * 96.0;

    /**
     * Authoritative lunge-charge gate: spends from the player's server-side ledger and reconciles the
     * client HUD via ChargeSync. Returns true if a charge was available (or if enforcement is off
     * because no config is loaded). A legit client already gated itself, so this only bites a modified
     * client or corrects desync.
     */
    private static boolean gateLungeCharge(ServerPlayerEntity player) {
        ServerConfig config = VisceralCombat.config;
        // No config, charge system disabled, or non-player: no gating (lunge always granted).
        if (config == null || !config.chargesEnabled || !(player instanceof HitstopAccessor accessor)) return true;
        LungeCharges charges = accessor.getLungeCharges();
        charges.setSize(config.maxCharges);
        double attackSpeed = Math.max(config.minAttackSpeed, Math.min(config.maxAttackSpeed,
            player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED)));
        boolean granted = charges.consume(player.getWorld().getTime() + LUNGE_GRACE_TICKS,
            attackSpeed, config.chargeRecoveryTime);
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        new Packet.ChargeSync(charges.copyStartTicks(), charges.copyReadyTicks()).write(buf);
        NetworkManager.sendToPlayer(player, Packet.ChargeSync.ID, buf);
        return granted;
    }

    /**
     * Relay a swing to everyone else who can see it.
     *
     * <p>The attacker draws their own ribbon the moment they swing rather than waiting for the round
     * trip, so they are excluded here. The swing is re-stamped with the sender's entity id and its
     * parameters are clamped on the way through: this is a cosmetic packet, and a modified client
     * should not be able to paint arbitrarily large arcs on anyone else's screen.
     */
    private static void relaySwing(ServerPlayerEntity attacker, AttackSwing swing) {
        List<ServerPlayerEntity> viewers = new ArrayList<>();
        for (ServerPlayerEntity viewer : attacker.getServerWorld().getPlayers()) {
            if (viewer != attacker && viewer.squaredDistanceTo(attacker) <= SWING_VISIBLE_RANGE_SQ) {
                viewers.add(viewer);
            }
        }
        if (viewers.isEmpty()) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        new Packet.Swing(attacker.getId(), swing.sanitised()).write(buf);
        NetworkManager.sendToPlayers(viewers, Packet.Swing.S2C_ID, buf);
    }

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, Packet.Holster.ID, (buf, context) -> {
            Packet.Holster payload = Packet.Holster.read(buf);
            context.queue(() -> {
                if (context.getPlayer() instanceof HitstopAccessor hitstopAccessor) {
                    hitstopAccessor.setHolster(payload.bool() || !hitstopAccessor.isHolster());
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, Packet.Impulse.ID, (buf, context) -> {
            Packet.Impulse payload = Packet.Impulse.read(buf);
            context.queue(() -> {
                ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
                Entity entity = player.getWorld().getEntityById(payload.id());
                if (payload.shouldCheck()) {
                    // Lunge (all modes send shouldCheck=true here): gate on the authoritative ledger.
                    boolean granted = gateLungeCharge(player);
                    if (player instanceof HitstopAccessor accessor) {
                        // Only grant the mod-side clamp effect when a charge was actually available.
                        if (granted) {
                            var list = EntityHelper.getEntitiesInFront(player, 4.5F * 1.4F);
                            accessor.setShouldClamp(!list.isEmpty());
                        } else {
                            accessor.setShouldClamp(false);
                        }
                    }
                    // Always ACK so the client clears its pending lock, granted or not.
                    PacketByteBuf ackBuf = new PacketByteBuf(Unpooled.buffer());
                    new Packet.LungeAck().write(ackBuf);
                    NetworkManager.sendToPlayer(player, Packet.LungeAck.ID, ackBuf);
                }
                else
                if (entity instanceof HitstopAccessor hitstopAccessor) {
                    boolean isEnemy = entity != player && !payload.shouldCheck();
                    if (isEnemy) {
                        Vec3d dir = new Vec3d(payload.x(), payload.y(), payload.z());
                        double dirLen = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                        if (dirLen > 0.001) {
                            // Stamp the swing-sweep direction. The redirect (and its dirCoeff factor)
                            // is applied exactly once when the freeze releases, against the compounded
                            // knockback magnitude — no scheduled read-modify-overwrite of the
                            // accumulator, so compounding is order-independent and can't re-inflate.
                            hitstopAccessor.setImpulseDir(new Vec3d(dir.x / dirLen, 0, dir.z / dirLen));
                        }
                    } else {
                        hitstopAccessor.setImpulseVector(
                            hitstopAccessor.getImpulseVector()
                                .multiply(payload.mag2())
                                .add(new Vec3d(payload.x(), payload.y(), payload.z()))
                                .multiply(payload.mag())
                        );
                        entity.setVelocity(entity.getVelocity());
                        entity.velocityModified = true;
                        entity.velocityDirty = true;
                    }
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, Packet.Swing.C2S_ID, (buf, context) -> {
            Packet.Swing payload = Packet.Swing.read(buf);
            context.queue(() -> {
                ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
                relaySwing(player, payload.swing());
            });
        });
    }
}
