package com.tabletmc.echo_summon.net.service;

import com.tabletmc.echo_summon.ModConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** In-memory fallback store for pending cross-dimension dismissals. */
public final class DeferredDismissStore {
    private static final Map<MinecraftServer, DeferredDismissStore> INSTANCES = new WeakHashMap<>();

    public static final class Entry {
        public String dimId = "";
        public String toolId = "";

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("dim", dimId);
            nbt.putString("tool", toolId);
            return nbt;
        }

        public static Entry fromNbt(NbtCompound nbt) {
            Entry e = new Entry();
            try { e.dimId = com.tabletmc.echo_summon.util.NbtUtils.getString(nbt, "dim"); } catch (Throwable ignored) {}
            try { e.toolId = com.tabletmc.echo_summon.util.NbtUtils.getString(nbt, "tool"); } catch (Throwable ignored) {}
            if (e.dimId == null) e.dimId = "";
            if (e.toolId == null) e.toolId = "";
            return e;
        }
    }

    private final Map<String, Entry> pending = new HashMap<>();

    public static DeferredDismissStore get(MinecraftServer server) {
        if (server == null) return new DeferredDismissStore();
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, s -> new DeferredDismissStore());
        }
    }

    public static DeferredDismissStore get(ServerWorld world) {
        if (world == null) return new DeferredDismissStore();
        return world.getServer() != null ? get(world.getServer()) : new DeferredDismissStore();
    }

    public void addPending(UUID uuid, String dimId, String toolId) {
        if (uuid == null) return;
        Entry e = new Entry();
        e.dimId = dimId != null ? dimId : "";
        e.toolId = toolId != null ? toolId : "";
        pending.put(uuid.toString(), e);
        try { ModConstants.LOGGER.info("[DeferredDismiss] added uuid={}, dim={}, toolId={}", uuid, dimId, toolId); } catch (Throwable ignored) {}
    }

    public Optional<Entry> getPending(UUID uuid) {
        if (uuid == null) return Optional.empty();
        return Optional.ofNullable(pending.get(uuid.toString()));
    }

    public Optional<Map.Entry<String, Entry>> findByToolId(String toolId) {
        if (toolId == null || toolId.isEmpty()) return Optional.empty();
        for (Map.Entry<String, Entry> me : pending.entrySet()) {
            if (toolId.equals(me.getValue().toolId)) return Optional.of(me);
        }
        return Optional.empty();
    }

    public void remove(UUID uuid) {
        if (uuid == null) return;
        if (pending.remove(uuid.toString()) != null) {
            try { ModConstants.LOGGER.info("[DeferredDismiss] removed uuid={}", uuid); } catch (Throwable ignored) {}
        }
    }
}
