package com.tabletmc.echo_summon.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import static net.minecraft.util.math.BlockPos.*;

/** Shared helper for locating safe spawn positions when releasing mounts. */
public final class SpawnPositionUtil {
    private SpawnPositionUtil() {}

    public static Vec3d findSafeSpawnPosition(ServerPlayerEntity player, Vec3d direction, double preferredDistance, double maxDistance) {
        ServerWorld world = player.getWorld();
        Vec3d forward = direction.normalize();

        double desiredDistance = Math.min(maxDistance, preferredDistance + 1.0);
        Vec3d eyePos = player.getEyePos();
        Vec3d rayEnd = eyePos.add(forward.multiply(maxDistance + 1.0));
        Vec3d targetPos = player.getPos().add(forward.multiply(desiredDistance));

        var hitResult = world.raycast(new net.minecraft.world.RaycastContext(
                eyePos,
                rayEnd,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            double hitDistance = hitResult.getPos().distanceTo(eyePos);
            double adjustedDistance = Math.max(0.5, Math.min(desiredDistance, hitDistance - 1.0));
            targetPos = eyePos.add(forward.multiply(adjustedDistance));
        }

        BlockPos column = ofFloored(targetPos.x, player.getY(), targetPos.z);
        ChunkPos chunkPos = new ChunkPos(column);
        if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
            column = player.getBlockPos();
        }

        BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
        BlockPos ground = surface.down();

        int downwardChecks = 0;
        while (world.isAir(ground) && ground.getY() > world.getBottomY() && downwardChecks++ < 8) {
            ground = ground.down();
        }
        if (world.isAir(ground)) {
            BlockPos fallbackSurface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, player.getBlockPos());
            return Vec3d.ofBottomCenter(fallbackSurface);
        }

        BlockPos spawnBlock = ground.up();
        if (!world.isAir(spawnBlock) || !world.isAir(spawnBlock.up())) {
            BlockPos check = spawnBlock;
            for (int i = 0; i < 4; i++) {
                if (world.isAir(check) && world.isAir(check.up())) {
                    spawnBlock = check;
                    break;
                }
                check = check.up();
            }
        }

        return Vec3d.ofBottomCenter(spawnBlock);
    }
}
