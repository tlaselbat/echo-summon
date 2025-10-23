package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.ServerPlayerEntityImpl;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.net.ServerNetworking;
import com.tabletmc.echo_summon.util.NbtUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerMixin implements ServerPlayerEntityImpl {

    @Unique private AnimalEntity storedHorse;
    @Unique private boolean tpWasRiding = false;
    @Unique private Entity tpLastVehicle = null;

    @Override
    public void echoSummon$summonMount(boolean mountPlayer) {
        if (storedHorse == null) {
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
    public void echoSummon$dismountHorse(boolean mountPlayer) {
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
    public void echoSummon$storeMount(AnimalEntity mount) {
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
                // Skip auto-dismiss for Happy Ghast to allow it to stay in world after dismount
                net.minecraft.util.Identifier prevId = net.minecraft.entity.EntityType.getId(previousLiving.getType());
                boolean prevIsHappyGhast = prevId != null && "happy_ghast".equals(prevId.getPath());
                if (!prevIsHappyGhast) {
                    ServerNetworking.handleSaddleAutoDismiss(self, previousLiving);
                }
            }
            tpLastVehicle = currentVehicle;
        } else if (tpWasRiding) {
            // Fully dismounted from vehicle: dismiss prior saddle summon if present
            if (tpLastVehicle instanceof LivingEntity living) {
                // Persist current state back into the summon tool before dismissing
                echo_summon$persistSaddleSummonedMount(self, living);
                // Skip auto-dismiss for Happy Ghast to allow it to stay in world after dismount
                net.minecraft.util.Identifier id = net.minecraft.entity.EntityType.getId(living.getType());
                boolean isHappyGhast = id != null && "happy_ghast".equals(id.getPath());
                if (!isHappyGhast) {
                    ServerNetworking.handleSaddleAutoDismiss(self, living);
                }
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
        // If Happy Ghast, bypass full SummonPersistence and only write minimal remote state
        try {
            net.minecraft.util.Identifier typeId = net.minecraft.entity.EntityType.getId(living.getType());
            boolean isHappyGhast = typeId != null && "happy_ghast".equals(typeId.getPath());
            if (isHappyGhast) {
                var cd = summonTool.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                var nbt = cd != null ? cd.copyNbt() : new net.minecraft.nbt.NbtCompound();
                java.util.Optional<net.minecraft.nbt.NbtCompound> st = nbt.getCompound(ModConstants.STORED_MOUNT_KEY);
                net.minecraft.nbt.NbtCompound stored = st.orElse(new net.minecraft.nbt.NbtCompound());
                // Ensure id + uuid
                if (typeId != null) stored.putString("id", typeId.toString());
                stored.putString(ModConstants.STORED_MOUNT_ID_KEY, living.getUuidAsString());
                // Record dim and position
                try {
                    String dimId = living.getWorld().getRegistryKey().getValue().toString();
                    stored.putString(ModConstants.STORED_DIM_KEY, dimId);
                    net.minecraft.util.math.BlockPos bp = living.getBlockPos();
                    net.minecraft.nbt.NbtCompound pos = new net.minecraft.nbt.NbtCompound();
                    pos.putInt("x", bp.getX());
                    pos.putInt("y", bp.getY());
                    pos.putInt("z", bp.getZ());
                    stored.put(ModConstants.STORED_POS_KEY, pos);
                } catch (Throwable ignored) {}
                // Mark remote active and keep tool id consistent/sanitized
                stored.putBoolean(ModConstants.REMOTE_ACTIVE_KEY, true);
                stored.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, toolId);
                nbt.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, toolId);
                nbt.put(ModConstants.STORED_MOUNT_KEY, stored);
                summonTool.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt));
                return;
            }
        } catch (Throwable ignored) {}
        // Delegate to service to ensure consistent persistence (non-Happy Ghast)
        try {
            com.tabletmc.echo_summon.net.service.SummonPersistence.persistSaddleSummonedMountToTool(player, summonTool, living, toolId, true);
        } catch (Throwable ignored) {}
    }

    @Unique
    private static String echo_summon$extractSaddleToolId(LivingEntity living) {
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

    
}
