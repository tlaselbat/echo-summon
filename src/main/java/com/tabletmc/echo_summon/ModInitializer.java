package com.tabletmc.echo_summon;

import com.tabletmc.echo_summon.command.SpawnAllowedMountsCommand;
import com.tabletmc.echo_summon.config.EchoSummonConfig;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.net.ServerNetworking;
import com.tabletmc.echo_summon.net.service.DeferredDismissStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public class ModInitializer implements net.fabricmc.api.ModInitializer {

    @Override
    public void onInitialize() {
        // Load config early on server to apply policy defaults
        EchoSummonConfig.load();
        ModItems.registerModItems();
        ServerNetworking.init();
        SpawnAllowedMountsCommand.register();

        // Entity-load hook to process deferred dismissals (kill-on-load)
        try {
            ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
                try {
                    if (!(entity instanceof LivingEntity living)) return;
                    if (!living.getCommandTags().contains(ModConstants.SUMMON_TAG)) return;

                    // Check pending store by UUID
                    DeferredDismissStore store = DeferredDismissStore.get(world);
                    var opt = store.getPending(entity.getUuid());
                    if (opt.isPresent()) {
                        // Optional: dimension check; if recorded, prefer removing only in that dimension
                        String recordedDim = opt.get().dimId;
                        String hereDim = world.getRegistryKey().getValue().toString();
                        if (recordedDim.isEmpty() || recordedDim.equals(hereDim)) {
                            try {
                                living.remove(Entity.RemovalReason.DISCARDED);
                            } catch (Throwable ignored) {}
                            store.remove(entity.getUuid());
                            // Also clear remote_active on matching tools by toolId
                            try {
                                String toolId = opt.get().toolId;
                                if (toolId != null && !toolId.isEmpty() && world.getServer() != null) {
                                    for (var p : world.getServer().getPlayerManager().getPlayerList()) {
                                        var stack = com.tabletmc.echo_summon.net.service.SummonToolLookup.findSaddleSummonToolById(p, toolId);
                                        if (!stack.isEmpty()) {
                                            var cd = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                                            if (cd != null) {
                                                var n = cd.copyNbt();
                                                var st = n.getCompound(ModConstants.STORED_MOUNT_KEY);
                                                if (st.isPresent()) {
                                                    var s = st.get();
                                                    s.remove(ModConstants.REMOTE_ACTIVE_KEY);
                                                    n.put(ModConstants.STORED_MOUNT_KEY, s);
                                                    // Also clear the tool-side summon flag since the entity was killed on load
                                                    n.remove(ModConstants.MOUNT_SUMMONED_TOOL_FLAG);
                                                    stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(n));
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {}
                            return;
                        }
                    }

                    // Fallback: match by tool tag in case UUID changed or was not written
                    String prefix = ModConstants.SADDLE_TOOL_TAG_PREFIX;
                    String foundId = "";
                    for (String tag : living.getCommandTags()) {
                        if (tag.startsWith(prefix)) { foundId = tag.substring(prefix.length()); break; }
                    }
                    if (!foundId.isEmpty()) {
                        var byTool = store.findByToolId(foundId);
                        if (byTool.isPresent()) {
                            // Optional: respect recorded dimension if available
                            String recordedDim = byTool.get().getValue().dimId;
                            String hereDim = world.getRegistryKey().getValue().toString();
                            if (recordedDim.isEmpty() || recordedDim.equals(hereDim)) {
                                try { living.remove(Entity.RemovalReason.DISCARDED); } catch (Throwable ignored) {}
                                // Remove the exact pending record by its original UUID key, not the current entity
                                try {
                                    java.util.UUID pendingKey = java.util.UUID.fromString(byTool.get().getKey());
                                    store.remove(pendingKey);
                                } catch (Throwable ignored) {}
                                // Clear remote_active on tools with this toolId and clear summon flag
                                try {
                                    if (world.getServer() != null) {
                                        for (var p : world.getServer().getPlayerManager().getPlayerList()) {
                                            var stack = com.tabletmc.echo_summon.net.service.SummonToolLookup.findSaddleSummonToolById(p, foundId);
                                            if (!stack.isEmpty()) {
                                                var cd = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                                                if (cd != null) {
                                                    var n = cd.copyNbt();
                                                    var st = n.getCompound(ModConstants.STORED_MOUNT_KEY);
                                                    if (st.isPresent()) {
                                                        var s = st.get();
                                                        s.remove(ModConstants.REMOTE_ACTIVE_KEY);
                                                        n.put(ModConstants.STORED_MOUNT_KEY, s);
                                                        // Also clear the tool-side summon flag since the entity was killed on load
                                                        n.remove(ModConstants.MOUNT_SUMMONED_TOOL_FLAG);
                                                        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(n));
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}

        // Global cooldown processing: run queued auto-dismiss when cooldown ends
        try {
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                try {
                    var players = server.getPlayerManager().getPlayerList();
                    for (var p : players) {
                        com.tabletmc.echo_summon.net.service.GlobalSummonCooldowns.process(p);
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }
}
