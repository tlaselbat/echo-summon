package com.tabletmc.echo_summon.net.service;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.item.custom.SaddleSummonToolItem;
import com.tabletmc.echo_summon.util.NbtUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

import java.util.Optional;

/**
 * Utility helpers for locating saddle and harness summon tools.
 */
public final class SummonToolLookup {
    private SummonToolLookup() {}

    public static ItemStack findSummonToolInHand(ServerPlayerEntity player) {
        ItemStack main = player.getStackInHand(Hand.MAIN_HAND);
        if (main.getItem() instanceof SaddleSummonToolItem) {
            return main;
        }
        ItemStack off = player.getStackInHand(Hand.OFF_HAND);
        if (off.getItem() instanceof SaddleSummonToolItem) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findSaddleSummonToolById(ServerPlayerEntity player, String toolId) {
        if (toolId == null || toolId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandStack();
        if (isMatchingSaddleSummonTool(main, toolId)) {
            return main;
        }
        ItemStack off = player.getOffHandStack();
        if (isMatchingSaddleSummonTool(off, toolId)) {
            return off;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (isMatchingSaddleSummonTool(stack, toolId)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean isMatchingSaddleSummonTool(ItemStack stack, String toolId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof SaddleSummonToolItem)) {
            return false;
        }
        return toolId.equals(getSaddleSummonToolId(stack));
    }





    public static String getSaddleSummonToolId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return getStringData(stack, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
    }

    public static String getStringData(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty() || key == null || key.isEmpty()) {
            return "";
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return "";
        }
        return NbtUtils.getString(custom.copyNbt(), key);
    }

    public static Optional<NbtCompound> getCompoundData(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty() || key == null || key.isEmpty()) {
            return Optional.empty();
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return Optional.empty();
        }
        return custom.copyNbt().getCompound(key);
    }

    public static NbtCompound getOrCreateCompoundData(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty() || key == null || key.isEmpty()) {
            return new NbtCompound();
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return new NbtCompound();
        }
        return custom.copyNbt().getCompound(key).orElse(new NbtCompound());
    }

    /**
     * Ensure the current tool's id is unique in the player's inventory. If duplicates are detected and there is
     * no linked summoned entity, generate and persist a new id into this stack. Returns the final (possibly new) id.
     */
    public static String ensureUniqueToolId(ServerPlayerEntity player, ItemStack currentTool, boolean hasLinkedSummoned) {
        String curId = getSaddleSummonToolId(currentTool);
        if (curId.isEmpty()) {
            String newId = java.util.UUID.randomUUID().toString();
            writeToolId(currentTool, newId);
            ModConstants.LOGGER.info("[ToolId] Assigned new id={} (was empty)", newId);
            return newId;
        }
        int dupCount = countStacksWithToolId(player, curId);
        if (dupCount > 1 && !hasLinkedSummoned) {
            String newId = java.util.UUID.randomUUID().toString();
            writeToolId(currentTool, newId);
            ModConstants.LOGGER.info("[ToolId] Regenerated id due to duplicates: oldId={}, newId={}, duplicates={}", curId, newId, dupCount);
            return newId;
        }
        return curId;
    }

    private static int countStacksWithToolId(ServerPlayerEntity player, String toolId) {
        int count = 0;
        if (isMatchingSaddleSummonTool(player.getMainHandStack(), toolId)) count++;
        if (isMatchingSaddleSummonTool(player.getOffHandStack(), toolId)) count++;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (isMatchingSaddleSummonTool(s, toolId)) count++;
        }
        return count;
    }

    private static void writeToolId(ItemStack stack, String newId) {
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = cd != null ? cd.copyNbt() : new NbtCompound();
        nbt.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, newId);
        // If a stored mount exists, also update its embedded tool id
        try {
            java.util.Optional<NbtCompound> st = nbt.getCompound(ModConstants.STORED_MOUNT_KEY);
            if (st.isPresent()) {
                NbtCompound s = st.get();
                s.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, newId);
                nbt.put(ModConstants.STORED_MOUNT_KEY, s);
            }
        } catch (Throwable ignored) {}
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }
}
