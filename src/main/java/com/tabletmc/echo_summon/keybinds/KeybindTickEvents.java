package com.tabletmc.echo_summon.keybinds;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.client.ClientCooldowns;
import com.tabletmc.echo_summon.item.custom.SaddleSummonToolItem;
import com.tabletmc.echo_summon.net.payload.StringPayload;
import com.tabletmc.echo_summon.net.service.SummonPersistence;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * Registers client-side tick events for the mod.
 * Previous handled the logic for the summon horse keybind.
 */
public class KeybindTickEvents {

    private static boolean previousSneakPressed = false;
    private static LivingEntity pendingHide = null;
    private static int pendingHideTicks = 0;
    private static boolean pendingHidePrevInvisible = false;
    // Ensures immediate client-side blocking even before cooldown state syncs
    private static int sneakLockTicks = 0;
    private static boolean wasCooling = false;

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(KeybindTickEvents::onEndTick);
        ClientTickEvents.END_CLIENT_TICK.register(KeybindTickEvents::onEndTick);
    }

    public static void lockSneakFor(int ticks) {
        if (ticks > sneakLockTicks) sneakLockTicks = ticks;
    }

    // Helper: find active tool by matching riding entity UUID with the tool's stored mount UUID
    private static ItemStack findActiveFlaggedToolMatchingRiding(ClientPlayerEntity player, LivingEntity riding) {
        String targetId = riding.getUuidAsString();
        ItemStack main = player.getMainHandStack();
        if (main.getItem() instanceof SaddleSummonToolItem && SummonPersistence.hasStoredMount(main)) {
            NbtCompound stored = SummonPersistence.getStoredMount(main);
            if (stored != null) {
                String storedId = com.tabletmc.echo_summon.util.NbtUtils.getString(stored, ModConstants.STORED_MOUNT_ID_KEY);
                if (!storedId.isEmpty() && storedId.equals(targetId)) return main;
            }
        }
        ItemStack off = player.getOffHandStack();
        if (off.getItem() instanceof SaddleSummonToolItem && SummonPersistence.hasStoredMount(off)) {
            NbtCompound stored = SummonPersistence.getStoredMount(off);
            if (stored != null) {
                String storedId = com.tabletmc.echo_summon.util.NbtUtils.getString(stored, ModConstants.STORED_MOUNT_ID_KEY);
                if (!storedId.isEmpty() && storedId.equals(targetId)) return off;
            }
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() instanceof SaddleSummonToolItem && SummonPersistence.hasStoredMount(s)) {
                NbtCompound stored = SummonPersistence.getStoredMount(s);
                if (stored != null) {
                    String storedId = com.tabletmc.echo_summon.util.NbtUtils.getString(stored, ModConstants.STORED_MOUNT_ID_KEY);
                    if (!storedId.isEmpty() && storedId.equals(targetId)) return s;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Called at the end of each client tick.
     * Keybind removed; this is now a no-op.
     */
    private static void onEndTick(MinecraftClient client) {
        if (client == null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player == null || player.isSpectator()) {
            previousSneakPressed = client.options.sneakKey.isPressed();
            return;
        }

        // Handle cosmetic unhide countdown for previously hidden entity
        if (pendingHide != null) {
            try {
                if (pendingHide.isRemoved() || pendingHideTicks <= 0) {
                    if (!pendingHidePrevInvisible) {
                        pendingHide.setInvisible(false);
                    }
                    pendingHide = null;
                    pendingHideTicks = 0;
                    pendingHidePrevInvisible = false;
                } else {
                    pendingHideTicks--;
                }
            } catch (Throwable ignored) {
                pendingHide = null;
                pendingHideTicks = 0;
                pendingHidePrevInvisible = false;
            }
        }

        // Detect cooldown rising edge and set a local hard lock window
        boolean coolingNow = isAnySummonToolCooling(player);
        if (coolingNow && !wasCooling) {
            try { KeybindTickEvents.lockSneakFor(ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored) {}
        }
        wasCooling = coolingNow;

        boolean sneakPressed = client.options.sneakKey.isPressed();
        // Hard lock window (e.g., right after client sends summon/dismiss)
        if (sneakLockTicks > 0) {
            sneakLockTicks--;
            cancelSneakPress(client, player);
            previousSneakPressed = false;
            return;
        }
        // Continuous gating: while any summon tool is cooling, cancel every tick (covers pressed and toggle sneak)
        if (coolingNow) {
            cancelSneakPress(client, player);
            previousSneakPressed = false;
            return;
        }
        boolean sneakJustPressed = sneakPressed && !previousSneakPressed;
        previousSneakPressed = sneakPressed;

        if (!sneakJustPressed) {
            return;
        }

        if (!player.hasVehicle()) {
            return;
        }

        // Mounted on something; prefer to use the riding entity if available
        if (!(player.getVehicle() instanceof LivingEntity riding)) {
            return;
        }

        // Skip client-side invisibility and dismiss for Happy Ghast
        try {
            var typeId = net.minecraft.entity.EntityType.getId(riding.getType());
            if (typeId != null && "happy_ghast".equals(typeId.getPath())) {
                return; // allow vanilla dismount; no client-side dismissal or invisibility
            }
        } catch (Throwable ignored) {}

        // Only apply instant dismiss/invisibility for saddle-summoned mounts
        // Prefer per-tool tag on the entity; if absent (client not synced), fall back to matching the riding UUID against the tool's stored mount UUID
        String toolId = extractToolIdFromTags(riding);
        ItemStack summonTool = ItemStack.EMPTY;
        if (!toolId.isEmpty()) {
            summonTool = findSaddleSummonToolById(player, toolId);
        } else {
            summonTool = findActiveFlaggedToolMatchingRiding(player, riding);
        }
        if (summonTool.isEmpty()) {
            // Not a saddle-summoned mount; allow vanilla dismount without visuals
            return;
        }
        if (player.getItemCooldownManager().isCoolingDown(summonTool)) {
            cancelSneakPress(client, player);
            return;
        }
        if (!SummonPersistence.hasStoredMount(summonTool)) {
            return;
        }

        // Send dismiss immediately and apply cooldown to all summon tool stacks (visual global cooldown)
        ClientPlayNetworking.send(new StringPayload("saddle_dismiss"));
        // Cosmetic: hide the entity for a few ticks to avoid visual linger until server removes it
        try {
            pendingHide = riding;
            pendingHidePrevInvisible = riding.isInvisible();
            pendingHideTicks = 8; // ~0.4s at 20 TPS; usually server removes sooner
            riding.setInvisible(true);
        } catch (Throwable ignored) {}
        // Client-side dismount to reduce perceived delay; server will confirm removal
        try { player.stopRiding(); } catch (Throwable ignored) {}
        ClientCooldowns.applyCooldownToAllSummonTools(player, ModConstants.SUMMON_COOLDOWN_TICKS);
        // Lock sneak locally for the cooldown window to guarantee immediate blocking
        try { KeybindTickEvents.lockSneakFor(ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored) {}
    }

    private static ItemStack findSaddleSummonToolById(ClientPlayerEntity player, String toolId) {
        if (toolId == null || toolId.isEmpty()) return ItemStack.EMPTY;
        ItemStack main = player.getMainHandStack();
        if (isMatchingSaddleSummonTool(main, toolId)) return main;
        ItemStack off = player.getOffHandStack();
        if (isMatchingSaddleSummonTool(off, toolId)) return off;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (isMatchingSaddleSummonTool(s, toolId)) return s;
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasSummonFlag(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return false;
        }
        try {
            NbtCompound nbt = custom.copyNbt();
            return nbt.getBoolean(ModConstants.MOUNT_SUMMONED_TOOL_FLAG).orElse(false);
        } catch (Throwable ignored) {
            return false;
        }
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

    private static boolean isMatchingSaddleSummonTool(ItemStack stack, String toolId) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof SaddleSummonToolItem)) return false;
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return false;
        String id;
        try {
            id = com.tabletmc.echo_summon.util.NbtUtils.getString(custom.copyNbt(), ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
        } catch (Throwable ignored) {
            id = "";
        }
        return toolId.equals(id);
    }

    // Helper: find the active saddle summon tool by the tool-side summon flag when entity tags are not available on the client
    private static ItemStack findActiveFlaggedSaddleSummonTool(ClientPlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (main.getItem() instanceof SaddleSummonToolItem && hasSummonFlag(main) && SummonPersistence.hasStoredMount(main)) return main;
        ItemStack off = player.getOffHandStack();
        if (off.getItem() instanceof SaddleSummonToolItem && hasSummonFlag(off) && SummonPersistence.hasStoredMount(off)) return off;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() instanceof SaddleSummonToolItem && hasSummonFlag(s) && SummonPersistence.hasStoredMount(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    // Helper: whether any saddle summon tool stack is currently cooling down (share cooldown for sneak)
    private static boolean isAnySummonToolCooling(ClientPlayerEntity player) {
        try {
            var inv = player.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s.getItem() instanceof SaddleSummonToolItem && player.getItemCooldownManager().isCoolingDown(s)) return true;
            }
            ItemStack off = player.getOffHandStack();
            if (off.getItem() instanceof SaddleSummonToolItem && player.getItemCooldownManager().isCoolingDown(off)) return true;
            ItemStack main = player.getMainHandStack();
            if (main.getItem() instanceof SaddleSummonToolItem && player.getItemCooldownManager().isCoolingDown(main)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    // Helper: cancel the sneak keypress to prevent vanilla dismount during cooldown
    private static void cancelSneakPress(MinecraftClient client, ClientPlayerEntity player) {
        try {
            client.options.sneakKey.setPressed(false);
            player.setSneaking(false);
        } catch (Throwable ignored) {}
    }
}