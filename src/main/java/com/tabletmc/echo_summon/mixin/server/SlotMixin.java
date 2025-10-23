package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow public abstract ItemStack getStack();
    @Shadow @Final public Inventory inventory;

    @Inject(method = "canTakeItems", at = @At("HEAD"), cancellable = true)
    private void echo_summon$bindMountSaddle(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && player.isCreative()) return; // creative bypass
        if (player == null) return;
        String handlerName = player.currentScreenHandler != null ? player.currentScreenHandler.getClass().getName() : "";
        boolean isMountHandler = handlerName.contains("HorseScreenHandler")
                || handlerName.contains("CamelScreenHandler");
        if (!isMountHandler) return;

        ItemStack stack = this.getStack();
        if (stack.isEmpty() || !stack.isOf(ModItems.MOUNT_SADDLE)) return;

        // Only block when the slot is not the player's own inventory
        try {
            if (this.inventory == player.getInventory()) return;
        } catch (Throwable ignored) {}

        // Prevent taking the bound saddle from the mount inventory
        cir.setReturnValue(false);
    }

    @Inject(method = "takeStack", at = @At("HEAD"), cancellable = true)
    private void echo_summon$blockTakeStack(int amount, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = this.getStack();
        if (stack.isEmpty() || !stack.isOf(ModItems.MOUNT_SADDLE)) return;
        String invName = this.inventory != null ? this.inventory.getClass().getName() : "";
        if (invName.contains("PlayerInventory")) return;
        cir.setReturnValue(ItemStack.EMPTY);
        cir.cancel();
    }

    // Note: Slot does not define removeStack in this mapping; guards above are sufficient.
}
