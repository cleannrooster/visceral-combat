package com.cleannrooster.visceral_combat.api;

import net.minecraft.util.math.Vec3d;

public interface HitstopAccessor {
    int getHitstopTicks();
    void setVelocityHitstop(Vec3d vec3d);
    void setHitstop(int hitstop);
    void setImpulseVector(Vec3d vec3d);
    Vec3d getImpulseVector();
    void setImpulseDir(Vec3d vec3d);
    Vec3d getImpulseDir();
    Vec3d getVelocityHitstop();
    void setHitstopTime(int hitstopTime);
    int getHitstopTime();
    void setLastAttackedTemporary(long time);
    long getLastHitstopAppliedTime();
    void setLastHitstopAppliedTime(long time);
    boolean isHolster();
    boolean shouldClamp();
    void setShouldClamp(boolean shouldClamp);
    void setHolster(boolean holster);
}
