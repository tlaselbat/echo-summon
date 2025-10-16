package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.MountSaddleMountImpl;
import com.tabletmc.echo_summon.impl.SaddleableMountImpl;
import com.tabletmc.echo_summon.impl.ServerPlayerEntityImpl;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.item.custom.MountSaddleItem;
import com.tabletmc.echo_summon.util.NbtUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
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

        NbtCompound preservedSaddleData = null;
        String mountId = this.entity.getUuidAsString();

        for (Slot slot : handler.slots) {
            if (slot == null || slot.inventory != this.inventory) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !stack.isOf(ModItems.MOUNT_SADDLE)) {
                continue;
            }
            if (preservedSaddleData == null) {
                NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
                if (custom != null) {
                    preservedSaddleData = custom.copyNbt();
                }
            }
            slot.setStack(ItemStack.EMPTY);
        }

        if (!mountId.isEmpty()) {
            ScreenHandler screenHandler = (ScreenHandler) (Object) this;
            ItemStack cursor = screenHandler.getCursorStack();
            if (echo_summon$isLinkedMountSaddle(cursor, mountId)) {
                if (preservedSaddleData == null) {
                    NbtComponent custom = cursor.get(DataComponentTypes.CUSTOM_DATA);
                    if (custom != null) {
                        preservedSaddleData = custom.copyNbt();
                    }
                }
                screenHandler.setCursorStack(ItemStack.EMPTY);
            }

            var inventory = player.getInventory();
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack invStack = inventory.getStack(i);
                if (echo_summon$isLinkedMountSaddle(invStack, mountId)) {
                    if (preservedSaddleData == null) {
                        NbtComponent custom = invStack.get(DataComponentTypes.CUSTOM_DATA);
                        if (custom != null) {
                            preservedSaddleData = custom.copyNbt();
                        }
                    }
                    inventory.setStack(i, ItemStack.EMPTY);
                }
            }
        }

        EquipmentSlot saddleSlot = MountSaddleItem.resolveSlot(this.entity.getType());
        if (saddleSlot != null) {
            ItemStack equipped = this.entity.getEquippedStack(saddleSlot);
            if (!equipped.isOf(ModItems.MOUNT_SADDLE)) {
                ItemStack saddle = new ItemStack(ModItems.MOUNT_SADDLE);
                MountSaddleItem.applyEquippable(saddle, this.entity.getType());

                NbtCompound data = preservedSaddleData != null ? preservedSaddleData : echo_summon$buildDefaultSaddleData(this.entity);
                if (data != null) {
                    if (!data.contains("mount_type")) {
                        var typeId = EntityType.getId(this.entity.getType());
                        if (typeId != null) {
                            data.putString("mount_type", typeId.toString());
                        }
                    }
                    saddle.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
                }

                this.entity.equipStack(saddleSlot, saddle);
            } else if (preservedSaddleData != null) {
                ItemStack refreshed = equipped.copy();
                refreshed.setCount(1);
                if (!preservedSaddleData.contains("mount_type")) {
                    var typeId = EntityType.getId(this.entity.getType());
                    if (typeId != null) {
                        preservedSaddleData.putString("mount_type", typeId.toString());
                    }
                }
                refreshed.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(preservedSaddleData));
                this.entity.equipStack(saddleSlot, refreshed);
            }

            if (saddleSlot == EquipmentSlot.SADDLE && this.entity instanceof SaddleableMountImpl saddleable) {
                saddleable.echo_summon$setSaddled(true);
            }
        }

        if (entity instanceof HorseEntity horse) {
            ((MountSaddleMountImpl) horse).updateMountSaddle();

            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (horse.equals(serverPlayer.getVehicle()) && ((MountSaddleMountImpl) horse).hasMountSaddle()) {
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

    @Unique
    private static NbtCompound echo_summon$buildDefaultSaddleData(AbstractHorseEntity entity) {
        try {
            NbtCompound data = new NbtCompound();
            data.putString(ModConstants.STORED_MOUNT_ID_KEY, entity.getUuidAsString());
            var typeId = EntityType.getId(entity.getType());
            if (typeId != null) {
                data.putString("mount_type", typeId.toString());
            }
            return data;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
