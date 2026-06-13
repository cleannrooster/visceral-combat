package com.cleannrooster.visceral_combat.config;

public enum LungeMode {
    /** Arcade — lunge in whatever direction you're moving. Rewards active footwork. */
    ARCADE,
    /** Dueling — always lunge straight forward, regardless of movement input. Rewards positioning before the swing. */
    DUELING,
    /** Hybrid — lunge forward, with a configurable amount of left/right influence from movement input. */
    HYBRID
}
