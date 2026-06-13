package com.cleannrooster.visceral_combat.util;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

public class TickScheduler {

    private static final Map<ServerWorld, Map<Long, List<Runnable>>> scheduled = new WeakHashMap<>();

    public static void schedule(ServerWorld world, int delayTicks, Runnable task) {
        long targetTick = world.getTime() + delayTicks;
        scheduled
            .computeIfAbsent(world, w -> new HashMap<>())
            .computeIfAbsent(targetTick, t -> new ArrayList<>())
            .add(task);
    }

    public static void register() {
        TickEvent.SERVER_POST.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                var worldMap = scheduled.get(world);
                if (worldMap == null) continue;
                var tasks = worldMap.remove(world.getTime());
                if (tasks != null) tasks.forEach(Runnable::run);
            }
        });
    }
}
