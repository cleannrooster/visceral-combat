package com.cleannrooster.visceral_combat;

import com.cleannrooster.visceral_combat.api.HitstopAccessor;
import com.cleannrooster.visceral_combat.client.CombatEventsClient;
import com.cleannrooster.visceral_combat.config.ServerConfig;
import com.cleannrooster.visceral_combat.networking.ClientNetworkHandler;
import com.cleannrooster.visceral_combat.networking.Packet;
import com.cleannrooster.visceral_combat.util.LungeCharges;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class VisceralCombatClient {
    public static ServerConfig clientConfig;
    public static KeyBinding holsterBinding;
    public static boolean lungePending = false;
    public static long lungeExpiry = 0L;

    // Lunge charges for the local player: the client's predicted view (drives the HUD and the instant
    // local lunge). The server keeps its own authoritative ledger and reconciles via ChargeSync. Both
    // sides share the same LungeCharges recovery math. The thin delegates below keep call sites unchanged.
    public static final LungeCharges charges = new LungeCharges(LungeCharges.DEFAULT_MAX_CHARGES);

    public static int maxCharges() { return charges.size(); }
    public static void setMaxCharges(int count) { charges.setSize(count); }
    public static boolean hasCharge(long now) { return charges.hasCharge(now); }
    public static void consumeCharge(long now, double attackSpeed, double recoveryMultiplier) {
        charges.consume(now, attackSpeed, recoveryMultiplier);
    }
    public static float chargeProgress(int slot, long now) { return charges.progress(slot, now); }
    public static float chargeAlpha(int slot, long now, int holdTicks, int fadeTicks) {
        return charges.alpha(slot, now, holdTicks, fadeTicks);
    }
    public static void resetCharges() { charges.reset(); }

    // DUELING-mode lunge smoothing. Seeded on lunge with a short decaying "tail" applied and decayed over
    // the next few ticks in the CLIENT_PRE handler. Kept entirely client-side so it never fights movement
    // prediction.
    public static Vec3d lungeImpulse = Vec3d.ZERO;

    /**
     * Add a horizontal lunge push to the local player, capped to the (doubled, code-side) lunge speed
     * cap. Shared by the instant lunge in all modes and DUELING's per-tick smoothing tail.
     */
    public static void applyLungeVelocity(ClientPlayerEntity player, ServerConfig config, double vx, double vz) {
        var currentVel = player.getVelocity();
        var cap = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * 1.4 * config.lungeSpeedCap * 2.0;
        var capSq = cap * cap;
        if (currentVel.x * currentVel.x + currentVel.z * currentVel.z >= capSq) return; // already at cap
        var addX = vx;
        var addZ = vz;
        var newX = currentVel.x + addX;
        var newZ = currentVel.z + addZ;
        var newSq = newX * newX + newZ * newZ;
        if (newSq > capSq) {
            var scale = cap / Math.sqrt(newSq);
            addX = newX * scale - currentVel.x;
            addZ = newZ * scale - currentVel.z;
        }
        player.addVelocity(addX, 0, addZ);
        player.velocityDirty = true;
    }

    public static void clientInit() {
        ClientNetworkHandler.register();
        CombatEventsClient.register();

        ClientTickEvent.CLIENT_POST.register(client -> {
            // No world means disconnected / main menu: forget the previous server's config so it can
            // never bleed into a later session. The server re-syncs it on join (see VisceralCombat).
            if (client.world == null) {
                clientConfig = null;
                resetCharges();
                lungeImpulse = Vec3d.ZERO;
            }
            if (clientConfig != null) {
                setMaxCharges(clientConfig.maxCharges);
            }
            if (lungePending && client.world != null && client.world.getTime() > lungeExpiry) {
                lungePending = false;
            }
            if (holsterBinding != null && holsterBinding.wasPressed()
                    && client.player instanceof HitstopAccessor accessor) {
                accessor.setHolster(!accessor.isHolster());
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                new Packet.Holster(false).write(buf);
                NetworkManager.sendToServer(Packet.Holster.ID, buf);
            }
        });

        ClientTickEvent.CLIENT_PRE.register(client -> {
            if (client.player == null) return;
            // DUELING lunge smoothing: apply the decaying tail this tick (before movement) and decay it.
            if (clientConfig != null && lungeImpulse.lengthSquared() > 0.02 * 0.02) {
                applyLungeVelocity(client.player, clientConfig, lungeImpulse.x, lungeImpulse.z);
                lungeImpulse = lungeImpulse.multiply(clientConfig.impulseCoeff);
                if (lungeImpulse.lengthSquared() <= 0.02 * 0.02) {
                    lungeImpulse = Vec3d.ZERO;
                }
            }
            if (client.player instanceof HitstopAccessor hitstopAccessor
                    && hitstopAccessor.isHolster()
                    && holsterBinding != null
                    && client.options.attackKey.isPressed()) {
                client.player.sendMessage(
                    Text.translatable("text.holster.error")
                        .append(holsterBinding.getDefaultKey().getLocalizedText()), true
                );
            }
        });
    }
}
