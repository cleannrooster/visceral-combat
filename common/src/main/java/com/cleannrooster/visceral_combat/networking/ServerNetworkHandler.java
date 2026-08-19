package com.cleannrooster.visceral_combat.networking;

import com.cleannrooster.visceral_combat.VisceralCombat;
import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.combat.AttackSwing;
import com.cleannrooster.visceral_combat.config.ConfigSync;
import com.cleannrooster.visceral_combat.config.ServerConfig;
import com.cleannrooster.visceral_combat.util.EntityHelper;
import com.cleannrooster.visceral_combat.util.LungeCharges;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class ServerNetworkHandler {

    // Grace applied to the server's readiness check so network jitter never denies a lunge the client
    // legitimately predicted. It cancels across consecutive spends (applied to both check and timer), so
    // it grants the boundary case without letting the budget drift.
    private static final long LUNGE_GRACE_TICKS = 3L;

    // Far enough that a swing is still legible on screen, near enough that a busy server is not
    // broadcasting every attack to everyone in the dimension.
    private static final double SWING_VISIBLE_RANGE_SQ = 96.0 * 96.0;

    /**
     * Authoritative lunge-charge gate shared by every lunge mode: spends from the player's server-side
     * ledger and reconciles the client HUD via ChargeSync. Returns true if a charge was available (or if
     * enforcement is off because no config is loaded yet). A legit client already gated itself, so this
     * only bites a modified client or corrects desync.
     */
    private static boolean gateLungeCharge(PlayerEntity player) {
        ServerConfig config = VisceralCombat.config;
        // No config, charge system disabled, or non-player: no gating (lunge always granted).
        if (config == null || !config.chargesEnabled || !(player instanceof HitstopAccessor accessor)) return true;
        LungeCharges charges = accessor.getLungeCharges();
        charges.setSize(config.maxCharges);
        double attackSpeed = Math.clamp(player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED),
            config.minAttackSpeed, config.maxAttackSpeed);
        boolean granted = charges.consume(player.getWorld().getTime() + LUNGE_GRACE_TICKS,
            attackSpeed, config.chargeRecoveryTime);
        NetworkManager.sendToPlayer((ServerPlayerEntity) player,
            new Packet.ChargeSync(charges.copyStartTicks(), charges.copyReadyTicks()));
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
        NetworkManager.sendToPlayers(viewers, new Packet.SwingS2C(attacker.getId(), swing.sanitised()));
    }

    public static void register() {
        if(Platform.getEnvironment().equals(Env.SERVER)) {

            NetworkManager.registerS2CPayloadType(ConfigSync.PACKET_ID, ConfigSync.CODEC);
            NetworkManager.registerS2CPayloadType(Packet.HolsterAssert.PACKET_ID, Packet.HolsterAssert.CODEC);
            NetworkManager.registerS2CPayloadType(Packet.LungeAck.PACKET_ID, Packet.LungeAck.CODEC);
            NetworkManager.registerS2CPayloadType(Packet.ChargeSync.PACKET_ID, Packet.ChargeSync.CODEC);
            NetworkManager.registerS2CPayloadType(Packet.SwingS2C.PACKET_ID, Packet.SwingS2C.CODEC);

        }
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, Packet.Holster.PACKET_ID, Packet.Holster.CODEC,
            (payload, context) -> context.queue(() -> {
                if (context.getPlayer() instanceof HitstopAccessor hitstopAccessor) {
                    hitstopAccessor.setHolster(payload.bool() || !hitstopAccessor.isHolster());
                }
            })
        );

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, Packet.Impulse.PACKET_ID, Packet.Impulse.CODEC,
            (payload, context) -> context.queue(() -> {
                Entity entity = context.getPlayer().getWorld().getEntityById(payload.id());
                if (payload.shouldCheck()) {
                    var player = context.getPlayer();
                    // HYBRID/ARCADE lunge: gate on the authoritative ledger (movement is client-side).
                    boolean granted = gateLungeCharge(player);
                    if (player instanceof HitstopAccessor accessor) {
                        // Only grant the mod-side clamp effect when a charge was actually available.
                        if (granted) {
                            var list = EntityHelper.getEntitiesInFront(player,
                                (float) player.getEntityInteractionRange() * 1.4F);
                            accessor.setShouldClamp(!list.isEmpty());
                        } else {
                            accessor.setShouldClamp(false);
                        }
                    }
                    // Always ACK so the client clears its pending lock, granted or not.
                    NetworkManager.sendToPlayer((ServerPlayerEntity) player, new Packet.LungeAck());
                }
                else
                if (entity instanceof HitstopAccessor hitstopAccessor) {
                    boolean isEnemy = entity != context.getPlayer() && !payload.shouldCheck();
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
                        // DUELING-mode lunge (shouldCheck=false, self-targeted): gate on the ledger + ACK
                        // only. The lunge and its smoothing are now applied client-side (see
                        // VisceralCombatClient); the server no longer pushes the player's velocity, which
                        // is what made it stutter against client movement prediction.
                        gateLungeCharge(context.getPlayer());
                        // ACK so charges (not the 40-tick pending-lock timeout) rate-limit DUELING lunges.
                        NetworkManager.sendToPlayer((ServerPlayerEntity) context.getPlayer(), new Packet.LungeAck());
                    }
                }
            })
        );

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, Packet.SwingC2S.PACKET_ID, Packet.SwingC2S.CODEC,
            (payload, context) -> context.queue(() -> {
                ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
                relaySwing(player, payload.swing());
            })
        );
    }
}
