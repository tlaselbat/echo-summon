package com.tabletmc.echo_summon.util;

import com.tabletmc.echo_summon.ModConstants;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/** Utility for best-effort synchronous chunk loading with debug logs. */
public final class ChunkLoadUtil {
    private ChunkLoadUtil() {}

    public static void forceLoadChunk(ServerWorld world, int cx, int cz) {
        try {
            ModConstants.LOGGER.debug("[ChunkLoad] Forcing chunk load world={}, cx={}, cz={}", describeWorld(world), cx, cz);
            world.getChunk(cx, cz);
        } catch (Throwable t) {
            ModConstants.LOGGER.debug("[ChunkLoad] getChunk threw for world={}, cx={}, cz={} -> {}", describeWorld(world), cx, cz, t.toString());
        }
    }

    public static void forceLoadArea(ServerWorld world, int centerCx, int centerCz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                forceLoadChunk(world, centerCx + dx, centerCz + dz);
            }
        }
    }

    /**
     * Force a chunk using a ticket (setChunkForced) if available, run an action, then release the ticket.
     * Falls back to getChunk when setChunkForced is unavailable on this platform/mapping.
     */
    public static void forceChunkWithTicket(ServerWorld world, int cx, int cz, Runnable action) {
        boolean forced = false;
        try {
            try {
                world.setChunkForced(cx, cz, true);
                forced = true;
            } catch (Throwable ignoreInt) {
                // Fallback: simple getChunk
                world.getChunk(cx, cz);
            }
            if (action != null) action.run();
        } catch (Throwable t) {
            try { ModConstants.LOGGER.debug("[ChunkLoad] forceChunkWithTicket error: {}", t.toString()); } catch (Throwable ignored) {}
        } finally {
            if (forced) {
                try {
                    world.setChunkForced(cx, cz, false);
                } catch (Throwable ignored) {}
            }
        }
    }

    /** Force a square area with tickets, run an action, then release all tickets. */
    public static void forceAreaWithTickets(ServerWorld world, int centerCx, int centerCz, int radius, Runnable action) {
        java.util.List<ChunkPos> forced = new java.util.ArrayList<>();
        try {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = centerCx + dx, z = centerCz + dz;
                    boolean ok = false;
                    try {
                        world.setChunkForced(x, z, true);
                        ok = true;
                    } catch (Throwable ignoreInt) {
                        forceLoadChunk(world, x, z);
                    }
                    if (ok) forced.add(new ChunkPos(x, z));
                }
            }
            if (action != null) action.run();
        } catch (Throwable t) {
            try { ModConstants.LOGGER.debug("[ChunkLoad] forceAreaWithTickets error: {}", t.toString()); } catch (Throwable ignored) {}
        } finally {
            for (ChunkPos pos : forced) {
                try {
                    world.setChunkForced(pos.x, pos.z, false);
                } catch (Throwable ignored) {}
            }
        }
    }

    public static String describeWorld(ServerWorld world) {
        try {
            return world.getRegistryKey().getValue().toString();
        } catch (Throwable ignored) {}
        return "unknown_world";
    }

    public static ChunkPos toChunkPos(int blockX, int blockZ) {
        return new ChunkPos(blockX >> 4, blockZ >> 4);
    }
}
