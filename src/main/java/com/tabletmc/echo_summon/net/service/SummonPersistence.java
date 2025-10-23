package com.tabletmc.echo_summon.net.service;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.item.custom.MountSaddleItem;
import com.tabletmc.echo_summon.util.NbtUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.List;

import static com.tabletmc.echo_summon.util.ModelKeyUtil.mapEntityTypeToModelKey;

/**
 * Utilities for persisting saddle-summoned mounts into the tool's NBT and restoring
 * minimal state across dismiss/summon cycles. This centralizes server-side write logic
 * for the tool's `CUSTOM_DATA` component.
 */
public final class SummonPersistence {
    private SummonPersistence() {}

    /**
     * Returns true if the given stack has a non-empty stored mount record under
     * {@code ModConstants.STORED_MOUNT_KEY}.
     */
    public static boolean hasStoredMount(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return false;
        NbtCompound comp = custom.copyNbt();
        return comp.getCompound(ModConstants.STORED_MOUNT_KEY).map(n -> !n.isEmpty()).orElse(false);
    }

    /**
     * Returns the stored mount compound for the given stack, or null if absent/empty.
     */
    public static NbtCompound getStoredMount(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return null;
        NbtCompound comp = custom.copyNbt();
        return comp.getCompound(ModConstants.STORED_MOUNT_KEY).filter(n -> !n.isEmpty()).orElse(null);
    }

    /**
     * If the tool already records a stored mount UUID, find the in-world entity and
     * persist its current state back into the tool.
     */
    public static void persistSummonedMountByStoredIdIfPresent(ServerPlayerEntity player, ItemStack summonTool) {
        if (player == null || summonTool == null || summonTool.isEmpty()) return;
        NbtComponent custom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return;
        NbtCompound comp = custom.copyNbt();
        String toolId = NbtUtils.getString(comp, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
        var opt = comp.getCompound(ModConstants.STORED_MOUNT_KEY);
        if (toolId.isEmpty() || opt.isEmpty() || opt.get().isEmpty()) return;
        String storedIdStr = NbtUtils.getString(opt.get(), ModConstants.STORED_MOUNT_ID_KEY);
        if (storedIdStr.isEmpty()) return;
        var server = player.getServer();
        if (server == null) return;
        try {
            java.util.UUID uuid = java.util.UUID.fromString(storedIdStr);
            for (ServerWorld w : server.getWorlds()) {
                Entity e = w.getEntity(uuid);
                if (e instanceof LivingEntity living && living.getCommandTags().contains(ModConstants.SUMMON_TAG)) {
                    persistSaddleSummonedMountToTool(player, summonTool, living, toolId);
                    break;
                }
            }
        } catch (IllegalArgumentException ignored) {}
    }

    /**
     * Persist the given living entity's state back into the saddle summon tool using the
     * provided tool id. Convenience overload with {@code suppressRemoteActive=false}.
     */
    public static void persistSaddleSummonedMountToTool(ServerPlayerEntity player, ItemStack summonTool, LivingEntity living, String toolId) {
        persistSaddleSummonedMountToTool(player, summonTool, living, toolId, false);
    }

    /**
     * Persist the given living entity's state back into the saddle summon tool. Records
     * entity type, UUID, dimension, position, and saddle metadata as needed. When
     * {@code suppressRemoteActive} is true, skips marking the remote-active flag.
     */
    public static void persistSaddleSummonedMountToTool(ServerPlayerEntity player, ItemStack summonTool, LivingEntity living, String toolId, boolean suppressRemoteActive) {
        if (summonTool == null || summonTool.isEmpty() || living == null) return;
        NbtComponent toolCustom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound toolNbt = toolCustom != null ? toolCustom.copyNbt() : new NbtCompound();
        String resolvedToolId = (toolId != null && !toolId.isEmpty()) ? toolId : NbtUtils.getString(toolNbt, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
        resolvedToolId = sanitizeOptionalWrapper(resolvedToolId);

        NbtCompound stored = net.minecraft.predicate.NbtPredicate.entityToNbt(living);
        Identifier typeId = EntityType.getId(living.getType());
        if (typeId != null) stored.putString("id", typeId.toString());
        stored.putString(ModConstants.STORED_MOUNT_ID_KEY, living.getUuidAsString());
        // Mark that a summoned instance is still active in the world for cross-dimension cleanup
        try {
            if (!suppressRemoteActive && !living.isRemoved()) {
                stored.putBoolean(ModConstants.REMOTE_ACTIVE_KEY, true);
            }
        } catch (Throwable ignored) {}
        // Also record dimension and position for potential cross-dimension cleanup
        try {
            String dimId = living.getWorld().getRegistryKey().getValue().toString();
            stored.putString(ModConstants.STORED_DIM_KEY, dimId);
            net.minecraft.util.math.BlockPos bp = living.getBlockPos();
            NbtCompound pos = new NbtCompound();
            pos.putInt("x", bp.getX());
            pos.putInt("y", bp.getY());
            pos.putInt("z", bp.getZ());
            stored.put(ModConstants.STORED_POS_KEY, pos);
        } catch (Throwable ignored) {}
        if (!resolvedToolId.isEmpty()) {
            // Always overwrite with sanitized id to heal legacy Optional[...] values
            stored.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, resolvedToolId);
            toolNbt.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, resolvedToolId);
        }

        EquipmentSlot slot = MountSaddleItem.resolveSlot(living.getType());
        EquipmentSlot ps = slot != null ? slot : EquipmentSlot.SADDLE;
        ItemStack eq = living.getEquippedStack(ps);
        if (!eq.isEmpty() && eq.isOf(ModItems.MOUNT_SADDLE)) {
            NbtComponent sd = eq.get(DataComponentTypes.CUSTOM_DATA);
            if (sd != null) {
                NbtCompound sdNbt = sd.copyNbt();
                if (!sdNbt.contains("mount_type")) {
                    sdNbt.putString("mount_type", EntityType.getId(living.getType()).toString());
                }
                stored.put(ModConstants.MOUNT_SADDLE_DATA_KEY, sdNbt);
            }
        }

        // Strip runtime-only fields that should not persist across spawns
        try { stored.remove("Tags"); } catch (Throwable ignored) {}
        try { stored.remove("UUID"); } catch (Throwable ignored) {}
        toolNbt.put(ModConstants.STORED_MOUNT_KEY, stored);
        toolNbt.remove(ModConstants.MOUNT_SUMMONED_TOOL_FLAG);
        summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(toolNbt));
        try {
            String idStr = typeId != null ? typeId.toString() : NbtUtils.getString(stored, "id");
            String modelKey = mapEntityTypeToModelKey(idStr);
            if (modelKey != null && !modelKey.isEmpty()) {
                summonTool.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(), List.of(), List.of(modelKey), List.of()));
            } else {
                summonTool.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
            }
        } catch (Throwable ignored) {}
    }

    private static String sanitizeOptionalWrapper(String s) {
        if (s == null) return "";
        if (s.startsWith("Optional[") && s.endsWith("]")) {
            return s.substring("Optional[".length(), s.length() - 1);
        }
        return s;
    }
}
