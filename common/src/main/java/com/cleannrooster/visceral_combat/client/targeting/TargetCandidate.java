package com.cleannrooster.visceral_combat.client.targeting;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;

/**
 * One enemy under consideration as the player's intended target, with the measurements the score was
 * built from.
 *
 * @param entity       the candidate
 * @param distance     blocks from the attack's tracing point to the nearest point of the candidate's
 *                     bounding box — nearest point rather than centre, so a large mob standing close
 *                     is not judged by where its middle happens to be
 * @param angleDegrees angular offset of the candidate's centre from the crosshair
 * @param score        the composite intent score — see {@link TargetAcquisition#score}
 */
@Environment(EnvType.CLIENT)
public record TargetCandidate(LivingEntity entity, double distance, double angleDegrees, double score) {
}
