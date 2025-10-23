package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Shadow @Final public DefaultedList<Slot> slots;

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void echo_summon$preventRemovingMountSaddle(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        // Validate slot index first
        if (slotIndex < 0 || slotIndex >= this.slots.size()) return;

        Slot slot = this.slots.get(slotIndex);
        ItemStack stack = slot.getStack();
        if (stack.isEmpty() || !stack.isOf(ModItems.MOUNT_SADDLE)) return;

        // 1) Globally block SWAP (number keys 1-9 and offhand key) for mount_saddle from any container
        if (actionType == SlotActionType.SWAP) {
            ci.cancel();
            return;
        }

        // 2) For other actions, only enforce when a mount handler is open and the slot is not the player's inventory
        String handlerName = ((Object) this).getClass().getName();
        boolean isMountHandler = ((Object) this) instanceof HorseScreenHandler
                || handlerName.contains("CamelScreenHandler");
        if (!isMountHandler) return;
        if (player != null && player.isCreative()) return; // allow creative to bypass

        boolean isPlayerSlot = false;
        try {
            if (player != null && slot.inventory == player.getInventory()) {
                isPlayerSlot = true;
            }
        } catch (Throwable ignored) {}
        int playerAreaStart = Math.max(0, this.slots.size() - 36);
        if (slotIndex >= playerAreaStart) {
            isPlayerSlot = true;
        }
        if (isPlayerSlot) return;

        // Block common removal actions from mount inventory when the item is the mount saddle
        switch (actionType) {
            case PICKUP, QUICK_MOVE, THROW, PICKUP_ALL, QUICK_CRAFT, CLONE -> {
                ci.cancel();
            }
            default -> {}
        }
    }

    // Note: quickMove is enforced at Slot level via canTakeItems/takeStack guards.

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void echo_summon$blockInsertToPlayer(ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        // Only guard mount inventories (horse + camel via name)
        String handlerName = ((Object) this).getClass().getName();
        boolean isMountHandler = ((Object) this) instanceof HorseScreenHandler
                || handlerName.contains("CamelScreenHandler");
        if (!isMountHandler) return;

        if (stack == null || !stack.isOf(ModItems.MOUNT_SADDLE)) return;

        // Determine if the target range includes the player's inventory area (last 36 slots)
        int total = this.slots.size();
        int playerStart = Math.max(0, total - 36);
        boolean targetsPlayer = (startIndex >= playerStart) || (endIndex > playerStart);
        if (!targetsPlayer) return;

        // Block moving the bound saddle into the player's inventory via shift-click
        cir.setReturnValue(false);
        cir.cancel();
    }
}
