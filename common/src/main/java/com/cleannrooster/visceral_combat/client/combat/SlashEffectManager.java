package com.cleannrooster.visceral_combat.client.combat;

import com.cleannrooster.visceral_combat.combat.AttackFrame;
import com.cleannrooster.visceral_combat.combat.AttackSwing;
import com.cleannrooster.visceral_combat.combat.SlashProfile;
import dev.architectury.event.events.client.ClientTickEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side pool of live slash ribbons.
 *
 * <p>Both loaders funnel their world-render callback into {@link #render}; ageing rides Architectury's
 * shared client tick event. Nothing here knows about any particular weapon — an attack that comes
 * through Better Combat gets its visual for free.
 */
@Environment(EnvType.CLIENT)
public final class SlashEffectManager {

    private static final List<SlashEffect> ACTIVE = new ArrayList<>();

    /**
     * World render callbacks run while Minecraft's shared entity buffer source is already in use. In
     * 1.20.1, injecting a new entity render layer into that batch this late can leave it unflushed, so
     * ribbons get their own immediate source with its own begin/end lifecycle on both loaders.
     */
    private static final VertexConsumerProvider.Immediate RIBBON_BUFFERS =
        VertexConsumerProvider.immediate(new BufferBuilder(256 * 1024));

    /** Past this the ribbon is a few pixels of a distant scuffle; not worth the draw. */
    private static final double RENDER_DISTANCE = 96.0;

    private SlashEffectManager() {
    }

    /** Wire up the tick hook. Called from client init on both loaders. */
    public static void init() {
        ClientTickEvent.CLIENT_POST.register(client -> tick());
    }

    /**
     * Start a ribbon for {@code attackerId}, as announced by the server.
     *
     * <p>Silently drops swings from entities this client cannot see: the attacker is what the frame is
     * read from every frame, so there is nothing to draw without one.
     */
    public static void spawn(int attackerId, AttackSwing swing) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        Entity entity = client.world.getEntityById(attackerId);
        if (entity instanceof LivingEntity attacker) {
            spawn(attacker, swing);
        }
    }

    /** Start a ribbon for an attacker this client already has in hand — the local player's own swing. */
    public static void spawn(LivingEntity attacker, AttackSwing swing) {
        ACTIVE.add(new SlashEffect(swing, SlashProfile.forShape(swing.shape()), attacker,
            AttackFrame.of(attacker, 1.0f)));
    }

    private static void tick() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        if (MinecraftClient.getInstance().world == null) {
            ACTIVE.clear();
            return;
        }
        for (Iterator<SlashEffect> it = ACTIVE.iterator(); it.hasNext(); ) {
            SlashEffect effect = it.next();
            effect.tick();
            if (effect.isExpired()) {
                it.remove();
            }
        }
    }

    /** Draw every live slash. Called from each loader's world-render hook. */
    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        // Keep the callback's view rotation basis, but add the world-to-camera translation explicitly.
        // A fresh MatrixStack would lose that basis and make the mesh look camera-bound.
        Vec3d cameraPos = camera.getPos();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (SlashEffect effect : ACTIVE) {
            if (effect.resolveFrame(tickDelta).origin().squaredDistanceTo(cameraPos)
                    <= RENDER_DISTANCE * RENDER_DISTANCE) {
                SlashRenderer.render(effect, matrices, RIBBON_BUFFERS, tickDelta);
            }
        }
        RIBBON_BUFFERS.draw();
        matrices.pop();
    }

    /** Drop everything — on world change or disconnect. */
    public static void clear() {
        ACTIVE.clear();
    }
}
