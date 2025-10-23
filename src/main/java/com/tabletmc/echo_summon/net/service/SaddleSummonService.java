package com.tabletmc.echo_summon.net.service;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.SaddleableMountImpl;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.item.custom.MountHarnessItem;
import com.tabletmc.echo_summon.item.custom.MountSaddleItem;
import com.tabletmc.echo_summon.util.ChunkLoadUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

import static com.tabletmc.echo_summon.ModConstants.HAD_VANILLA_SADDLE_KEY;
import static com.tabletmc.echo_summon.util.ModelKeyUtil.mapEntityTypeToModelKey;
import static com.tabletmc.echo_summon.util.SpawnPositionUtil.findSafeSpawnPosition;



    /**
     * Service layer for managing the lifecycle of "saddle"-based mount summoning.
     * <p>
     *
     * Responsibilities:
     * - Capture: store a target living entity into a summon tool's NBT.
     * - Summon: spawn the stored entity as a temporary, tool-linked mount and seat the player.
     * - Dismiss: persist state back to the tool and remove the temporary entity from the world.
     * - Release: spawn the stored entity as a permanent, non-summoned mount and clear tool storage.
     * <p>
     * Identification:
     *   of the form MOD_ID + ":saddle_tool:" + toolId to pair the entity with a tool.
     * <p>
     * Notes:
     * - Defensive against nulls/invalid NBT.
     * - Uses try/catch in non-critical paths to avoid hard crashes in production.
        */


public final class SaddleSummonService {

    private SaddleSummonService() {}

    /**
{{ ... }}
     * Entry point for handling all "saddle_*" actions from the summon tool.
     *
     * @param player     the server player performing the action
     * @param summonTool the tool item stack (expected to be the saddle summon tool)
     * @param action     the action string (e.g., "saddle_capture:<uuid>", "saddle_summon")
     * @return true if the action was recognized and handled; false otherwise
     */ 


