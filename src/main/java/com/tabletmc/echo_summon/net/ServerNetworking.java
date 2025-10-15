package com.tabletmc.echo_summon.net;

import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.impl.HarnessableMountImpl;
import com.tabletmc.echo_summon.impl.SaddleableMountImpl;
import com.tabletmc.echo_summon.item.custom.MountHarnessItem;
import com.tabletmc.echo_summon.item.custom.MountSaddleItem;
import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.net.payload.StringPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.function.Function;

/**
 * Handles server-side networking for the mod.
 */
public class ServerNetworking {

    private static final TagKey<EntityType<?>> ALLOWED_MOUNTS_TAG = TagKey.of(RegistryKeys.ENTITY_TYPE, ModConstants.Id(ModConstants.ALLOWED_MOUNTS_TAG_PATH));
    private static final TagKey<EntityType<?>> HARNESS_ALLOWED_MOUNTS_TAG = TagKey.of(RegistryKeys.ENTITY_TYPE, ModConstants.Id(ModConstants.HARNESS_ALLOWED_MOUNTS_TAG_PATH));
    private static final String SADDLE_TOOL_TAG_PREFIX = ModConstants.MOD_ID + ":saddle_tool:";

    public static void init() {
        PayloadTypeRegistry.playC2S().register(StringPayload.PACKET_ID, StringPayload.PACKET_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(StringPayload.PACKET_ID, (StringPayload handler, ServerPlayNetworking.Context context) -> {
            var player = context.player();
            String action = handler.stringPayload();

            boolean harnessAction = action.startsWith("harness_");
            ItemStack summonTool = findSummonToolInHand(player, harnessAction);
            if (summonTool.isEmpty()) {
                player.sendMessage(Text.literal(harnessAction ? "No harness summon tool in hand" : "No saddle summon tool in hand"));
                return;
            }
            if (action.startsWith("harness_capture:")) {
                handleHarnessCapture(player, summonTool, action);
                return;
            }
            if ("harness_toggle".equals(action)) {
                Entity vehicle = player.getVehicle();
                if (vehicle instanceof LivingEntity living && living.getCommandTags().contains(ModConstants.HARNESS_SUMMON_TAG)) {
                    // Riding a harness-summoned mount → dismiss it
                    dismissHarnessMount(player, summonTool, living);
                } else {
                    // Not riding: if a matching summoned mount exists nearby, dismiss; otherwise summon
                    LivingEntity found = findHarnessSummonedMount(player, summonTool);
                    if (found != null) {
                        dismissHarnessMount(player, summonTool, found);
                    } else {
                        handleHarnessSummon(player, summonTool);
                    }
                }
                return;
            }
            if ("harness_release_or_dismiss".equals(action)) {
                Entity vehicle = player.getVehicle();
                if (vehicle instanceof LivingEntity living && living.getCommandTags().contains(ModConstants.HARNESS_SUMMON_TAG)) {
                    dismissHarnessMount(player, summonTool, living);
                    return;
                }
                LivingEntity found = findHarnessSummonedMount(player, summonTool);
                if (found != null) {
                    dismissHarnessMount(player, summonTool, found);
                    return;
                }
                handleHarnessRelease(player, summonTool);
                return;
            }
            if ("harness_dismiss".equals(action)) {
                handleHarnessDismiss(player, summonTool);
                return;
            }
            if ("harness_summon".equals(action)) {
                handleHarnessSummon(player, summonTool);
                return;
            }
            if ("harness_release".equals(action)) {
                handleHarnessRelease(player, summonTool);
                return;
            }
            if (action.startsWith("saddle_capture:")) {
                if (hasStoredMount(summonTool)) {
                    player.sendMessage(Text.literal("This summon tool already contains a mount."));
                    return;
                }
                String uuidStr = action.substring("saddle_capture:".length());
                java.util.UUID uuid;
                try {
                    uuid = java.util.UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(Text.literal("Invalid target"));
                    return;
                }
                ServerWorld world = (ServerWorld) player.getWorld();
                Entity target = world.getEntity(uuid);
                if (!(target instanceof LivingEntity living)) {
                    player.sendMessage(Text.literal("Target is not valid"));
                    return;
                }
                if (!living.getType().isIn(ALLOWED_MOUNTS_TAG)) {
                    player.sendMessage(Text.literal("That entity cannot be stored"));
                    return;
                }
                if (living.getCommandTags().contains(ModConstants.SUMMON_TAG)) {
                    player.sendMessage(Text.literal("Cannot store a summoned mount"));
                    return;
                }
                // If it's a horse-like mount, mark it tamed by the capturing player so vanilla rendering/behavior applies
                if (living instanceof AbstractHorseEntity ahe) {
                    try {
                        ahe.setTame(true);
                    } catch (Throwable ignored) {}
                }

                // Persist mount to the summon tool's CUSTOM_DATA under our key
                NbtCompound mountNbt = net.minecraft.predicate.NbtPredicate.entityToNbt(living);
                mountNbt.putString("id", EntityType.getId(living.getType()).toString());
                // Persist identifiers for mapping
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
                NbtComponent custom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
                NbtCompound comp = custom != null ? custom.copyNbt() : new NbtCompound();
                // Ensure summon tool ID persists; migrate from legacy if present
                String toolId = com.tabletmc.echo_summon.util.NbtUtils.getString(comp, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
                if (toolId.isEmpty()) toolId = java.util.UUID.randomUUID().toString();
                comp.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, toolId);
                mountNbt.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, toolId);
                comp.put(ModConstants.STORED_MOUNT_KEY, mountNbt);
                summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(comp));

                // Set CustomModelData on the summon tool to reflect stored mount (for item model overrides)
                try {
                    String modelKey = mapEntityTypeToModelKey(EntityType.getId(living.getType()).toString());
                    if (modelKey != null && !modelKey.isEmpty()) {
                        summonTool.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(), List.of(), List.of(modelKey), List.of()));
                    } else {
                        summonTool.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
                    }
                } catch (Throwable ignored) {}
                
                // Create mount saddle with mapping data and equip it to the mount
                ItemStack mountSaddle = new ItemStack(ModItems.MOUNT_SADDLE);
                MountSaddleItem.applyEquippable(mountSaddle, living.getType());
                NbtCompound mountSaddleData = new NbtCompound();
                mountSaddleData.putString(ModConstants.STORED_MOUNT_ID_KEY, living.getUuidAsString());
                // Store the mount entity type for item-entity model selection
                mountSaddleData.putString("mount_type", EntityType.getId(living.getType()).toString());
                {
                    String wid = com.tabletmc.echo_summon.util.NbtUtils.getString(comp, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
                    if (!wid.isEmpty()) {
                        mountSaddleData.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, wid);
                    }
                }
                mountSaddle.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(mountSaddleData));

