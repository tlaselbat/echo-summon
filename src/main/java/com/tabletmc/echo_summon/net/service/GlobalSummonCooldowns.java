package com.tabletmc.echo_summon.net.service;

import com.tabletmc.echo_summon.ModConstants;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player global cooldown manager for saddle summon/dismiss, plus a small queue
 * to run an auto-dismiss right when the cooldown expires.
 */
public final class GlobalSummonCooldowns {
    private GlobalSummonCooldowns() {}

    private static final Map<UUID, Long> COOLDOWN_END_TICK = new HashMap<>();
    private static final Map<UUID, PendingDismiss> PENDING_DISMISS = new HashMap<>();

    private record PendingDismiss(UUID entityUuid, String toolId) {}

    public static void set(ServerPlayerEntity player, int ticks) {
        if (player == null) return;
        long now = safeTime(player);
        COOLDOWN_END_TICK.put(player.getUuid(), now + Math.max(0, ticks));
    }

    public static boolean isCoolingDown(ServerPlayerEntity player) {
        if (player == null) return false;
        Long end = COOLDOWN_END_TICK.get(player.getUuid());
        if (end == null) return false;
        return safeTime(player) < end;
    }

    public static long remaining(ServerPlayerEntity player) {
        if (player == null) return 0L;
        Long end = COOLDOWN_END_TICK.get(player.getUuid());
        if (end == null) return 0L;
        long rem = end - safeTime(player);
        return Math.max(0L, rem);
    }

    public static void queueDismissAfterCooldown(ServerPlayerEntity player, UUID entityUuid, String toolId) {
        if (player == null || entityUuid == null) return;
        PENDING_DISMISS.put(player.getUuid(), new PendingDismiss(entityUuid, toolId != null ? toolId : ""));
    }

    public static void process(ServerPlayerEntity player) {
        if (player == null) return;
        if (isCoolingDown(player)) return;
        PendingDismiss pending = PENDING_DISMISS.remove(player.getUuid());
        if (pending == null) return;
        // Try to find entity across worlds
        var server = player.getServer();
        if (server == null) return;
        LivingEntity target = null;
        for (var world : server.getWorlds()) {
            Entity e = world.getEntity(pending.entityUuid);
            if (e instanceof LivingEntity le) { target = le; break; }
        }
        if (target == null) {
            return; // entity already gone
        }
        // Resolve tool id from pending or from the target's command tags
        String toolId = pending.toolId;
        if (toolId == null || toolId.isEmpty()) {
            toolId = extractToolIdFromTags(target);
        }
        // Find the owning tool in player's inventory
        var stack = SummonToolLookup.findSaddleSummonToolById(player, toolId);
        if (stack.isEmpty()) {
            return; // owner tool not found; skip
        }
        try {
            SaddleSummonService.dismissSaddleSummonedMount(player, stack, target, toolId, true, null);
        } catch (Throwable ignored) {}
    }

    private static long safeTime(ServerPlayerEntity player) {
        try {
            return player.getServer() != null ? player.getServer().getTicks() : player.getWorld().getTime();
        } catch (Throwable ignored) {}
        return System.currentTimeMillis() / 50L; // fallback
    }

    private static String extractToolIdFromTags(LivingEntity living) {
        String prefix = ModConstants.SADDLE_TOOL_TAG_PREFIX;
        for (String tag : living.getCommandTags()) {
            if (tag.startsWith(prefix)) {
                String raw = tag.substring(prefix.length());
                if (raw.startsWith("Optional[") && raw.endsWith("]")) {
                    raw = raw.substring("Optional[".length(), raw.length() - 1);
                }
                return raw;
            }
        }
        return "";
    }
}