    public static boolean handleAction(ServerPlayerEntity player, ItemStack summonTool, String action) {
        // Enforce tool-state driven interaction model and preconditions
        boolean toolHasLinked = SummonPersistence.hasStoredMount(summonTool);
        boolean toolSummonFlag = false;
        try {
            NbtComponent tcd = summonTool.get(DataComponentTypes.CUSTOM_DATA);
            if (tcd != null) {
                NbtCompound tnbt = tcd.copyNbt();
                try { toolSummonFlag = tnbt.getBoolean(ModConstants.MOUNT_SUMMONED_TOOL_FLAG).orElse(false); } catch (Throwable ignored) {}
                // Self-heal: if the tool thinks it is summoned but no linked entity exists and no remote-active flag, clear and proceed
                if (toolSummonFlag) {
                    boolean remoteActive = false;
                    try {
                        java.util.Optional<NbtCompound> st = tnbt.getCompound(ModConstants.STORED_MOUNT_KEY);
                        if (st.isPresent()) {
                            remoteActive = st.get().getBoolean(ModConstants.REMOTE_ACTIVE_KEY).orElse(false);
                        }
                    } catch (Throwable ignored2) {}
                    LivingEntity linked = findSaddleSummonedMount(player, summonTool);
                    if (linked == null && !remoteActive) {
                        try {
                            tnbt.remove(ModConstants.MOUNT_SUMMONED_TOOL_FLAG);
                            summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tnbt));
                        } catch (Throwable ignored3) {}
                        toolSummonFlag = false;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // If the tool indicates a mount is currently summoned, force any action to behave as dismiss
        if (toolSummonFlag && !"saddle_dismiss".equals(action)) {
            handleDismiss(player, summonTool);
            return true;
        }

        if (action.startsWith("saddle_capture:")) {
            if (toolHasLinked) {
                return true;
            }
            handleCapture(player, summonTool, action);
            return true;
        }
        switch (action) {
            case "saddle_dismiss" -> {
                // Fast path: if currently riding a saddle-summoned entity, dismiss it directly
                try {
                    if (player.hasVehicle() && player.getVehicle() instanceof LivingEntity riding) {
                        if (riding.getCommandTags().contains(ModConstants.SUMMON_TAG)) {
                            String entToolId = extractSaddleToolId(riding);
                            String curToolId = SummonToolLookup.getSaddleSummonToolId(summonTool);
                            String useId = !entToolId.isEmpty() ? entToolId : curToolId;
                            if (!useId.isEmpty()) {
                                dismissSaddleSummonedMount(player, summonTool, riding, useId, true, null);
                                return true;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
                // Fallback to normal dismiss flow
                handleDismiss(player, summonTool);
                return true;
            }
            case "saddle_summon" -> {
                // Only allowed if we have a linked mount and no active summon flag
                if (!toolHasLinked) {
                    return true;
                }
                handleSummon(player, summonTool);
                return true;
            }
            case "saddle_release" -> {
                // Block release while summoned, and require a linked mount
                if (!toolHasLinked) {
                    return true;
                }
                handleRelease(player, summonTool);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Dismiss a currently active saddle-summoned mount when the server decides it should end,
     * for example when its owner logs out or leaves the relevant area.
     * <p>
     * Implementation detail:
     * - Resolves the owning tool by reading the mount's per-tool command tag. If the tool cannot be found,
     *   it still proceeds with a safe fallback dismissal to avoid orphaned entities.
     */ 

    public static void handleAutoDismiss(ServerPlayerEntity player, LivingEntity mount) {
        // Auto-dismiss only applies to entities we spawned (guard by SUMMON_TAG)
        if (player == null || mount == null) {
            return;
        }
        if (!mount.getCommandTags().contains(ModConstants.SUMMON_TAG)) {
            return;
        }
        // If global cooldown is active, queue dismiss to run as soon as cooldown ends
        try {
            if (com.tabletmc.echo_summon.net.service.GlobalSummonCooldowns.isCoolingDown(player)) {
                String toolIdForQueue = extractSaddleToolId(mount);
                com.tabletmc.echo_summon.net.service.GlobalSummonCooldowns.queueDismissAfterCooldown(player, mount.getUuid(), toolIdForQueue);
                return;
            }
        } catch (Throwable ignored) {}
        // Restrict auto-dismiss to specific approved types, and exclude happy_ghast
        try {
            net.minecraft.util.Identifier typeId = net.minecraft.entity.EntityType.getId(mount.getType());
            if (typeId == null) return;
            String path = typeId.getPath();
            boolean allowedType = switch (path) {
                case "camel", "donkey", "mule", "horse", "zombie_horse", "skeleton_horse" -> true;
                default -> false;
            };
            if (!allowedType) return;
        } catch (Throwable ignored) {}
        String toolId = extractSaddleToolId(mount);
        if (toolId.isEmpty()) {
            return;
        }
        ItemStack summonTool = SummonToolLookup.findSaddleSummonToolById(player, toolId);
        if (summonTool.isEmpty()) {
            // Fallback: dismiss even if the tool cannot be found (e.g., item moved/renamed in production env)
            try {
                mount.remove(Entity.RemovalReason.DISCARDED);
                mount.removeCommandTag(ModConstants.SUMMON_TAG);
                mount.removeCommandTag(ModConstants.SADDLE_TOOL_TAG_PREFIX + toolId);
                // no user-facing message
            } catch (Throwable ignored) {}
            return;
        }
        dismissSaddleSummonedMount(player, summonTool, mount, toolId, true, null);
    }

    /**
     * Capture the targeted living entity (provided via "saddle_capture:<uuid>")
     * into the summon tool's NBT, then remove the original from the world.
     * Enforces entity allowlist and prevents capturing already-summoned mounts.
     */ 

    private static void handleCapture(ServerPlayerEntity player, ItemStack summonTool, String action) {
        // Disallow capturing when the tool already has a stored mount
        if (SummonPersistence.hasStoredMount(summonTool)) {
            return;
        }
        String uuidStr = action.substring("saddle_capture:".length());
        java.util.UUID uuid;
        try {
            uuid = java.util.UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        ServerWorld world = player.getWorld();
        Entity target = world.getEntity(uuid);
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        if (!ModConstants.isSaddleAllowed(living.getType())) {
            return;
        }
        if (living.getCommandTags().contains(ModConstants.SUMMON_TAG)) {
            return;
        }
        if (living instanceof AbstractHorseEntity ahe) {
            try {
                ahe.setTame(true);
            } catch (Throwable ignored) {}
        }

        NbtCompound mountNbt = net.minecraft.predicate.NbtPredicate.entityToNbt(living);
        mountNbt.putString("id", EntityType.getId(living.getType()).toString());
        mountNbt.putString(ModConstants.STORED_MOUNT_ID_KEY, living.getUuidAsString());
        try {
            String ownerName = player.getGameProfile().getName();
            if (ownerName != null && !ownerName.isEmpty()) {
                mountNbt.putString("owner_name", ownerName);
            }
        } catch (Throwable ignored) {}
        if (living.getMaxHealth() > 0f) {
            mountNbt.putFloat("Health", living.getMaxHealth());
        }

        // If allowed mount had a vanilla saddle, mark flag and equip our mount_saddle
        if (ModConstants.isSaddleAllowed(living.getType())) {
            try {
                // Special-case: Happy Ghast receives a mount harness on capture instead of a saddle
                net.minecraft.util.Identifier typeId = net.minecraft.entity.EntityType.getId(living.getType());
                boolean isHappyGhast = typeId != null && "happy_ghast".equals(typeId.getPath());
                if (isHappyGhast) {
                    ItemStack harness = new ItemStack(ModItems.MOUNT_HARNESS);
                    MountHarnessItem.applyEquippable(harness, living.getType());
                    living.equipStack(EquipmentSlot.BODY, harness);
                } else {
                    ItemStack curr = living.getEquippedStack(EquipmentSlot.SADDLE);
                    if (!curr.isEmpty() && curr.isOf(Items.SADDLE)) {
                        mountNbt.putBoolean(HAD_VANILLA_SADDLE_KEY, true);
                        living.equipStack(EquipmentSlot.SADDLE, new ItemStack(ModItems.MOUNT_SADDLE));
                    }
                }
            } catch (Throwable ignored) {}
        }

        NbtComponent custom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound comp = custom != null ? custom.copyNbt() : new NbtCompound();
        String toolId = SummonToolLookup.getSaddleSummonToolId(summonTool);
        if (toolId.isEmpty()) toolId = java.util.UUID.randomUUID().toString();
        comp.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, toolId);
        mountNbt.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, toolId);
        comp.put(ModConstants.STORED_MOUNT_KEY, mountNbt);
        summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(comp));

        try {
            String modelKey = mapEntityTypeToModelKey(EntityType.getId(living.getType()).toString());
            if (!modelKey.isEmpty()) {
                summonTool.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(), List.of(), List.of(modelKey), List.of()));
            } else {
                summonTool.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
            }
        } catch (Throwable ignored) {}

        // Remove the original entity now that it is stored, and apply cooldown + feedback
        living.remove(Entity.RemovalReason.DISCARDED);
        player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS);
    }

    /**
     * Summon the stored mount as a temporary entity linked to the summon tool and start riding it.
     * Behavior:
     * - If a linked summoned mount currently exists, dismiss it instead (acts as a toggle).
     * - If the stored mount is simultaneously harness-summoned elsewhere, prevent saddle-summon until dismissed.
     * - Ensures a compatible mount_saddle is equipped and tags the entity with both SUMMON_TAG and tool tag.
     */ 

    private static void handleSummon(ServerPlayerEntity player, ItemStack summonTool) {
        // Early cooldown gate to avoid double-event races, but allow remote cleanup to proceed
        try {
            boolean cooling = player.getItemCooldownManager().isCoolingDown(summonTool)
                    || com.tabletmc.echo_summon.net.service.GlobalSummonCooldowns.isCoolingDown(player);
            if (cooling) {
                boolean remoteActive = false;
                NbtComponent cd = summonTool.get(DataComponentTypes.CUSTOM_DATA);
                if (cd != null) {
                    NbtCompound nbt = cd.copyNbt();
                    java.util.Optional<NbtCompound> st = nbt.getCompound(ModConstants.STORED_MOUNT_KEY);
                    if (st.isPresent()) {
                        try {
                            remoteActive = st.get().getBoolean(ModConstants.REMOTE_ACTIVE_KEY).orElse(false);
                        } catch (Throwable ignored) {}
                    }
                }
                if (!remoteActive) {
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // If a remote instance is marked active, handle Happy Ghast non-blocking: enqueue cleanup and proceed to summon.
        try {
            NbtComponent tcd = summonTool.get(DataComponentTypes.CUSTOM_DATA);
            if (tcd != null) {
                NbtCompound tnbt = tcd.copyNbt();
                java.util.Optional<NbtCompound> optStored = tnbt.getCompound(ModConstants.STORED_MOUNT_KEY);
                if (optStored.isPresent()) {
                    boolean remoteActive = false;
                    try { remoteActive = optStored.get().getBoolean(ModConstants.REMOTE_ACTIVE_KEY).orElse(false); } catch (Throwable ignored2) {}
                    if (remoteActive) {
                        // Determine if stored mount is Happy Ghast
                        String idStr = com.tabletmc.echo_summon.util.NbtUtils.getString(optStored.get(), "id");
                        boolean isHappyGhastStored = idStr.endsWith("happy_ghast") || "happy_ghast".equals(idStr);
                        if (isHappyGhastStored) {
                            // Enqueue remote cleanup using stored UUID/dim and proceed without blocking
                            try {
                                String uuidStr = com.tabletmc.echo_summon.util.NbtUtils.getString(optStored.get(), ModConstants.STORED_MOUNT_ID_KEY);
                                if (!uuidStr.isEmpty()) {
                                    java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                                    var server = player.getServer();
                                    if (server != null) {
                                        DeferredDismissStore store = DeferredDismissStore.get(server);
                                        String dimStr = com.tabletmc.echo_summon.util.NbtUtils.getString(optStored.get(), ModConstants.STORED_DIM_KEY);
                                        String tid = SummonToolLookup.getSaddleSummonToolId(summonTool);
                                        store.addPending(uuid, dimStr, tid);
                                    }
                                }
                            } catch (Throwable ignored3) {}
                            // Clear remote_active now and continue to summoning
                            try {
                                NbtCompound s = optStored.get();
                                s.remove(ModConstants.REMOTE_ACTIVE_KEY);
                                tnbt.put(ModConstants.STORED_MOUNT_KEY, s);
                                summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tnbt));
                            } catch (Throwable ignored4) {}
                        } else {
                            // no user-facing message
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        // Ensure the tool has a stable ID early (needed to dismiss cross-dimension safely)
        String toolId = SummonToolLookup.getSaddleSummonToolId(summonTool);
        if (toolId.isEmpty()) {
            try {
                NbtComponent tcd = summonTool.get(DataComponentTypes.CUSTOM_DATA);
                NbtCompound tnbt = tcd != null ? tcd.copyNbt() : new NbtCompound();
                toolId = java.util.UUID.randomUUID().toString();
                tnbt.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, toolId);
                summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tnbt));
            } catch (Throwable ignored) {}
        }
        // Always dismiss a linked summoned mount if it exists, regardless of riding state
        LivingEntity existing = findSaddleSummonedMount(player, summonTool);
        
        if (existing != null) {
            dismissSaddleSummonedMount(player, summonTool, existing, toolId, false, null);
            return;
        }
        // Regenerate tool id if duplicates exist and no active summon is linked (either locally or remote-active)
        try {
            boolean remoteActiveFlag = false;
            NbtComponent cdTmp2 = summonTool.get(DataComponentTypes.CUSTOM_DATA);
            if (cdTmp2 != null) {
                NbtCompound nbtTmp2 = cdTmp2.copyNbt();
                java.util.Optional<NbtCompound> stTmp2 = nbtTmp2.getCompound(ModConstants.STORED_MOUNT_KEY);
                if (stTmp2.isPresent()) {
                    try { remoteActiveFlag = stTmp2.get().getBoolean(ModConstants.REMOTE_ACTIVE_KEY).orElse(false); } catch (Throwable ignored) {}
                }
            }
            boolean hasLinkedSummoned = existing != null || remoteActiveFlag;
            toolId = SummonToolLookup.ensureUniqueToolId(player, summonTool, hasLinkedSummoned);
        } catch (Throwable ignored) {}
        // If none found by tags, perform a raw UUID lookup from stored NBT to handle cross-dimension duplicates
        try {
            NbtCompound storedProbe = SummonPersistence.getStoredMount(summonTool);
            if (storedProbe != null && !storedProbe.isEmpty()) {
                String storedIdStr = com.tabletmc.echo_summon.util.NbtUtils.getString(storedProbe, ModConstants.STORED_MOUNT_ID_KEY);
                if (!storedIdStr.isEmpty()) {
                    java.util.UUID uuid = java.util.UUID.fromString(storedIdStr);
                    var server = player.getServer();
                    if (server != null) {
                        boolean isHappyGhast = false;
                        boolean storedRemoteActive = false;
                        try {
                            String storedTypeId = com.tabletmc.echo_summon.util.NbtUtils.getString(storedProbe, "id");
                            isHappyGhast = storedTypeId.endsWith("happy_ghast") || "happy_ghast".equals(storedTypeId);
                        } catch (Throwable ignored) {}
                        try { storedRemoteActive = storedProbe.getBoolean(ModConstants.REMOTE_ACTIVE_KEY).orElse(false); } catch (Throwable ignored) {}
                        // Debug: log presence and target
                        try {
                            String dimIdLog = com.tabletmc.echo_summon.util.NbtUtils.getString(storedProbe, ModConstants.STORED_DIM_KEY);
                            java.util.Optional<NbtCompound> posLog = storedProbe.getCompound(ModConstants.STORED_POS_KEY);
                            boolean hasPos = posLog.isPresent();
                            ModConstants.LOGGER.debug("[Summon-XD] toolId={}, storedUUID={}, storedDim={}, hasPos={}", toolId, storedIdStr, dimIdLog, hasPos);
                        } catch (Throwable ignored) {}
                        // Build world preference list: stored dimension first, then all worlds
                        java.util.List<ServerWorld> worldsToCheck = new java.util.ArrayList<>();
                        try {
                            String dimId = com.tabletmc.echo_summon.util.NbtUtils.getString(storedProbe, ModConstants.STORED_DIM_KEY);
                            if (!dimId.isEmpty()) {
                                net.minecraft.util.Identifier did = net.minecraft.util.Identifier.tryParse(dimId);
                                if (did != null) {
                                    var key = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, did);
                                    ServerWorld sw = server.getWorld(key);
                                    if (sw != null) worldsToCheck.add(sw);
                                }
                            }
                        } catch (Throwable ignored2) {}
                        for (ServerWorld w : server.getWorlds()) {
                            if (!worldsToCheck.contains(w)) worldsToCheck.add(w);
                        }
                        for (ServerWorld w : worldsToCheck) {
                            Entity e = w.getEntity(uuid);
                            if (e == null) {
                                // Try ticket-forcing the stored chunk then retry
                                try {
                                    java.util.Optional<NbtCompound> posOpt = storedProbe.getCompound(ModConstants.STORED_POS_KEY);
                                    if (posOpt.isPresent()) {
                                        NbtCompound pos = posOpt.get();
                                        int cx = pos.getInt("x").orElse(0) >> 4;
                                        int cz = pos.getInt("z").orElse(0) >> 4;
                                        final Entity[] holder = new Entity[1];
                                        com.tabletmc.echo_summon.util.ChunkLoadUtil.forceChunkWithTicket(w, cx, cz, () -> {
                                            holder[0] = w.getEntity(uuid);
                                        });
                                        e = holder[0];
                                    }
                                } catch (Throwable ignored2) {}
                            }
                            if (e == null) {
                                // Expand load radius and scan locally for a matching entity near the stored position
                                try {
                                    java.util.Optional<NbtCompound> posOpt2 = storedProbe.getCompound(ModConstants.STORED_POS_KEY);
                                    if (posOpt2.isPresent()) {
                                        NbtCompound pos2 = posOpt2.get();
                                        int bx = pos2.getInt("x").orElse(0);
                                        int by = pos2.getInt("y").orElse(64);
                                        int bz = pos2.getInt("z").orElse(0);
                                        // Throttle heavy radius scans
                                        long now = System.currentTimeMillis();
                                        long last = 0L; try { last = storedProbe.getLong(ModConstants.LAST_SCAN_TIME_KEY).orElse(0L); } catch (Throwable ignored) {}
                                        boolean throttled = (now - last) < 2000L;
                                        if (!throttled) {
                                            // persist last scan time back to tool
                                            try {
                                                NbtComponent tcd2 = summonTool.get(DataComponentTypes.CUSTOM_DATA);
                                                NbtCompound tnbt2 = tcd2 != null ? tcd2.copyNbt() : new NbtCompound();
                                                java.util.Optional<NbtCompound> st2 = tnbt2.getCompound(ModConstants.STORED_MOUNT_KEY);
                                                if (st2.isPresent()) {
                                                    NbtCompound s2 = st2.get();
                                                    s2.putLong(ModConstants.LAST_SCAN_TIME_KEY, now);
                                                    tnbt2.put(ModConstants.STORED_MOUNT_KEY, s2);
                                                    summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tnbt2));
                                                }
                                            } catch (Throwable ignored4) {}
                                        }
                                        if (throttled) {
                                            try { ModConstants.LOGGER.debug("[Summon-XD] Skip radius scan due to throttle; dt={}ms", (now - last)); } catch (Throwable ignored5) {}
                                        }
                                        int cx0 = bx >> 4, cz0 = bz >> 4;
                                        int r = 3;
                                        if (!throttled) {
                                            final Entity[] holder2 = new Entity[1];
                                            final String tagToolId = toolId;
                                            ChunkLoadUtil.forceAreaWithTickets(w, cx0, cz0, r, () -> {
                                                int range = (r + 1) * 16;
                                                net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                                                        bx - range, Math.max(by - 128, w.getBottomY()), bz - range,
                                                        bx + range, Math.min(by + 128, w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz)), bz + range
                                                );
                                                String expectedTag = tagToolId.isEmpty() ? null : ModConstants.SADDLE_TOOL_TAG_PREFIX + tagToolId;
                                                java.util.List<LivingEntity> cands = w.getEntitiesByClass(
                                                        LivingEntity.class,
                                                        box,
                                                        ent -> {
                                                            if (!ent.getCommandTags().contains(ModConstants.SUMMON_TAG)) return false;
                                                            if (ent.getUuid().equals(uuid)) return true;
                                                            return expectedTag != null && ent.getCommandTags().contains(expectedTag);
                                                        }
                                                );
                                                for (LivingEntity cand : cands) { holder2[0] = cand; break; }
                                            });
                                            e = holder2[0];
                                        }
                                    }
                                } catch (Throwable ignored3) {}
                            }
                            if (e instanceof LivingEntity livingProbe) {
                                // Recover tool id from entity tag if possible when ours is missing/mismatched
                                String useId = !toolId.isEmpty() ? toolId : extractSaddleToolId(livingProbe);
                                if (useId.isEmpty()) {
                                    useId = toolId; // fall back to current tool id
                                }
                                if (!useId.isEmpty()) {
                                    try { ModConstants.LOGGER.info("[Summon-XD] Dismissing remote linked entity in world={} uuid={}", com.tabletmc.echo_summon.util.ChunkLoadUtil.describeWorld((ServerWorld) livingProbe.getWorld()), storedIdStr); } catch (Throwable ignored) {}
                                    dismissSaddleSummonedMount(player, summonTool, livingProbe, useId, false, null);
                                    // For Happy Ghast, continue to summon immediately; otherwise return after dismiss
                                    if (!isHappyGhast) {
                                        return;
                                    }
                                }
                            }
                        }
                        if (isHappyGhast) {
                            // Happy Ghast handled locally later
                        } else if (storedRemoteActive) {
                            // Only enqueue for non-Happy mounts when remote is still active
                            try {
                                DeferredDismissStore store = DeferredDismissStore.get(server);
                                String dimStr = com.tabletmc.echo_summon.util.NbtUtils.getString(storedProbe, ModConstants.STORED_DIM_KEY);
                                store.addPending(uuid, dimStr, toolId);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        // After cleanup attempts, if remote_active remains, allow Sneak+Summon to force-clear; otherwise abort
        try {
            NbtComponent cd3 = summonTool.get(DataComponentTypes.CUSTOM_DATA);
            if (cd3 != null) {
                NbtCompound n3 = cd3.copyNbt();
                java.util.Optional<NbtCompound> st3 = n3.getCompound(ModConstants.STORED_MOUNT_KEY);
                if (st3.isPresent()) {
                    boolean remoteActive3 = false;
                    try { remoteActive3 = st3.get().getBoolean(ModConstants.REMOTE_ACTIVE_KEY).orElse(false); } catch (Throwable ignored) {}
                    if (remoteActive3) {
                        String storedType = com.tabletmc.echo_summon.util.NbtUtils.getString(st3.get(), "id");
                        boolean isHappyGhastFlag = storedType.endsWith("happy_ghast") || "happy_ghast".equals(storedType);
                        if (isHappyGhastFlag) {
                            // For Happy Ghast, auto-clear remote flag without requiring sneak so summon can proceed
                            NbtCompound s3 = st3.get();
                            s3.remove(ModConstants.REMOTE_ACTIVE_KEY);
                            n3.put(ModConstants.STORED_MOUNT_KEY, s3);
                            summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n3));
                        } else {
                            if (player.isSneaking()) {
                                NbtCompound s3 = st3.get();
                                s3.remove(ModConstants.REMOTE_ACTIVE_KEY);
                                n3.put(ModConstants.STORED_MOUNT_KEY, s3);
                                summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n3));
                            } else {
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        // As a final guard, sweep all loaded worlds for any entity with our tool tag and dismiss them (loaded chunks only)
        try {
            if (!toolId.isEmpty()) {
                String expectedTag = ModConstants.SADDLE_TOOL_TAG_PREFIX + toolId;
                var server = player.getServer();
                if (server != null) {
                    for (ServerWorld w : server.getWorlds()) {
                        net.minecraft.util.math.Box big = new net.minecraft.util.math.Box(-3.0e7, -3.0e7, -3.0e7, 3.0e7, 3.0e7, 3.0e7);
                        java.util.List<LivingEntity> candidates = w.getEntitiesByClass(
                                LivingEntity.class,
                                big,
                                e -> e.getCommandTags().contains(ModConstants.SUMMON_TAG) && e.getCommandTags().contains(expectedTag)
                        );
                        for (LivingEntity cand : candidates) {
                            dismissSaddleSummonedMount(player, summonTool, cand, toolId, false, null);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // If riding something that isn't our linked summoned mount, require manual dismount
        try {
            if (player.hasVehicle()) {
                boolean isSummoned = isIsSummoned(player, toolId);
                if (!isSummoned) {
                    return;
                }
            }
        } catch (Throwable ignored) {}
        NbtCompound stored = SummonPersistence.getStoredMount(summonTool);
        if (stored == null || stored.isEmpty()) {
            return;
        }
        // Sanitize stored NBT prior to spawn to avoid entity-load deferred-kill and tag mismatches
        try {
            // Clear any previous deferred-dismiss entries for this tool or stored UUID to prevent killing our new spawn
            var server = player.getServer();
            if (server != null) {
                DeferredDismissStore store = DeferredDismissStore.get(server);
                try {
                    String storedIdStr0 = com.tabletmc.echo_summon.util.NbtUtils.getString(stored, ModConstants.STORED_MOUNT_ID_KEY);
                    if (!storedIdStr0.isEmpty()) {
                        store.remove(java.util.UUID.fromString(storedIdStr0));
                    }
                } catch (Throwable ignored) {}
                try {
                    if (!toolId.isEmpty()) {
                        var byTool = store.findByToolId(toolId);
                        if (byTool.isPresent()) {
                            java.util.UUID pendingKey = java.util.UUID.fromString(byTool.get().getKey());
                            store.remove(pendingKey);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            // Remove our runtime tags so the spawned entity doesn't carry stale tool/summon tags from storage
            try { stored.remove("Tags"); } catch (Throwable ignored) {}
            // Force a new UUID for the spawned entity to decouple from any stale pending entries
            try { stored.remove("UUID"); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        // Ownership requirement removed per request
        // Removed legacy harness-summoned blocking check (no harness system)

        Entity loaded = EntityType.loadEntityWithPassengers(stored, player.getWorld(), SpawnReason.LOAD, Function.identity());
        if (!(loaded instanceof LivingEntity mount) || !ModConstants.isSaddleAllowed(loaded.getType())) {
            return;
        }
        if (mount instanceof AbstractHorseEntity ahe2) {
            try {
                ahe2.setTame(true);
            } catch (Throwable ignored) {}
        }
        try { mount.setHealth(mount.getMaxHealth()); } catch (Throwable ignored) {}
        mount.fallDistance = player.fallDistance;
        mount.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        // Raise Happy Ghast by +2 blocks to avoid collision/teleport artifacts
        try {
            net.minecraft.util.Identifier tId = net.minecraft.entity.EntityType.getId(mount.getType());
            if (tId != null && "happy_ghast".equals(tId.getPath())) {
                mount.setPosition(mount.getX(), mount.getY() + 2.0, mount.getZ());
            }
        } catch (Throwable ignored) {}
        mount.setVelocity(player.getVelocity());
        // Ensure AI is enabled for summon (Happy Ghast may carry NoAI in stored data)
        if (mount instanceof net.minecraft.entity.mob.MobEntity mob) {
            try { mob.setAiDisabled(false); } catch (Throwable ignored) {}
        }

        // Ensure allowed mounts have a mount_saddle equipped when summoned (with Equippable + mapping NBT)
        if (ModConstants.isSaddleAllowed(mount.getType())) {
            try {
                net.minecraft.util.Identifier typeId = net.minecraft.entity.EntityType.getId(mount.getType());
                boolean isHappyGhast = typeId != null && "happy_ghast".equals(typeId.getPath());
                if (isHappyGhast) {
                    // Ensure a harness is present on Happy Ghast when summoned
                    ItemStack body = mount.getEquippedStack(EquipmentSlot.BODY);
                    if (body.isEmpty() || !body.isOf(ModItems.MOUNT_HARNESS)) {
                        ItemStack harness = new ItemStack(ModItems.MOUNT_HARNESS);
                        MountHarnessItem.applyEquippable(harness, mount.getType());
                        mount.equipStack(EquipmentSlot.BODY, harness);
                    }
                } else {
                    ItemStack curr = mount.getEquippedStack(EquipmentSlot.SADDLE);
                    if (curr.isEmpty() || !curr.isOf(ModItems.MOUNT_SADDLE)) {
                        // Resolve final tool id using current tool data or fallback to toolId
                        String currentId = SummonToolLookup.getSaddleSummonToolId(summonTool);
                        String finalToolId = !currentId.isEmpty() ? currentId : toolId;
                        ItemStack ms = new ItemStack(ModItems.MOUNT_SADDLE);
                        MountSaddleItem.applyEquippable(ms, mount.getType());
                        NbtCompound sComp = new NbtCompound();
                        // Recover stored mount id from the stored NBT we spawned with, if present
                        String storedId = com.tabletmc.echo_summon.util.NbtUtils.getString(stored, ModConstants.STORED_MOUNT_ID_KEY);
                        sComp.putString(MountSaddleItem.STORED_MOUNT_ID_KEY, storedId);
                        if (!finalToolId.isEmpty()) {
                            sComp.putString(MountSaddleItem.ECHO_MOUNT_SUMMON_TOOL_ID_KEY, finalToolId);
                        }
                        ms.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(sComp));
                        mount.equipStack(EquipmentSlot.SADDLE, ms);
                        if (mount instanceof SaddleableMountImpl saddleable2) {
                            saddleable2.echo_summon$setSaddled(true);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        // Spawn and guard against failure; do not set summon state if spawn fails
        boolean spawned = false;
        try { spawned = player.getWorld().spawnEntity(mount); } catch (Throwable ignored) {}
        if (!spawned) {
            return;
        }

        // Tag after successful spawn to avoid immediate kill by deferred-dismiss entity-load hook
        try { mount.addCommandTag(ModConstants.SUMMON_TAG); } catch (Throwable ignored) {}

        NbtComponent toolCustom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound toolNbt = toolCustom != null ? toolCustom.copyNbt() : new NbtCompound();
        String resolvedToolId = SummonToolLookup.getSaddleSummonToolId(summonTool);
        if (resolvedToolId.isEmpty()) {
            resolvedToolId = java.util.UUID.randomUUID().toString();
            toolNbt.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, resolvedToolId);
            java.util.Optional<net.minecraft.nbt.NbtCompound> storedOpt = toolNbt.getCompound(ModConstants.STORED_MOUNT_KEY);
            if (storedOpt.isPresent()) {
                net.minecraft.nbt.NbtCompound storedCompound = storedOpt.get();
                storedCompound.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, resolvedToolId);
                toolNbt.put(ModConstants.STORED_MOUNT_KEY, storedCompound);
            }
            summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(toolNbt));
        }
        if (!resolvedToolId.isEmpty()) {
            String toolTag = ModConstants.SADDLE_TOOL_TAG_PREFIX + resolvedToolId;
            mount.addCommandTag(toolTag);
        }

        // Persist live summoned UUID + tool id back into the tool for reliable global lookup
        try {
            NbtComponent tc2 = summonTool.get(DataComponentTypes.CUSTOM_DATA);
            NbtCompound tn2 = tc2 != null ? tc2.copyNbt() : new NbtCompound();
            java.util.Optional<NbtCompound> optStored = tn2.getCompound(ModConstants.STORED_MOUNT_KEY);
            if (optStored.isPresent()) {
                NbtCompound storedCompound = updateStoredMountNbt(mount, optStored.get(), resolvedToolId);
                tn2.put(ModConstants.STORED_MOUNT_KEY, storedCompound);
                summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tn2));
            }
        } catch (Throwable ignored) {}

        player.startRiding(mount, true);
        player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS);
        try { com.tabletmc.echo_summon.net.service.GlobalSummonCooldowns.set(player, ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored) {}
        // Mark tool-side summon flag
        try {
            NbtComponent tcd3 = summonTool.get(DataComponentTypes.CUSTOM_DATA);
            NbtCompound tnbt3 = tcd3 != null ? tcd3.copyNbt() : new NbtCompound();
            tnbt3.putBoolean(ModConstants.MOUNT_SUMMONED_TOOL_FLAG, true);
            summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tnbt3));
        } catch (Throwable ignored) {}
    }

        private static boolean isIsSummoned(ServerPlayerEntity player, String toolId) {
            Entity veh = player.getVehicle();
            boolean isSummoned = false;
            if (veh instanceof LivingEntity lv) {
                if (lv.getCommandTags().contains(ModConstants.SUMMON_TAG)) {
                    if (!toolId.isEmpty()) {
                        String expectedTag = ModConstants.SADDLE_TOOL_TAG_PREFIX + toolId;
                        isSummoned = lv.getCommandTags().contains(expectedTag);
                    }
                }
            }
            return isSummoned;
        }

        private static @NotNull NbtCompound updateStoredMountNbt(LivingEntity mount, NbtCompound storedCompound, String resolvedToolId) {
            storedCompound.putString(ModConstants.STORED_MOUNT_ID_KEY, mount.getUuidAsString());
            // Always overwrite with the current tool id to sanitize any legacy Optional[...] values
            storedCompound.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, resolvedToolId);
            // Also update dimension and position
            try {
                String dimStr = mount.getWorld().getRegistryKey().getValue().toString();
                storedCompound.putString(ModConstants.STORED_DIM_KEY, dimStr);
                net.minecraft.util.math.BlockPos bp = mount.getBlockPos();
                NbtCompound posNbt = new NbtCompound();
                posNbt.putInt("x", bp.getX());
                posNbt.putInt("y", bp.getY());
                posNbt.putInt("z", bp.getZ());
                storedCompound.put(ModConstants.STORED_POS_KEY, posNbt);
                // Clear remote_active once we've successfully spawned a new instance
                storedCompound.remove(ModConstants.REMOTE_ACTIVE_KEY);
            } catch (Throwable ignored3) {}
            return storedCompound;
        }

        /**
     * Release the stored mount as a permanent world entity and clear storage from the tool.
     * Blocks release if a linked summoned mount is currently active to prevent duplication.
     */ 

    private static void handleRelease(ServerPlayerEntity player, ItemStack summonTool) {
        // Release spawns a permanent mount and clears storage; ensure we actually have data
        NbtComponent custom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound comp = custom != null ? custom.copyNbt() : null;
        if (comp == null) {
            return;
        }
        // Prevent release if a remote-active instance exists (e.g., Happy Ghast parked elsewhere)
        try {
            java.util.Optional<NbtCompound> st = comp.getCompound(ModConstants.STORED_MOUNT_KEY);
            if (st.isPresent() && st.get().getBoolean(ModConstants.REMOTE_ACTIVE_KEY).orElse(false)) {
                return;
            }
        } catch (Throwable ignored) {}
        // If a linked summoned mount exists anywhere, block release until dismissed
        LivingEntity linked = findSaddleSummonedMount(player, summonTool);
        if (linked != null) {
            return;
        }
        SummonPersistence.persistSummonedMountByStoredIdIfPresent(player, summonTool);
        NbtComponent refreshed = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        if (refreshed != null) {
            comp = refreshed.copyNbt();
        }
        java.util.Optional<NbtCompound> opt = comp.getCompound(ModConstants.STORED_MOUNT_KEY);
        if (opt.isEmpty() || opt.get().isEmpty()) {
            return;
        }
        NbtCompound stored = opt.get();
        // Read and strip custom mod NBT before spawning
        boolean hadVanilla = false;
        try {
            hadVanilla = stored.getBoolean(HAD_VANILLA_SADDLE_KEY).orElse(false);
        } catch (Throwable ignored) {}
        stored.remove(HAD_VANILLA_SADDLE_KEY);
        stored.remove(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
        stored.remove(ModConstants.STORED_MOUNT_ID_KEY);
        stored.remove(ModConstants.MOUNT_SADDLE_DATA_KEY);
        stored.remove("owner_name");

        Entity loaded = EntityType.loadEntityWithPassengers(stored, player.getWorld(), SpawnReason.LOAD, Function.identity());
        if (!(loaded instanceof LivingEntity mount) || !ModConstants.isSaddleAllowed(loaded.getType())) {
            return;
        }
        try {
            mount.removeCommandTag(ModConstants.SUMMON_TAG);
            for (String tag : new java.util.ArrayList<>(mount.getCommandTags())) {
                if (tag.startsWith(ModConstants.SADDLE_TOOL_TAG_PREFIX)) {
                    mount.removeCommandTag(tag);
                }
            }
        } catch (Throwable ignored) {}

        // Remove mount_saddle and restore vanilla saddle if the mount originally had one
        if (ModConstants.isSaddleAllowed(mount.getType())) {
            try {
                net.minecraft.util.Identifier typeId = net.minecraft.entity.EntityType.getId(mount.getType());
                boolean isHappyGhast = typeId != null && "happy_ghast".equals(typeId.getPath());
                if (isHappyGhast) {
                    // Strip harness on release
                    ItemStack body = mount.getEquippedStack(EquipmentSlot.BODY);
                    if (!body.isEmpty() && body.isOf(ModItems.MOUNT_HARNESS)) {
                        mount.equipStack(EquipmentSlot.BODY, ItemStack.EMPTY);
                    }
                } else {
                    ItemStack curr = mount.getEquippedStack(EquipmentSlot.SADDLE);
                    if (!curr.isEmpty() && curr.isOf(ModItems.MOUNT_SADDLE)) {
                        mount.equipStack(EquipmentSlot.SADDLE, ItemStack.EMPTY);
                    }
                    if (hadVanilla) {
                        mount.equipStack(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Place the mount a short, safe distance in front of the player
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d spawnPos = findSafeSpawnPosition(player, look, 3.0, 5.0);
        mount.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, player.getYaw(), player.getPitch());

        player.getWorld().spawnEntity(mount);

        // Clear stored mount and model data from the tool
        comp.remove(ModConstants.STORED_MOUNT_KEY);
        summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(comp));
        try {
            summonTool.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
        } catch (Throwable ignored) {}

        String toolId = SummonToolLookup.getSaddleSummonToolId(summonTool);
        if (!toolId.isEmpty()) {
            // If we had a tool id, forget it on release (tool becomes unbound)
            comp.remove(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
            summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(comp));
        }
        // Clear summon flag
        try {
            NbtComponent tcd = summonTool.get(DataComponentTypes.CUSTOM_DATA);
            if (tcd != null) {
                NbtCompound tnbt = tcd.copyNbt();
                tnbt.remove(ModConstants.MOUNT_SUMMONED_TOOL_FLAG);
                summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tnbt));
            }
        } catch (Throwable ignored) {}

        player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS);
    }

    /**
     * Dismiss the currently linked summoned mount for this tool. Prefers the mount the player
     * is currently riding if it matches the tool id; otherwise searches globally for the linked entity.
     */ 

    private static void handleDismiss(ServerPlayerEntity player, ItemStack summonTool) {
        // Prefer dismissing the ridden linked mount
        Entity vehicle = player.getVehicle();
        String toolId = SummonToolLookup.getSaddleSummonToolId(summonTool);
        // Ownership requirement removed per request
        if (vehicle instanceof LivingEntity riding && riding.getCommandTags().contains(ModConstants.SUMMON_TAG)) {
            if (!toolId.isEmpty()) {
                String expectedTag = ModConstants.SADDLE_TOOL_TAG_PREFIX + toolId;
                if (riding.getCommandTags().contains(expectedTag)) {
                    dismissSaddleSummonedMount(player, summonTool, riding, toolId, false, null);
                    return;
                }

            }
            // Tool id missing or mismatched: recover id from the entity's tag if present
            String recovered = extractSaddleToolId(riding);
            if (!recovered.isEmpty()) {
                dismissSaddleSummonedMount(player, summonTool, riding, recovered, false, null);
                return;
            }
        }
        // Else, find the linked summoned mount across worlds and dismiss it
        LivingEntity found = findSaddleSummonedMount(player, summonTool);
        if (found != null) {
            String useId = !toolId.isEmpty() ? toolId : extractSaddleToolId(found);
            if (!useId.isEmpty()) {
                dismissSaddleSummonedMount(player, summonTool, found, useId, false, null);
                return;
            }
        }
        // Cross-dimension Happy Ghast fallback: use stored UUID to locate and dismiss in other worlds
        try {
            NbtComponent tcd = summonTool.get(DataComponentTypes.CUSTOM_DATA);
            NbtCompound tnbt = tcd != null ? tcd.copyNbt() : null;
            if (tnbt != null) {
                java.util.Optional<NbtCompound> optStored = tnbt.getCompound(ModConstants.STORED_MOUNT_KEY);
                if (optStored.isPresent()) {
                    NbtCompound storedCompound = optStored.get();
                    String storedIdStr = com.tabletmc.echo_summon.util.NbtUtils.getString(storedCompound, ModConstants.STORED_MOUNT_ID_KEY);
                    String storedTypeStr = com.tabletmc.echo_summon.util.NbtUtils.getString(storedCompound, "id");
                    boolean storedIsHappyGhast = storedTypeStr.endsWith("happy_ghast") || "happy_ghast".equals(storedTypeStr);
                    if (!storedIdStr.isEmpty()) {
                        java.util.UUID uuid = java.util.UUID.fromString(storedIdStr);
                        var server = player.getServer();
                        if (server != null) {
                            for (ServerWorld w : server.getWorlds()) {
                                Entity e = w.getEntity(uuid);
                                    if (e == null) {
                                        // Try ticket-forcing the stored chunk then retry
                                        try {
                                            java.util.Optional<NbtCompound> posOpt = optStored.get().getCompound("echo_summon:stored_pos");
                                            if (posOpt.isPresent()) {
                                                NbtCompound pos = posOpt.get();
                                                int cx = pos.getInt("x").orElse(0) >> 4;
                                            int cz = pos.getInt("z").orElse(0) >> 4;
                                            final Entity[] holder = new Entity[1];
                                            com.tabletmc.echo_summon.util.ChunkLoadUtil.forceChunkWithTicket(w, cx, cz, () -> {
                                                holder[0] = w.getEntity(uuid);
                                            });
                                            e = holder[0];
                                        }
                                    } catch (Throwable ignored2) {}
                                }
                                if (e == null) {
                                    // Expand load radius and scan locally for a matching entity near the stored position
                                    try {
                                        java.util.Optional<NbtCompound> posOpt2 = optStored.get().getCompound("echo_summon:stored_pos");
                                        if (posOpt2.isPresent()) {
                                            NbtCompound pos2 = posOpt2.get();
                                            int bx = pos2.getInt("x").orElse(0);
                                            int by = pos2.getInt("y").orElse(64);
                                            int bz = pos2.getInt("z").orElse(0);
                                            int cx0 = bx >> 4, cz0 = bz >> 4;
                                            int r = 3;
                                            final Entity[] holder2 = new Entity[1];
                                            com.tabletmc.echo_summon.util.ChunkLoadUtil.forceAreaWithTickets(w, cx0, cz0, r, () -> {
                                                int range = (r + 1) * 16;
                                                net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                                                        bx - range, Math.max(by - 128, w.getBottomY()), bz - range,
                                                        bx + range, Math.min(by + 128, w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz)), bz + range
                                                );
                                                String expectedTag = toolId.isEmpty() ? null : ModConstants.SADDLE_TOOL_TAG_PREFIX + toolId;
                                                java.util.List<LivingEntity> cands = w.getEntitiesByClass(
                                                        LivingEntity.class,
                                                        box,
                                                        ent -> {
                                                            if (!ent.getCommandTags().contains(ModConstants.SUMMON_TAG)) return false;
                                                            if (ent.getUuid().equals(uuid)) return true;
                                                            return expectedTag != null && ent.getCommandTags().contains(expectedTag);
                                                        }
                                                );
                                                for (LivingEntity cand : cands) { holder2[0] = cand; break; }
                                            });
                                            e = holder2[0];
                                        }
                                    } catch (Throwable ignored3) {}
                                }
                                if (e instanceof LivingEntity livingProbe) {
                                    String recovered = extractSaddleToolId(livingProbe);
                                    String useId = !toolId.isEmpty() ? toolId : recovered;
                                    if (!useId.isEmpty()) {
                                        dismissSaddleSummonedMount(player, summonTool, livingProbe, useId, false, null);
                                        return;
                                    }
                                }
                            }
                            if (storedIsHappyGhast) {
                                // Happy Ghast: clear flags locally and allow summon to proceed without queueing
                                try {
                                    tcd = summonTool.get(DataComponentTypes.CUSTOM_DATA);
                                    if (tcd != null) {
                                        tnbt = tcd.copyNbt();
                                        tnbt.remove(ModConstants.MOUNT_SUMMONED_TOOL_FLAG);
                                        java.util.Optional<NbtCompound> st = tnbt.getCompound(ModConstants.STORED_MOUNT_KEY);
                                        if (st.isPresent()) {
                                            NbtCompound s = st.get();
                                            s.remove(ModConstants.REMOTE_ACTIVE_KEY);
                                            tnbt.put(ModConstants.STORED_MOUNT_KEY, s);
                                        }
                                        summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tnbt));
                                    }
                                } catch (Throwable ignored3) {}
                                try { player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored4) {}
                                try { com.tabletmc.echo_summon.net.service.GlobalSummonCooldowns.set(player, ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored5) {}
                                return;
                            } else {
                                // Not found anywhere: record deferred dismiss for other mounts
                                try {
                                    DeferredDismissStore store = DeferredDismissStore.get(server);
                                    String dimStr = com.tabletmc.echo_summon.util.NbtUtils.getString(storedCompound, ModConstants.STORED_DIM_KEY);
                                    store.addPending(uuid, dimStr, toolId);
                                    // Since we have queued a cross-dimension dismiss, proactively clear flags on the tool
                                    // so the player can summon again without being forced into dismiss.
                                    try {
                                        // Clear summon flag
                                        tcd = summonTool.get(DataComponentTypes.CUSTOM_DATA);
                                        if (tcd != null) {
                                            tnbt = tcd.copyNbt();
                                            tnbt.remove(ModConstants.MOUNT_SUMMONED_TOOL_FLAG);
                                            // Also clear remote_active if present
                                            java.util.Optional<NbtCompound> st = tnbt.getCompound(ModConstants.STORED_MOUNT_KEY);
                                            if (st.isPresent()) {
                                                NbtCompound s = st.get();
                                                s.remove(ModConstants.REMOTE_ACTIVE_KEY);
                                                tnbt.put(ModConstants.STORED_MOUNT_KEY, s);
                                            }
                                            summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tnbt));
                                        }
                                    } catch (Throwable ignored3) {}
                                    // Apply cooldown and return
                                    try { player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored4) {}
                                    try { com.tabletmc.echo_summon.net.service.GlobalSummonCooldowns.set(player, ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored5) {}
                                    return;
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        // If we reached here, no linked summoned entity was found. Try to enqueue a deferred dismiss
        // using the stored UUID/dimension if present. If enqueued, clear flags, cooldown, message, and return.
        try {
            NbtComponent cd = summonTool.get(DataComponentTypes.CUSTOM_DATA);
            NbtCompound nbt = cd != null ? cd.copyNbt() : null;
            java.util.Optional<NbtCompound> st = (nbt != null) ? nbt.getCompound(ModConstants.STORED_MOUNT_KEY) : java.util.Optional.empty();
            if (st.isPresent()) {
                NbtCompound stored = st.get();
                String storedIdStr = com.tabletmc.echo_summon.util.NbtUtils.getString(stored, ModConstants.STORED_MOUNT_ID_KEY);
                String storedTypeStr = com.tabletmc.echo_summon.util.NbtUtils.getString(stored, "id");
                boolean storedIsHappyGhast = storedTypeStr.endsWith("happy_ghast") || "happy_ghast".equals(storedTypeStr);
                if (!storedIdStr.isEmpty()) {
                    java.util.UUID uuid = java.util.UUID.fromString(storedIdStr);
                    var server = player.getServer();
                    if (server != null) {
                        boolean remoteActiveFlag2 = false;
                        try { remoteActiveFlag2 = stored.getBoolean(ModConstants.REMOTE_ACTIVE_KEY).orElse(false); } catch (Throwable ignoredX) {}
                        if (remoteActiveFlag2 || !stored.isEmpty()) {
                            if (storedIsHappyGhast && remoteActiveFlag2) {
                                // Happy Ghast: just clear remote flags locally, no queue
                                try {
                                    nbt.remove(ModConstants.MOUNT_SUMMONED_TOOL_FLAG);
                                    java.util.Optional<NbtCompound> st2 = nbt.getCompound(ModConstants.STORED_MOUNT_KEY);
                                    if (st2.isPresent()) {
                                        NbtCompound s2 = st2.get();
                                        s2.remove(ModConstants.REMOTE_ACTIVE_KEY);
                                        nbt.put(ModConstants.STORED_MOUNT_KEY, s2);
                                    }
                                    summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                                } catch (Throwable ignored3) {}
                                try { player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored4) {}
                                try { com.tabletmc.echo_summon.net.service.GlobalSummonCooldowns.set(player, ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored5) {}
                                return;
                            } else {
                                DeferredDismissStore store = DeferredDismissStore.get(server);
                                String dimStr = com.tabletmc.echo_summon.util.NbtUtils.getString(stored, ModConstants.STORED_DIM_KEY);
                                store.addPending(uuid, dimStr, toolId);
                                // Clear summon flag and remote_active, apply cooldown
                                try {
                                    nbt.remove(ModConstants.MOUNT_SUMMONED_TOOL_FLAG);
                                    java.util.Optional<NbtCompound> st2 = nbt.getCompound(ModConstants.STORED_MOUNT_KEY);
                                    if (st2.isPresent()) {
                                        NbtCompound s2 = st2.get();
                                        s2.remove(ModConstants.REMOTE_ACTIVE_KEY);
                                        nbt.put(ModConstants.STORED_MOUNT_KEY, s2);
                                    }
                                    summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                                } catch (Throwable ignored3) {}
                                try { player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored4) {}
                                try { com.tabletmc.echo_summon.net.service.GlobalSummonCooldowns.set(player, ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored5) {}
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored2) {}
        // Fall back: clear summon flag only and inform player
        try {
            NbtComponent tcd = summonTool.get(DataComponentTypes.CUSTOM_DATA);
            if (tcd != null) {
                NbtCompound tnbt = tcd.copyNbt();
                tnbt.remove(ModConstants.MOUNT_SUMMONED_TOOL_FLAG);
                summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tnbt));
            }
        } catch (Throwable ignored5) {}
        // no user-facing message
    }

    /**
     * Persist the current state of a saddle-summoned mount back to the tool and remove it from the world.
     * Validates that the provided tool actually owns the living entity via the per-tool command tag.
     *
     * @param actionBar when true, show feedback in the action bar; otherwise as a chat message
     */ 

    public static void dismissSaddleSummonedMount(ServerPlayerEntity player, ItemStack summonTool, LivingEntity living, String toolId, boolean actionBar, Text feedback) {
        if (player == null || living == null) {
            return;
        }
        if (toolId == null || toolId.isEmpty()) {
            return;
        }
        if (summonTool == null || summonTool.isEmpty()) {
            return;
        }
        // Resolve the correct owning tool if the provided stack has a different id
        ItemStack owningTool = summonTool;
        String verifyId = SummonToolLookup.getSaddleSummonToolId(summonTool);
        if (!toolId.equals(verifyId)) {
            ItemStack resolved = SummonToolLookup.findSaddleSummonToolById(player, toolId);
            if (!resolved.isEmpty()) {
                owningTool = resolved;
            } else {
                // Permit dismissal for Happy Ghast even if tool id desynced
                boolean allow = false;
                try {
                    net.minecraft.util.Identifier tId = net.minecraft.entity.EntityType.getId(living.getType());
                    allow = tId != null && "happy_ghast".equals(tId.getPath());
                } catch (Throwable ignored) {}
                if (!allow) {
                    return;
                }
            }
        }
        // Persist current mount state (captures donkey/mule chest inventory) before removal
        try {
            SummonPersistence.persistSaddleSummonedMountToTool(player, owningTool, living, toolId, true);
        } catch (Throwable ignored) {}
        // No special-case logic for any specific mount types on dismiss
        living.remove(Entity.RemovalReason.DISCARDED);
        living.removeCommandTag(ModConstants.SUMMON_TAG);
        living.removeCommandTag(ModConstants.SADDLE_TOOL_TAG_PREFIX + toolId);
        if (!owningTool.isEmpty()) {
            player.getItemCooldownManager().set(owningTool, ModConstants.SUMMON_COOLDOWN_TICKS);
            try { com.tabletmc.echo_summon.net.service.GlobalSummonCooldowns.set(player, ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored) {}
        }
        // Clear remote_active flag from tool since the remote instance is gone
        try {
            NbtComponent cd = owningTool.get(DataComponentTypes.CUSTOM_DATA);
            if (cd != null) {
                NbtCompound nbt = cd.copyNbt();
                java.util.Optional<NbtCompound> st = nbt.getCompound(ModConstants.STORED_MOUNT_KEY);
                if (st.isPresent()) {
                    NbtCompound s = st.get();
                    s.remove(ModConstants.REMOTE_ACTIVE_KEY);
                    nbt.put(ModConstants.STORED_MOUNT_KEY, s);
                    owningTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                }
            }
        } catch (Throwable ignored) {}
        // Clear tool-side summon flag
        try {
            NbtComponent tcd = owningTool.get(DataComponentTypes.CUSTOM_DATA);
            if (tcd != null) {
                NbtCompound tnbt = tcd.copyNbt();
                tnbt.remove(ModConstants.MOUNT_SUMMONED_TOOL_FLAG);
                owningTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tnbt));
            }
        } catch (Throwable ignored) {}
        // suppressed: no user-facing message
    }

    /**
     * Extract the tool id from a living entity's command tags.
     * The tag format is SADDLE_TOOL_TAG_PREFIX + toolId.
     */ 

    private static String extractSaddleToolId(LivingEntity living) {
        for (String tag : living.getCommandTags()) {
            if (tag.startsWith(ModConstants.SADDLE_TOOL_TAG_PREFIX)) {
                String raw = tag.substring(ModConstants.SADDLE_TOOL_TAG_PREFIX.length());
                if (raw.startsWith("Optional[") && raw.endsWith("]")) {
                    raw = raw.substring("Optional[".length(), raw.length() - 1);
                }
                return raw;
            }
        }
        return "";
    }

    /**
     * Locate the saddle-summoned mount associated with the provided tool.
     * Strategy:
     * 1) If a stored UUID exists in the tool, directly query all worlds for that entity and verify tags.
     * 2) Otherwise, run a nearby search box scan for entities with both SUMMON_TAG and the per-tool tag.
     */ 

    private static LivingEntity findSaddleSummonedMount(ServerPlayerEntity player, ItemStack summonTool) {
        // Try exact lookup by stored UUID; fallback to a local area scan by tags
        if (player == null || summonTool == null || summonTool.isEmpty()) {
            return null;
        }
        String toolId = SummonToolLookup.getSaddleSummonToolId(summonTool);
        String storedIdStr = SummonToolLookup.getCompoundData(summonTool, ModConstants.STORED_MOUNT_KEY)
                .map(n -> com.tabletmc.echo_summon.util.NbtUtils.getString(n, ModConstants.STORED_MOUNT_ID_KEY))
                .orElse("");

        // 1) If we know the stored UUID, scan all server worlds for an entity with that UUID and tags
        if (!storedIdStr.isEmpty()) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(storedIdStr);
                var server = player.getServer();
                if (server != null) {
                    for (ServerWorld world : server.getWorlds()) {
                        Entity entity = world.getEntity(uuid);
                        if (entity instanceof LivingEntity living && living.getCommandTags().contains(ModConstants.SUMMON_TAG)) {
                            if (toolId.isEmpty()) {
                                return living;
                            }
                            String expectedTag = ModConstants.SADDLE_TOOL_TAG_PREFIX + toolId;
                            if (living.getCommandTags().contains(expectedTag)) {
                                return living;
                            }
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // 2) Nearby fallback search by tags
        if (!toolId.isEmpty()) {
            String expectedTag = ModConstants.SADDLE_TOOL_TAG_PREFIX + toolId;
            net.minecraft.util.math.Box searchBox = player.getBoundingBox().expand(64.0);
            java.util.List<LivingEntity> candidates = player.getWorld().getEntitiesByClass(
                    LivingEntity.class,
                    searchBox,
                    e -> e.getCommandTags().contains(ModConstants.SUMMON_TAG) && e.getCommandTags().contains(expectedTag)
            );
            for (LivingEntity candidate : candidates) {
                return candidate;
            }
        }
        return null;
    }
}