                EquipmentSlot slot = MountSaddleItem.resolveSlot(living.getType());
                if (slot != null && living.canEquip(mountSaddle, slot)) {
                    living.equipStack(slot, mountSaddle);
                    if (slot == EquipmentSlot.SADDLE) {
                        setSaddled(living, true);
                    }
                }
                
                // Store mount saddle data in mount NBT for persistence
                mountNbt.put("mount_saddle_data", mountSaddleData);

                // Re-capture entity NBT after equipping to persist the saddle in stored data
                NbtCompound mountNbtUpdated = net.minecraft.predicate.NbtPredicate.entityToNbt(living);
                mountNbtUpdated.putString("id", EntityType.getId(living.getType()).toString());
                mountNbtUpdated.putString(ModConstants.STORED_MOUNT_ID_KEY, living.getUuidAsString());
                try {
                    String ownerName2 = player.getGameProfile().getName();
                    if (ownerName2 != null && !ownerName2.isEmpty()) {
                        mountNbtUpdated.putString("owner_name", ownerName2);
                    }
                } catch (Throwable ignored) {}
                if (living.getMaxHealth() > 0f) {
                    mountNbtUpdated.putFloat("Health", living.getMaxHealth());
                }
                {
                    String wid2 = com.tabletmc.echo_summon.util.NbtUtils.getString(comp, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
                    if (!wid2.isEmpty()) {
                        mountNbtUpdated.putString(ModConstants.SADDLE_SUMMON_TOOL_ID_KEY, wid2);
                    }
                }
                mountNbtUpdated.put("mount_saddle_data", mountSaddleData);
                comp.put(ModConstants.STORED_MOUNT_KEY, mountNbtUpdated);
                summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(comp));
                // Ensure CustomModelData remains set after updating
                try {
                    String modelKey2 = mapEntityTypeToModelKey(EntityType.getId(living.getType()).toString());
                    if (modelKey2 != null && !modelKey2.isEmpty()) {
                        summonTool.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(), List.of(), List.of(modelKey2), List.of()));
                    } else {
                        summonTool.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
                    }
                } catch (Throwable ignored) {}
                
                // Remove the original entity
                living.remove(Entity.RemovalReason.DISCARDED);
                ((com.tabletmc.echo_summon.impl.EntityMixinImpl) living).undoRemove();
                // Apply cooldown
                player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS);
                player.sendMessage(Text.literal("Mount stored in summon tool"));
                return;
            }
            if ("saddle_dismiss".equals(action)) {
                Entity vehicle = player.getVehicle();
                if (vehicle instanceof LivingEntity living) {
                    if (living.getCommandTags().contains(ModConstants.SUMMON_TAG)) {
                        String toolId = getSaddleSummonToolId(summonTool);
                        dismissSaddleSummonedMount(player, summonTool, living, toolId, true, false, Text.literal("Mount dismissed"));
                    } else {
                        player.sendMessage(Text.literal("No summonable mount to dismiss"));
                    }
                } else {
                    player.sendMessage(Text.literal("You're not riding a mount"));
                }
                return;
            }
            if ("saddle_summon".equals(action)) {
                if (player.hasVehicle()) {
                    player.sendMessage(Text.literal("Already riding"));
                    return;
                }
                NbtCompound stored = getStoredMount(summonTool);
                if (stored == null || stored.isEmpty()) {
                    player.sendMessage(Text.literal("No mount stored in summon tool"));
                    return;
                }
                Entity loaded = EntityType.loadEntityWithPassengers(
                        stored,
                        player.getWorld(),
                        SpawnReason.LOAD,
                        Function.identity()
                );
                if (!(loaded instanceof LivingEntity mount) || !loaded.getType().isIn(ALLOWED_MOUNTS_TAG)) {
                    player.sendMessage(Text.literal("Stored entity is invalid"));
                    return;
                }
                // Ensure horses are tamed on summon to make saddle render and allow control
                if (mount instanceof AbstractHorseEntity ahe2) {
                    try {
                        ahe2.setTame(true);
                    } catch (Throwable ignored) {}
                }
                mount.fallDistance = player.fallDistance;
                mount.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
                mount.setVelocity(player.getVelocity());
                mount.addCommandTag(ModConstants.SUMMON_TAG);
                
                player.getWorld().spawnEntity(mount);

                NbtComponent toolCustom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
                NbtCompound toolNbt = toolCustom != null ? toolCustom.copyNbt() : new NbtCompound();
                String toolId = com.tabletmc.echo_summon.util.NbtUtils.getString(toolNbt, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);

                // Ensure mount saddle is equipped (apply immediately and re-apply next tick)
                stored.getCompound("mount_saddle_data").ifPresent(saddleData -> {
                    if (!saddleData.isEmpty()) {
                        // Auto-heal missing mount_type for legacy data
                        if (!saddleData.contains("mount_type")) {
                            saddleData.putString("mount_type", EntityType.getId(mount.getType()).toString());
                        }
                        ItemStack mountSaddleNow = new ItemStack(ModItems.MOUNT_SADDLE);
                        MountSaddleItem.applyEquippable(mountSaddleNow, mount.getType());
                        mountSaddleNow.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(saddleData));
                        EquipmentSlot slot = MountSaddleItem.resolveSlot(mount.getType());
                        if (slot != null && mount.canEquip(mountSaddleNow, slot)) {
                            mount.equipStack(slot, mountSaddleNow);
                            if (slot == EquipmentSlot.SADDLE) {
                                setSaddled(mount, true);
                            }
                        }
                        var server = player.getServer();
                        if (server != null) {
                            server.execute(() -> {
                                EquipmentSlot reapplySlot = MountSaddleItem.resolveSlot(mount.getType());
                                if (reapplySlot == null) {
                                    return;
                                }
                                ItemStack current = mount.getEquippedStack(reapplySlot);
                                if (!current.isOf(ModItems.MOUNT_SADDLE)) {
                                    ItemStack copy = mountSaddleNow.copy();
                                    MountSaddleItem.applyEquippable(copy, mount.getType());
                                    mount.equipStack(reapplySlot, copy);
                                }
                                if (reapplySlot == EquipmentSlot.SADDLE) {
                                    setSaddled(mount, true);
                                }
                            });
                        }
                    }
                });
                if (toolId != null && !toolId.isEmpty()) {
                    String toolTag = SADDLE_TOOL_TAG_PREFIX + toolId;
                    mount.addCommandTag(toolTag);
                }

                player.startRiding(mount, true);
                // Apply cooldown
                player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS);
                player.sendMessage(Text.literal("Mount summoned from summon tool"));
                return;
            }
            if ("saddle_release".equals(action)) {
                NbtComponent custom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
                NbtCompound comp = custom != null ? custom.copyNbt() : null;
                if (comp == null) {
                    player.sendMessage(Text.literal("No mount stored in summon tool"));
                    return;
                }
                java.util.Optional<NbtCompound> opt = comp.getCompound(ModConstants.STORED_MOUNT_KEY);
                if (opt.isEmpty() || opt.get().isEmpty()) {
                    player.sendMessage(Text.literal("No mount stored in summon tool"));
                    return;
                }
                NbtCompound stored = opt.get();
                Entity loaded = EntityType.loadEntityWithPassengers(
                        stored,
                        player.getWorld(),
                        SpawnReason.LOAD,
                        Function.identity()
                );
                if (!(loaded instanceof LivingEntity mount) || !loaded.getType().isIn(ALLOWED_MOUNTS_TAG)) {
                    player.sendMessage(Text.literal("Stored entity is invalid"));
                    return;
                }
                // Safer spawn: raycast to find valid position in front of player
                Vec3d look = player.getRotationVec(1.0F).normalize();
                Vec3d spawnPos = findSafeSpawnPosition(player, look, 3.0, 5.0);
                mount.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, player.getYaw(), player.getPitch());
                
                // Remove echo saddle from the mount's equipped slot and give to player
                EquipmentSlot slot = MountSaddleItem.resolveSlot(mount.getType());
                EquipmentSlot removalSlot = slot != null ? slot : EquipmentSlot.SADDLE;
                ItemStack saddleSlot = mount.getEquippedStack(removalSlot);
                if (saddleSlot.isOf(ModItems.MOUNT_SADDLE)) {
                    ItemStack copy = saddleSlot.copy();
                    NbtComponent echoCustom = copy.get(DataComponentTypes.CUSTOM_DATA);
                    NbtCompound echoComp = echoCustom != null ? echoCustom.copyNbt() : new NbtCompound();
                    if (!echoComp.contains("mount_type")) {
                        echoComp.putString("mount_type", EntityType.getId(mount.getType()).toString());
                    }
                    copy.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(echoComp));
                    player.giveItemStack(copy);
                    mount.equipStack(removalSlot, ItemStack.EMPTY);
                    if (removalSlot == EquipmentSlot.SADDLE) {
                        setSaddled(mount, false);
                    }
                } else {
                    // Fallback: create from stored mapping data if not equipped
                    stored.getCompound("mount_saddle_data").ifPresent(saddleData -> {
                        if (!saddleData.isEmpty()) {
                            if (!saddleData.contains("mount_type")) {
                                saddleData.putString("mount_type", EntityType.getId(mount.getType()).toString());
                            }
                            ItemStack mountSaddle = new ItemStack(ModItems.MOUNT_SADDLE);
                            MountSaddleItem.applyEquippable(mountSaddle, mount.getType());
                            mountSaddle.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(saddleData));
                            player.giveItemStack(mountSaddle);
                        }
                    });
                }
                
                player.getWorld().spawnEntity(mount);

                comp.remove(ModConstants.STORED_MOUNT_KEY);
                summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(comp));
                // Clear CustomModelData so summon tool reverts to default texture
                try {
                    summonTool.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
                } catch (Throwable ignored) {}
                
                // Auto-delete linked echo saddles when stored mount is cleared
                {
                    String toolId = com.tabletmc.echo_summon.util.NbtUtils.getString(comp, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
                    if (!toolId.isEmpty()) {
                        deleteLinkedMountSaddles(player, toolId);
                    }
                }
                
                // Apply cooldown
                player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS);
                player.sendMessage(Text.literal("Mount released from summon tool"));
                return;
            }
        });
    }

    private static boolean hasHarnessStoredMount(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return false;
        NbtCompound comp = custom.copyNbt();
        return comp.getCompound(ModConstants.STORED_MOUNT_KEY).map(n -> !n.isEmpty()).orElse(false);
    }

    private static NbtCompound getHarnessStoredMount(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return null;
        NbtCompound comp = custom.copyNbt();
        java.util.Optional<NbtCompound> opt = comp.getCompound(ModConstants.STORED_MOUNT_KEY);
        return opt.filter(n -> !n.isEmpty()).orElse(null);
    }

    private static NbtCompound getHarnessComponentData(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return null;
        NbtCompound comp = custom.copyNbt();
        java.util.Optional<NbtCompound> opt = comp.getCompound(ModConstants.MOUNT_HARNESS_DATA_KEY);
        return opt.filter(n -> !n.isEmpty()).orElse(null);
    }

    private static void handleHarnessCapture(net.minecraft.server.network.ServerPlayerEntity player, ItemStack summonTool, String action) {
        if (hasHarnessStoredMount(summonTool)) {
            player.sendMessage(Text.literal("This harness summon tool already contains a mount."));
            return;
        }
        String uuidStr = action.substring("harness_capture:".length());
        java.util.UUID uuid;
        try {
            uuid = java.util.UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("Invalid target"));
            return;
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        Entity target = world.getEntity(uuid);
        if (!(target instanceof LivingEntity living)) {
            player.sendMessage(Text.literal("Target is not valid"));
            return;
        }
        if (!living.getType().isIn(HARNESS_ALLOWED_MOUNTS_TAG)) {
            player.sendMessage(Text.literal("That entity cannot be harnessed"));
            return;
        }
        if (living.getCommandTags().contains(ModConstants.HARNESS_SUMMON_TAG)) {
            player.sendMessage(Text.literal("Cannot store a summoned harness mount"));
            return;
        }

        NbtComponent custom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound comp = custom != null ? custom.copyNbt() : new NbtCompound();
        String toolId = com.tabletmc.echo_summon.util.NbtUtils.getString(comp, ModConstants.HARNESS_SUMMON_TOOL_ID_KEY);
        if (toolId.isEmpty()) toolId = java.util.UUID.randomUUID().toString();
        comp.putString(ModConstants.HARNESS_SUMMON_TOOL_ID_KEY, toolId);

        Identifier typeId = EntityType.getId(living.getType());
        String typeIdStr = typeId != null ? typeId.toString() : "";

        NbtCompound harnessComponent = new NbtCompound();
        boolean harnessEquipped = false;
        if (!typeIdStr.isEmpty()) {
            harnessComponent.putString("mount_type", typeIdStr);
        }
        harnessComponent.putString(ModConstants.HARNESS_SUMMON_TOOL_ID_KEY, toolId);
        harnessComponent.putString(ModConstants.STORED_MOUNT_ID_KEY, living.getUuidAsString());

        EquipmentSlot slot = MountHarnessItem.resolveSlot(living.getType());
        if (slot != null) {
            ItemStack harnessStack = new ItemStack(ModItems.MOUNT_HARNESS);
            MountHarnessItem.applyEquippable(harnessStack, living.getType());
            harnessStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(harnessComponent.copy()));
            if (living.canEquip(harnessStack, slot)) {
                living.equipStack(slot, harnessStack);
                harnessEquipped = true;
                if (living instanceof HarnessableMountImpl harnessable) {
                    harnessable.echo_summon$setHarnessed(true);
                }
                NbtComponent equippedData = harnessStack.get(DataComponentTypes.CUSTOM_DATA);
                if (equippedData != null) {
                    harnessComponent = equippedData.copyNbt();
                }
            } else {
                harnessComponent = new NbtCompound();
            }
        } else {
            harnessComponent = new NbtCompound();
        }

        if (!harnessEquipped) {
            harnessComponent.remove(ModConstants.HARNESS_SUMMON_TOOL_ID_KEY);
            harnessComponent.remove(ModConstants.STORED_MOUNT_ID_KEY);
        }

        NbtCompound entityNbt = net.minecraft.predicate.NbtPredicate.entityToNbt(living);
        if (!typeIdStr.isEmpty()) {
            entityNbt.putString("id", typeIdStr);
        }
        entityNbt.putString(ModConstants.STORED_MOUNT_ID_KEY, living.getUuidAsString());
        entityNbt.putString(ModConstants.HARNESS_SUMMON_TOOL_ID_KEY, toolId);
        if (!harnessComponent.isEmpty()) {
            entityNbt.put(ModConstants.MOUNT_HARNESS_DATA_KEY, harnessComponent.copy());
        } else {
            entityNbt.remove(ModConstants.MOUNT_HARNESS_DATA_KEY);
        }

        comp.put(ModConstants.STORED_MOUNT_KEY, entityNbt);
        if (!harnessComponent.isEmpty()) {
            comp.put(ModConstants.MOUNT_HARNESS_DATA_KEY, harnessComponent.copy());
        } else {
            comp.remove(ModConstants.MOUNT_HARNESS_DATA_KEY);
        }
        // Flag if happy ghast and set CustomModelData for item predicate
        boolean isHappyGhast = ModConstants.HAPPY_GHAST_ID != null && ModConstants.HAPPY_GHAST_ID.toString().equals(typeIdStr);
        comp.putBoolean(ModConstants.STORED_IS_HAPPY_GHAST_KEY, isHappyGhast);
        summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(comp));
        try {
            if (isHappyGhast) {
                summonTool.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(), List.of(), List.of("echo_summon:happy_ghast"), List.of()));
            } else {
                summonTool.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
            }
        } catch (Throwable ignored) {}

        living.remove(Entity.RemovalReason.DISCARDED);
        ((com.tabletmc.echo_summon.impl.EntityMixinImpl) living).undoRemove();
        if (living instanceof HarnessableMountImpl harnessable) {
            harnessable.echo_summon$setHarnessed(false);
        }
        if (living instanceof MobEntity mob) {
            try { mob.setAiDisabled(false); } catch (Throwable ignored) {}
        }
        player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS);
        player.sendMessage(Text.literal("Harness mount stored"));
    }

    private static void handleHarnessDismiss(net.minecraft.server.network.ServerPlayerEntity player, ItemStack summonTool) {
        // Prefer current vehicle if valid
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof LivingEntity living && living.getCommandTags().contains(ModConstants.HARNESS_SUMMON_TAG)) {
            dismissHarnessMount(player, summonTool, living);
            return;
        }
        // Otherwise look for a nearby summoned mount linked to this tool and dismiss it
        LivingEntity found = findHarnessSummonedMount(player, summonTool);
        if (found != null) {
            dismissHarnessMount(player, summonTool, found);
            return;
        }
        player.sendMessage(Text.literal("No harness mount to dismiss"));
    }

    private static LivingEntity findHarnessSummonedMount(net.minecraft.server.network.ServerPlayerEntity player, ItemStack summonTool) {
        // Read tool linkage
        NbtComponent toolCustom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound toolNbt = toolCustom != null ? toolCustom.copyNbt() : new NbtCompound();
        String toolId = com.tabletmc.echo_summon.util.NbtUtils.getString(toolNbt, ModConstants.HARNESS_SUMMON_TOOL_ID_KEY);
        String toolTag = ModConstants.MOD_ID + ":harness_tool:" + toolId;
        String storedIdStr = toolNbt.getCompound(ModConstants.STORED_MOUNT_KEY)
                .map(n -> com.tabletmc.echo_summon.util.NbtUtils.getString(n, ModConstants.STORED_MOUNT_ID_KEY))
                .orElse("");

        // 1) Global lookup by stored UUID across all loaded worlds
        if (!storedIdStr.isEmpty()) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(storedIdStr);
                var server = player.getServer();
                if (server != null) {
                    for (ServerWorld world : server.getWorlds()) {
                        Entity e = world.getEntity(uuid);
                        if (e instanceof LivingEntity living) {
                            if (living.getCommandTags().contains(ModConstants.HARNESS_SUMMON_TAG)) {
                                // Prefer exact tool tag match if present
                                if (!toolId.isEmpty() && living.getCommandTags().contains(toolTag)) {
                                    return living;
                                }
                                // Otherwise accept UUID match
                                return living;
                            }
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // 2) Fallback: nearby scan in current world
        final String finalToolId = toolId;
        var box = player.getBoundingBox().expand(64.0);
        List<LivingEntity> candidates = player.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e.getCommandTags().contains(ModConstants.HARNESS_SUMMON_TAG));
        for (LivingEntity e : candidates) {
            // Prefer direct tool command tag match
            if (!toolId.isEmpty() && e.getCommandTags().contains(toolTag)) {
                return e;
            }
            // Match by harness component tool id
            EquipmentSlot slot = MountHarnessItem.resolveSlot(e.getType());
            if (slot != null) {
                ItemStack eq = e.getEquippedStack(slot);
                NbtComponent hc = eq.get(DataComponentTypes.CUSTOM_DATA);
                if (hc != null) {
                    NbtCompound hcNbt = hc.copyNbt();
                    String hid = com.tabletmc.echo_summon.util.NbtUtils.getString(hcNbt, ModConstants.HARNESS_SUMMON_TOOL_ID_KEY);
                    if (!hid.isEmpty() && hid.equals(finalToolId)) {
                        return e;
                    }
                }
            }
        }
        return null;
    }

    private static void dismissHarnessMount(net.minecraft.server.network.ServerPlayerEntity player, ItemStack summonTool, LivingEntity living) {
        // Remove linkage tag if present
        NbtComponent toolCustom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound toolNbt = toolCustom != null ? toolCustom.copyNbt() : new NbtCompound();
        String toolId = com.tabletmc.echo_summon.util.NbtUtils.getString(toolNbt, ModConstants.HARNESS_SUMMON_TOOL_ID_KEY);
        if (toolId.isEmpty()) {
            toolId = java.util.UUID.randomUUID().toString();
        }
        String toolTag = ModConstants.MOD_ID + ":harness_tool:" + toolId;

        // Persist current mount NBT and harness data back onto the tool so it can be re-summoned with harness intact
        NbtCompound stored = net.minecraft.predicate.NbtPredicate.entityToNbt(living);
        Identifier typeId = EntityType.getId(living.getType());
        if (typeId != null) {
            stored.putString("id", typeId.toString());
        }
        stored.putString(ModConstants.STORED_MOUNT_ID_KEY, living.getUuidAsString());

        toolNbt.putString(ModConstants.HARNESS_SUMMON_TOOL_ID_KEY, toolId);
        toolNbt.put(ModConstants.STORED_MOUNT_KEY, stored);

        EquipmentSlot slot = MountHarnessItem.resolveSlot(living.getType());
        if (slot != null) {
            ItemStack equipped = living.getEquippedStack(slot);
            if (!equipped.isEmpty()) {
                NbtComponent harnessData = equipped.get(DataComponentTypes.CUSTOM_DATA);
                NbtCompound harnessNbt = harnessData != null ? harnessData.copyNbt() : new NbtCompound();
                harnessNbt.putString(ModConstants.HARNESS_SUMMON_TOOL_ID_KEY, toolId);
                toolNbt.put(ModConstants.MOUNT_HARNESS_DATA_KEY, harnessNbt);
            } else {
                toolNbt.remove(ModConstants.MOUNT_HARNESS_DATA_KEY);
            }
        }

        // Maintain happy ghast flag and CustomModelData based on stored entity type
        boolean isHappyGhast2 = false;
        if (typeId != null) {
            isHappyGhast2 = ModConstants.HAPPY_GHAST_ID != null && ModConstants.HAPPY_GHAST_ID.equals(typeId);
        } else {
            String idStr = com.tabletmc.echo_summon.util.NbtUtils.getString(stored, "id");
            isHappyGhast2 = ModConstants.HAPPY_GHAST_ID != null && ModConstants.HAPPY_GHAST_ID.toString().equals(idStr);
        }
        toolNbt.putBoolean(ModConstants.STORED_IS_HAPPY_GHAST_KEY, isHappyGhast2);
        summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(toolNbt));
        try {
            if (isHappyGhast2) {
                summonTool.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(), List.of(), List.of("echo_summon:happy_ghast"), List.of()));
            } else {
                summonTool.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
            }
        } catch (Throwable ignored) {}

        living.remove(Entity.RemovalReason.DISCARDED);
        ((com.tabletmc.echo_summon.impl.EntityMixinImpl) living).undoRemove();
        living.removeCommandTag(ModConstants.HARNESS_SUMMON_TAG);
        if (!toolId.isEmpty()) {
            living.removeCommandTag(toolTag);
        }
        if (living instanceof HarnessableMountImpl harnessable) {
            harnessable.echo_summon$setHarnessed(false);
        }
        if (living instanceof MobEntity mob) {
            try { mob.setAiDisabled(false); } catch (Throwable ignored) {}
        }
        player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS);
        player.sendMessage(Text.literal("Harness mount dismissed"));
    }

    private static void handleHarnessSummon(net.minecraft.server.network.ServerPlayerEntity player, ItemStack summonTool) {
        if (player.hasVehicle()) {
            player.sendMessage(Text.literal("Already riding"));
            return;
        }
        NbtCompound stored = getHarnessStoredMount(summonTool);
        if (stored == null || stored.isEmpty()) {
            player.sendMessage(Text.literal("No harness mount stored"));
            return;
        }
        Entity loaded = EntityType.loadEntityWithPassengers(stored, player.getWorld(), SpawnReason.LOAD, Function.identity());
        if (!(loaded instanceof LivingEntity mount) || !loaded.getType().isIn(HARNESS_ALLOWED_MOUNTS_TAG)) {
            player.sendMessage(Text.literal("Stored entity is invalid"));
            return;
        }

        mount.fallDistance = player.fallDistance;
        mount.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        mount.setVelocity(player.getVelocity());
        mount.addCommandTag(ModConstants.HARNESS_SUMMON_TAG);

        if (mount instanceof MobEntity mob) {
            try { mob.setAiDisabled(true); } catch (Throwable ignored) {}
        }

        player.getWorld().spawnEntity(mount);

        NbtComponent toolCustom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound toolNbt = toolCustom != null ? toolCustom.copyNbt() : new NbtCompound();
        String toolId = com.tabletmc.echo_summon.util.NbtUtils.getString(toolNbt, ModConstants.HARNESS_SUMMON_TOOL_ID_KEY);
        String toolTag = ModConstants.MOD_ID + ":harness_tool:" + (toolId != null ? toolId : "");

        NbtCompound harnessComponent = getHarnessComponentData(summonTool);
        if (harnessComponent != null && !harnessComponent.isEmpty()) {
            NbtCompound harnessCopy = harnessComponent.copy();
            harnessCopy.putString(ModConstants.HARNESS_SUMMON_TOOL_ID_KEY, toolId);
            ItemStack harnessStack = new ItemStack(ModItems.MOUNT_HARNESS);
            MountHarnessItem.applyEquippable(harnessStack, mount.getType());
            harnessStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(harnessCopy));
            EquipmentSlot harnessSlot = MountHarnessItem.resolveSlot(mount.getType());
            if (harnessSlot != null && mount.canEquip(harnessStack, harnessSlot)) {
                mount.equipStack(harnessSlot, harnessStack);
                if (mount instanceof HarnessableMountImpl harnessable) {
                    harnessable.echo_summon$setHarnessed(true);
                }
            }
        }
        // Always link the summoned entity to this tool via a command tag so we can find/dismiss it reliably
        if (!toolId.isEmpty()) {
            mount.addCommandTag(toolTag);
        }
        player.startRiding(mount, true);
        player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS);
        player.sendMessage(Text.literal("Harness mount summoned"));
    }

    private static void handleHarnessRelease(net.minecraft.server.network.ServerPlayerEntity player, ItemStack summonTool) {
        NbtComponent custom = summonTool.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound comp = custom != null ? custom.copyNbt() : null;
        if (comp == null) {
            player.sendMessage(Text.literal("No harness mount stored"));
            return;
        }
        java.util.Optional<NbtCompound> opt = comp.getCompound(ModConstants.STORED_MOUNT_KEY);
        if (opt.isEmpty() || opt.get().isEmpty()) {
            player.sendMessage(Text.literal("No harness mount stored"));
            return;
        }
        NbtCompound stored = opt.get();
        Entity loaded = EntityType.loadEntityWithPassengers(stored, player.getWorld(), SpawnReason.LOAD, Function.identity());
        if (!(loaded instanceof LivingEntity mount) || !loaded.getType().isIn(HARNESS_ALLOWED_MOUNTS_TAG)) {
            player.sendMessage(Text.literal("Stored entity is invalid"));
            return;
        }

        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d spawnPos = findSafeSpawnPosition(player, look, 1.5, 3.0);
        mount.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, player.getYaw(), player.getPitch());

        if (mount instanceof MobEntity mob) {
            try { mob.setAiDisabled(false); } catch (Throwable ignored) {}
        }

        EquipmentSlot slot = MountHarnessItem.resolveSlot(mount.getType());
        if (slot != null) {
            ItemStack equipped = mount.getEquippedStack(slot);
            if (!equipped.isEmpty()) {
                mount.equipStack(slot, ItemStack.EMPTY);
                if (mount instanceof HarnessableMountImpl harnessable) {
                    harnessable.echo_summon$setHarnessed(false);
                }
            }
        }

        player.getWorld().spawnEntity(mount);

        mount.removeCommandTag(ModConstants.HARNESS_SUMMON_TAG);

        comp.remove(ModConstants.STORED_MOUNT_KEY);
        comp.remove(ModConstants.MOUNT_HARNESS_DATA_KEY);
        comp.remove(ModConstants.STORED_IS_HAPPY_GHAST_KEY);
        comp.remove(ModConstants.HARNESS_SUMMON_TOOL_ID_KEY);
        summonTool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(comp));
        try {
            summonTool.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
        } catch (Throwable ignored) {}

        player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS);
        player.sendMessage(Text.literal("Harness mount released"));
    }

    /**
     * Finds the appropriate summon tool in the player's hands (saddle vs harness).
     */
    private static ItemStack findSummonToolInHand(net.minecraft.server.network.ServerPlayerEntity player, boolean harness) {
        ItemStack main = player.getStackInHand(Hand.MAIN_HAND);
        if (harness) {
            if (main.getItem() instanceof com.tabletmc.echo_summon.item.custom.HarnessSummonToolItem) return main;
        } else {
            if (main.getItem() instanceof com.tabletmc.echo_summon.item.custom.SaddleSummonToolItem) return main;
        }
        ItemStack off = player.getStackInHand(Hand.OFF_HAND);
        if (harness) {
            if (off.getItem() instanceof com.tabletmc.echo_summon.item.custom.HarnessSummonToolItem) return off;
        } else {
            if (off.getItem() instanceof com.tabletmc.echo_summon.item.custom.SaddleSummonToolItem) return off;
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasStoredMount(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return false;
        NbtCompound comp = custom.copyNbt();
        return comp.getCompound(ModConstants.STORED_MOUNT_KEY).map(n -> !n.isEmpty()).orElse(false);
    }

    private static NbtCompound getStoredMount(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return null;
        NbtCompound comp = custom.copyNbt();
        java.util.Optional<NbtCompound> opt = comp.getCompound(ModConstants.STORED_MOUNT_KEY);
        return opt.filter(n -> !n.isEmpty()).orElse(null);
    }

    /**
     * Deletes all echo saddles in player's inventory that are linked to the given summon tool ID.
     */
    private static void deleteLinkedMountSaddles(net.minecraft.server.network.ServerPlayerEntity player, String summonToolId) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(ModItems.MOUNT_SADDLE)) {
                NbtComponent echoCustom = stack.get(DataComponentTypes.CUSTOM_DATA);
                if (echoCustom != null) {
                    NbtCompound echoComp = echoCustom.copyNbt();
                    String echoToolId = com.tabletmc.echo_summon.util.NbtUtils.getString(echoComp, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
                    if (summonToolId.equals(echoToolId)) {
                        inventory.removeStack(i);
                    }
                }
            }
        }
    }

    public static void handleSaddleAutoDismiss(ServerPlayerEntity player, LivingEntity mount) {
        if (player == null || mount == null) {
            return;
        }
        if (!mount.getCommandTags().contains(ModConstants.SUMMON_TAG)) {
            return;
        }
        String toolId = extractSaddleToolId(mount);
        ItemStack summonTool = ItemStack.EMPTY;
        if (!toolId.isEmpty()) {
            summonTool = findSaddleSummonToolById(player, toolId);
        }
        boolean applyCooldown = !summonTool.isEmpty();
        dismissSaddleSummonedMount(player, summonTool, mount, toolId, applyCooldown, true, Text.literal("Mount dismissed"));
    }

    private static boolean dismissSaddleSummonedMount(ServerPlayerEntity player, ItemStack summonTool, LivingEntity living, String toolId, boolean applyCooldown, boolean actionBar, Text feedback) {
        if (player == null || living == null) {
            return false;
        }
        living.remove(Entity.RemovalReason.DISCARDED);
        ((com.tabletmc.echo_summon.impl.EntityMixinImpl) living).undoRemove();
        living.removeCommandTag(ModConstants.SUMMON_TAG);
        if (!toolId.isEmpty()) {
            living.removeCommandTag(SADDLE_TOOL_TAG_PREFIX + toolId);
        }
        if (applyCooldown && summonTool != null && !summonTool.isEmpty()) {
            player.getItemCooldownManager().set(summonTool, ModConstants.SUMMON_COOLDOWN_TICKS);
        }
        if (feedback != null) {
            player.sendMessage(feedback, actionBar);
        }
        return true;
    }

    private static String extractSaddleToolId(LivingEntity living) {
        for (String tag : living.getCommandTags()) {
            if (tag.startsWith(SADDLE_TOOL_TAG_PREFIX)) {
                return tag.substring(SADDLE_TOOL_TAG_PREFIX.length());
            }
        }
        return "";
    }

    private static ItemStack findSaddleSummonToolById(ServerPlayerEntity player, String toolId) {
        if (toolId.isEmpty()) {
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

    private static boolean isMatchingSaddleSummonTool(ItemStack stack, String toolId) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof com.tabletmc.echo_summon.item.custom.SaddleSummonToolItem)) {
            return false;
        }
        return toolId.equals(getSaddleSummonToolId(stack));
    }

    private static String getSaddleSummonToolId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return "";
        }
        NbtCompound comp = custom.copyNbt();
        return com.tabletmc.echo_summon.util.NbtUtils.getString(comp, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
    }

    /**
     * Finds a safe spawn position in front of the player using raycast.
     * Falls back to player position if no valid spot found.
     */
    private static Vec3d findSafeSpawnPosition(net.minecraft.server.network.ServerPlayerEntity player, Vec3d direction, double preferredDistance, double maxDistance) {
        net.minecraft.server.world.ServerWorld world = player.getWorld();
        Vec3d forward = direction.normalize();

        double desiredDistance = Math.min(maxDistance, preferredDistance + 1.0);
        Vec3d eyePos = player.getEyePos();
        Vec3d rayEnd = eyePos.add(forward.multiply(maxDistance + 1.0));
        Vec3d targetPos = player.getPos().add(forward.multiply(desiredDistance));

        var hitResult = world.raycast(new net.minecraft.world.RaycastContext(
                eyePos,
                rayEnd,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            double hitDistance = hitResult.getPos().distanceTo(eyePos);
            double adjustedDistance = Math.max(0.5, Math.min(desiredDistance, hitDistance - 1.0));
            targetPos = eyePos.add(forward.multiply(adjustedDistance));
        }

        BlockPos column = BlockPos.ofFloored(targetPos.x, player.getY(), targetPos.z);
        ChunkPos chunkPos = new ChunkPos(column);
        if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
            column = player.getBlockPos();
            chunkPos = new ChunkPos(column);
        }

        BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
        BlockPos ground = surface.down();

        int downwardChecks = 0;
        while (world.isAir(ground) && ground.getY() > world.getBottomY() && downwardChecks++ < 8) {
            ground = ground.down();
        }
        if (world.isAir(ground)) {
            BlockPos fallbackSurface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, player.getBlockPos());
            return Vec3d.ofBottomCenter(fallbackSurface);
        }

        BlockPos spawnBlock = ground.up();
        if (!world.isAir(spawnBlock) || !world.isAir(spawnBlock.up())) {
            BlockPos check = spawnBlock;
            for (int i = 0; i < 4; i++) {
                if (world.isAir(check) && world.isAir(check.up())) {
                    spawnBlock = check;
                    break;
                }
                check = check.up();
            }
        }

        return Vec3d.ofBottomCenter(spawnBlock);
    }

    private static void setSaddled(Entity entity, boolean saddled) {
        if (entity instanceof SaddleableMountImpl saddleable) {
            saddleable.echo_summon$setSaddled(saddled);
        }
    }

    public static LivingEntity findHarnessSummonedMountForTool(ServerPlayerEntity player, ItemStack summonTool) {
        return findHarnessSummonedMount(player, summonTool);
    }

    public static void handleHarnessAutoDismiss(ServerPlayerEntity player, LivingEntity mount) {
        if (player == null || mount == null) {
            return;
        }
        if (!mount.getCommandTags().contains(ModConstants.HARNESS_SUMMON_TAG)) {
            return;
        }

        String toolId = extractHarnessToolId(mount);
        if (!toolId.isEmpty()) {
            ItemStack summonTool = findHarnessSummonToolById(player, toolId);
            if (!summonTool.isEmpty()) {
                dismissHarnessMount(player, summonTool, mount);
                return;
            }
        }

        mount.remove(Entity.RemovalReason.DISCARDED);
        ((com.tabletmc.echo_summon.impl.EntityMixinImpl) mount).undoRemove();
        mount.removeCommandTag(ModConstants.HARNESS_SUMMON_TAG);
        if (!toolId.isEmpty()) {
            mount.removeCommandTag(ModConstants.MOD_ID + ":harness_tool:" + toolId);
        }
        if (mount instanceof HarnessableMountImpl harnessable) {
            harnessable.echo_summon$setHarnessed(false);
        }
        if (mount instanceof MobEntity mob) {
            try {
                mob.setAiDisabled(false);
            } catch (Throwable ignored) {}
        }
        player.sendMessage(Text.literal("Harness mount dismissed"), true);
    }

    private static String extractHarnessToolId(LivingEntity living) {
        String prefix = ModConstants.MOD_ID + ":harness_tool:";
        for (String tag : living.getCommandTags()) {
            if (tag.startsWith(prefix)) {
                return tag.substring(prefix.length());
            }
        }
        return "";
    }

    private static ItemStack findHarnessSummonToolById(ServerPlayerEntity player, String toolId) {
        if (toolId == null || toolId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandStack();
        if (isMatchingHarnessSummonTool(main, toolId)) {
            return main;
        }
        ItemStack off = player.getOffHandStack();
        if (isMatchingHarnessSummonTool(off, toolId)) {
            return off;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (isMatchingHarnessSummonTool(stack, toolId)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isMatchingHarnessSummonTool(ItemStack stack, String toolId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof com.tabletmc.echo_summon.item.custom.HarnessSummonToolItem)) {
            return false;
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return false;
        }
        NbtCompound comp = custom.copyNbt();
        String id = com.tabletmc.echo_summon.util.NbtUtils.getString(comp, ModConstants.HARNESS_SUMMON_TOOL_ID_KEY);
        return toolId.equals(id);
    }

    // Maps entity ID to CustomModelData string keys used by the saddle summon tool asset selector.
    private static String mapEntityTypeToModelKey(String id) {
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