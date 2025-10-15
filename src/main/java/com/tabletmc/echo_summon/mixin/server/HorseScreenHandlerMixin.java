package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.MountSaddleMountImpl;
import com.tabletmc.echo_summon.impl.ServerPlayerEntityImpl;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.util.NbtUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HorseScreenHandler.class)
public abstract class HorseScreenHandlerMixin {
    @Shadow @Final private AbstractHorseEntity entity;
    @Shadow @Final private Inventory inventory;

    /**
     * Injected method that is called when the horse screen handler is closed.
     * This method updates Echo Saddle state and stores the mount when appropriate.
     *
     * @param player the player who closed the horse screen handler
     * @param ci     the callback info
     */
    @Inject(method = "onClosed", at = @At("HEAD"))
    public void transferSlot(PlayerEntity player, CallbackInfo ci) {
        var handler = (HorseScreenHandler) (Object) this;

        for (Slot slot : handler.slots) {
            if (slot == null || slot.inventory != this.inventory) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack.isOf(ModItems.MOUNT_SADDLE)) {
                slot.setStack(ItemStack.EMPTY);
            }
        }

        String mountId = this.entity.getUuidAsString();
        if (!mountId.isEmpty()) {
            ScreenHandler screenHandler = (ScreenHandler) (Object) this;
            ItemStack cursor = screenHandler.getCursorStack();
            if (echo_summon$isLinkedMountSaddle(cursor, mountId)) {
                screenHandler.setCursorStack(ItemStack.EMPTY);
            }

            var inventory = player.getInventory();
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack invStack = inventory.getStack(i);
                if (echo_summon$isLinkedMountSaddle(invStack, mountId)) {
                    inventory.setStack(i, ItemStack.EMPTY);
                }
            }
        }

        // Check if the entity is a horse
        if (entity instanceof HorseEntity horse) {
            // Update the horse's Mount Saddle state
            ((MountSaddleMountImpl) horse).updateMountSaddle();

            // Check if the player is a server player
            if (player instanceof ServerPlayerEntity serverPlayer) {
                // Check if the horse is the player's vehicle and has the Mount Saddle
                if (horse.equals(serverPlayer.getVehicle()) && ((MountSaddleMountImpl) horse).hasMountSaddle()) {
                    // Store the horse's data on the server player
                    ((ServerPlayerEntityImpl) serverPlayer).storeMount(horse);
                }
            }
        }
    }

    @Unique
    private static boolean echo_summon$isLinkedMountSaddle(ItemStack stack, String mountId) {
        if (stack == null || stack.isEmpty() || !stack.isOf(ModItems.MOUNT_SADDLE)) {
            return false;
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return false;
        }
        NbtCompound data = custom.copyNbt();
        String storedId = NbtUtils.getString(data, ModConstants.STORED_MOUNT_ID_KEY);
        return !storedId.isEmpty() && storedId.equals(mountId);
    }
}
