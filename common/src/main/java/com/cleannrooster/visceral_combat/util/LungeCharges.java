package com.cleannrooster.visceral_combat.util;

/**
 * A pool of independently-recovering lunge charges. Used on both sides with identical recovery math so
 * server enforcement lines up with client prediction: client-side there is one static instance for the
 * local player (see VisceralCombatClient, drives prediction + the HUD), and server-side there is one
 * instance per player (see HitstopAccessor#getLungeCharges, the authoritative ledger).
 */
public class LungeCharges {
    public static final int DEFAULT_MAX_CHARGES = 4;

    // World tick each slot began recovering (for fill progress) and the tick it becomes ready again.
    private long[] startTick;
    private long[] readyTick;

    public LungeCharges(int count) {
        count = clampCount(count);
        this.startTick = new long[count];
        this.readyTick = new long[count];
    }

    private static int clampCount(int count) {
        return Math.max(0, Math.min(64, count));
    }

    /** Number of charge slots. */
    public int size() {
        return readyTick.length;
    }

    /** Resize the pool, preserving existing state; new slots start ready. */
    public void setSize(int count) {
        count = clampCount(count);
        if (count == readyTick.length) return;
        long[] newStart = new long[count];
        long[] newReady = new long[count];
        int copy = Math.min(count, readyTick.length);
        System.arraycopy(startTick, 0, newStart, 0, copy);
        System.arraycopy(readyTick, 0, newReady, 0, copy);
        startTick = newStart;
        readyTick = newReady;
    }

    /** True if at least one charge is ready to spend at {@code now}. */
    public boolean hasCharge(long now) {
        for (long ready : readyTick) {
            if (now >= ready) return true;
        }
        return false;
    }

    /**
     * Spend one ready charge, starting its recovery over {@code recoveryMultiplier} attack cooldowns
     * of the weapon used (recoveryMultiplier * 20 / attackSpeed ticks). Returns true if a charge was
     * spent, false if none was ready.
     */
    public boolean consume(long now, double attackSpeed, double recoveryMultiplier) {
        for (int i = 0; i < readyTick.length; i++) {
            if (now >= readyTick[i]) {
                long duration = Math.max(1L, Math.round(recoveryMultiplier * 20.0 / Math.max(0.01, attackSpeed)));
                startTick[i] = now;
                readyTick[i] = now + duration;
                return true;
            }
        }
        return false;
    }

    /** Recovery progress of a slot in [0, 1]; 1 means ready. */
    public float progress(int slot, long now) {
        if (now >= readyTick[slot]) return 1.0f;
        long dur = readyTick[slot] - startTick[slot];
        if (dur <= 0L) return 1.0f;
        float p = (now - startTick[slot]) / (float) dur;
        return p < 0f ? 0f : (p > 1f ? 1f : p);
    }

    /**
     * Render opacity of a slot in [0, 1]. Recovering slots are fully visible; a slot that has been full
     * for longer than {@code holdTicks} fades to 0 over {@code fadeTicks}.
     */
    public float alpha(int slot, long now, int holdTicks, int fadeTicks) {
        if (now < readyTick[slot]) return 1.0f; // still recovering
        long elapsed = now - readyTick[slot];
        if (elapsed <= holdTicks) return 1.0f;
        if (fadeTicks <= 0) return 0.0f;
        long fadeElapsed = elapsed - holdTicks;
        if (fadeElapsed >= fadeTicks) return 0.0f;
        return 1.0f - fadeElapsed / (float) fadeTicks;
    }

    /** Restore all charges to ready (e.g. on disconnect / world change). */
    public void reset() {
        for (int i = 0; i < readyTick.length; i++) {
            startTick[i] = 0L;
            readyTick[i] = 0L;
        }
    }

    /** Snapshot of per-slot start ticks (for syncing authoritative state to clients). */
    public long[] copyStartTicks() {
        return startTick.clone();
    }

    /** Snapshot of per-slot ready ticks (for syncing authoritative state to clients). */
    public long[] copyReadyTicks() {
        return readyTick.clone();
    }

    /** Overwrite this pool's state from an authoritative snapshot (client reconciliation). */
    public void setState(long[] start, long[] ready) {
        int n = Math.min(start.length, ready.length);
        this.startTick = new long[n];
        this.readyTick = new long[n];
        System.arraycopy(start, 0, this.startTick, 0, n);
        System.arraycopy(ready, 0, this.readyTick, 0, n);
    }
}
