package com.cleannrooster.visceral_combat.config;

/**
 * How much the character helps execute an attack against the enemy the player is clearly aiming at.
 *
 * <p>Deliberately a separate axis from {@link LungeMode}: the lunge mode decides where a lunge's
 * <em>impulse</em> comes from (movement input vs. facing), while this decides whether that impulse —
 * and the swing itself — may bend toward a committed target. Any lunge mode combines with any assist
 * mode.
 *
 * <p>None of these modes ever guarantee a hit. Better Combat's own target finder stays authoritative;
 * assistance only nudges the player's facing and step within clamped envelopes, and a target that
 * moves outside those envelopes is missed exactly as if there were no assistance at all.
 */
public enum TargetAssistMode {
    /** No acquisition, no highlight, no swing tracking, no target-aware lunge. */
    OFF,
    /** Highlight the intended target and softly track it through the swing. The lunge is untouched. */
    SOFT,
    /**
     * The full system: highlight, soft swing tracking, and an attack lunge that bends toward the
     * committed target to close a small range gap.
     */
    RPG
}
