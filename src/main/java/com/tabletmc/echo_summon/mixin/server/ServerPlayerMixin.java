package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.MountSaddleMountImpl;
import com.tabletmc.echo_summon.impl.ServerPlayerEntityImpl;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.net.ServerNetworking;
import com.tabletmc.echo_summon.util.NbtUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerMixin implements ServerPlayerEntityImpl {

    @Shadow public abstract void sendMessage(Text message, boolean actionBar);

    @Unique private AnimalEntity storedHorse;
    @Unique private boolean tpWasRiding = false;
    @Unique private Entity tpLastVehicle = null;

    @Override
    public void summonMount(boolean mountPlayer) {
        if (storedHorse == null) {
            sendMessage(Text.of("No Horse Found!"), true);
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        storedHorse.fallDistance = player.fallDistance;
        storedHorse.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());

        if (mountPlayer) {
            player.startRiding(storedHorse, true);
        }

        storedHorse.setVelocity(player.getVelocity());
        player.getWorld().spawnEntity(storedHorse);

        if (!mountPlayer) {
            storedHorse.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, false, false));
        }
    }

    @Override
    public void dismountHorse(boolean mountPlayer) {
        if (storedHorse == null) {
            return; // No horse to dismount
        }
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Remove the horse from the player's riding entity
        if (player.getRootVehicle() != null) {
            player.stopRiding();
            player.getRootVehicle().dismountVehicle();
        }
    }

    @Override
    public void storeMount(AnimalEntity mount) {
        // Do not auto-summon or replace the previously stored horse.
        // Simply update the stored reference to the current mount.
        if (mount.getRemovalReason() != null) {
            storedHorse = null;
        } else {
            storedHorse = mount;
        }
    }

    // Removed: auto-store on startRiding. Mounts should only be stored when right-clicked with an empty saddle_summon_tool.

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void echo_summon$preventSaddleDrop(boolean dropEntireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ItemStack selected = player.getMainHandStack();
        if (echo_summon$isMountSaddle(selected)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void echo_summon$preventSaddleDrop(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (echo_summon$isMountSaddle(stack)) {
            cir.setReturnValue(null);
            cir.cancel();
        }
    }

    // stopRiding is now handled in EntityMixin to avoid descriptor issues on ServerPlayerEntity.

    // Replace stopRiding hook with a stable tick-based transition detector to avoid recursion
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void echo_summon$afterTick(CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        boolean nowRiding = self.hasVehicle();
        Entity currentVehicle = nowRiding ? self.getVehicle() : null;

        if (nowRiding) {
            // Vehicle changed while still riding: dismiss previous saddle summon if present
            if (tpLastVehicle instanceof LivingEntity previousLiving && previousLiving != currentVehicle) {
                // Persist current state back into the summon tool before dismissing
                echo_summon$persistSaddleSummonedMount(self, previousLiving);
                ServerNetworking.handleSaddleAutoDismiss(self, previousLiving);
            }
            tpLastVehicle = currentVehicle;
        } else if (tpWasRiding) {
            // Fully dismounted from vehicle: dismiss prior saddle summon if present
            if (tpLastVehicle instanceof LivingEntity living) {
                // Persist current state back into the summon tool before dismissing
                echo_summon$persistSaddleSummonedMount(self, living);
                ServerNetworking.handleSaddleAutoDismiss(self, living);
            }
            tpLastVehicle = null;
        }

        tpWasRiding = nowRiding;
    }

    @Unique
    private static boolean echo_summon$isMountSaddle(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(ModItems.MOUNT_SADDLE)) {
            return false;
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return false;
        }
        NbtCompound data = custom.copyNbt();
        String storedId = NbtUtils.getString(data, ModConstants.STORED_MOUNT_ID_KEY);
        return !storedId.isEmpty();
    }

    @Unique
    private static void echo_summon$persistSaddleSummonedMount(ServerPlayerEntity player, LivingEntity living) {
        if (player == null || living == null) return;
        // Only handle mounts that were summoned by the saddle tool
        if (!living.getCommandTags().contains(ModConstants.SUMMON_TAG)) return;

        String toolId = echo_summon$extractSaddleToolId(living);
        if (toolId.isEmpty()) return;
        ItemStack summonTool = echo_summon$findSaddleSummonToolById(player, toolId);
        if (summonTool.isEmpty()) return;

        NbtComponent toolCustom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound toolNbt = toolCustom != null ? toolCustom.copyNbt() : new NbtCompound();

        // Serialize full entity NBT (captures donkey/mule chest inventory)
        NbtCompound stored = net.minecraft.predicate.NbtPredicate.entityToNbt(living);
        var typeId = EntityType.getId(living.getType());
        if (typeId != null) {
            stored.putString("id", typeId.toString());
        }
        stored.putString(ModConstants.STORED_MOUNT_ID_KEY, living.getUuidAsString());
        stored.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, toolId);
        toolNbt.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, toolId);

        // Persist mount saddle data explicitly from the saddle slot
        ItemStack saddleEq = living.getEquippedStack(EquipmentSlot.SADDLE);
        if (!saddleEq.isEmpty() && saddleEq.isOf(ModItems.MOUNT_SADDLE)) {
            NbtComponent saddleData = saddleEq.get(DataComponentTypes.CUSTOM_DATA);
            if (saddleData != null) {
                NbtCompound saddleNbt = saddleData.copyNbt();
                if (!saddleNbt.contains("mount_type")) {
                    saddleNbt.putString("mount_type", EntityType.getId(living.getType()).toString());
                }
                stored.put("mount_saddle_data", saddleNbt);
            }
        }

        toolNbt.put(ModConstants.STORED_MOUNT_KEY, stored);
        summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(toolNbt));

        // Update model override to match mount type
        try {
            String idStr = typeId != null ? typeId.toString() : "";
            String modelKey = echo_summon$mapEntityTypeToModelKey(idStr);
            if (!modelKey.isEmpty()) {
                summonTool.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(), List.of(), List.of(modelKey), List.of()));
            } else {
                summonTool.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
            }
        } catch (Throwable ignored) {}
    }

    @Unique
    private static String echo_summon$extractSaddleToolId(LivingEntity living) {
        String prefix = ModConstants.MOD_ID + ":saddle_tool:";
        for (String tag : living.getCommandTags()) {
            if (tag.startsWith(prefix)) {
                return tag.substring(prefix.length());
            }
        }
        return "";
    }

    @Unique
    private static ItemStack echo_summon$findSaddleSummonToolById(ServerPlayerEntity player, String toolId) {
        if (toolId == null || toolId.isEmpty()) return ItemStack.EMPTY;
        ItemStack main = player.getMainHandStack();
        if (echo_summon$isMatchingSaddleSummonTool(main, toolId)) return main;
        ItemStack off = player.getOffHandStack();
        if (echo_summon$isMatchingSaddleSummonTool(off, toolId)) return off;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (echo_summon$isMatchingSaddleSummonTool(s, toolId)) return s;
        }
        return ItemStack.EMPTY;
    }

    @Unique
    private static boolean echo_summon$isMatchingSaddleSummonTool(ItemStack stack, String toolId) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof com.tabletmc.echo_summon.item.custom.SaddleSummonToolItem)) return false;
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return false;
        NbtCompound comp = custom.copyNbt();
        String id = NbtUtils.getString(comp, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
        return toolId.equals(id);
    }

    @Unique
    private static String echo_summon$mapEntityTypeToModelKey(String id) {
        if (id == null || id.isEmpty()) return "";
        return switch (id) {
            case "minecraft:horse" -> "echo_summon:horse";
            case "minecraft:donkey" -> "echo_summon:donkey";
            case "minecraft:mule" -> "echo_summon:mule";
            case "minecraft:camel" -> "echo_summon:camel";
            case "minecraft:skeleton_horse" -> "echo_summon:skeleton_horse";
            case "minecraft:zombie_horse" -> "echo_summon:zombie_horse";
            default -> "";
        };
    }
}
