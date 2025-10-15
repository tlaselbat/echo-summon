package com.tabletmc.echo_summon.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.SaddleableMountImpl;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.item.custom.MountSaddleItem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SpawnAllowedMountsCommand {
    private SpawnAllowedMountsCommand() {}

    private static final String TEST_COMMAND_TAG = "echo_summon:test_saddle_summon_tool_spawn";
    private static final String TEST_RUN_ID_KEY = "echo_summon:test_saddle_summon_tool_run";
    private static final Set<UUID> TRACKED_ENTITY_IDS = new HashSet<>();
    private static final Set<UUID> ACTIVE_RUN_IDS = new HashSet<>();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(buildCommand(registryAccess));
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildCommand(CommandRegistryAccess registryAccess) {
        return CommandManager.literal("test_saddle_summon_tool")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(SpawnAllowedMountsCommand::execute)
                .then(CommandManager.literal("kill")
                        .executes(ctx -> execute(ctx, false, true)))
                .then(CommandManager.literal("saddled")
                        .executes(ctx -> execute(ctx, true, false)));
    }

    private static int execute(CommandContext<ServerCommandSource> ctx) {
        return execute(ctx, false, false);
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, boolean saddled, boolean kill) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            src.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        ServerWorld world = src.getWorld();

        KillSummary summary = KillSummary.EMPTY;
        if (kill) {
            summary = killSpawned(src);
            sendKillFeedback(src, summary);
            if (!saddled) {
                return summary.total();
            }
        }

        TagKey<EntityType<?>> tag = TagKey.of(RegistryKeys.ENTITY_TYPE, ModConstants.Id(ModConstants.ALLOWED_MOUNTS_TAG_PATH));
        java.util.List<EntityType<?>> types = new java.util.ArrayList<>();
        java.util.List<EntityType<?>> happyGhasts = new java.util.ArrayList<>();
        for (EntityType<?> t : Registries.ENTITY_TYPE) {
            if (t.isIn(tag)) {
                Identifier id = Registries.ENTITY_TYPE.getId(t);
                if (id != null && id.equals(Identifier.of("minecraft", "happy_ghast"))) {
                    happyGhasts.add(t);
                } else {
                    types.add(t);
                }
            }
        }
        if (types.isEmpty() && happyGhasts.isEmpty()) {
            src.sendError(Text.literal("No allowed mounts found (tag empty)."));
            return 0;
        }

        UUID runId = UUID.randomUUID();
        ACTIVE_RUN_IDS.add(runId);

        Vec3d base = player.getPos();
        float playerYaw = player.getYaw();
        float yawRadians = playerYaw * ((float) Math.PI / 180.0F);
        Vec3d forward = new Vec3d(-MathHelper.sin(yawRadians), 0.0, MathHelper.cos(yawRadians));
        Vec3d right = new Vec3d(MathHelper.cos(yawRadians), 0.0, MathHelper.sin(yawRadians));
        Vec3d forwardOffset = forward.multiply(2.0);
        double spacing = 2.0;
        int totalToSpawn = types.size() + happyGhasts.size();
        double centerOffset = totalToSpawn > 0 ? (totalToSpawn - 1) * 0.5 : 0.0;
        int index = 0;
        int spawned = 0;

        for (EntityType<?> type : types) {
            Vec3d lateral = right.multiply(spacing * (index - centerOffset));
            Vec3d pos = base.add(forwardOffset).add(lateral);
            if (spawnEntityForType(world, player, type, pos, saddled, runId)) {
                spawned++;
            }
            index++;
        }

        // Spawn happy_ghast last and +5 blocks further
        for (EntityType<?> type : happyGhasts) {
            Vec3d lateral = right.multiply(spacing * (index - centerOffset));
            Vec3d pos = base.add(forwardOffset).add(forward.multiply(5.0)).add(lateral);
            if (spawnEntityForType(world, player, type, pos, saddled, runId)) {
                spawned++;
            }
            index++;
        }

        final int count = spawned;
        String suffix = saddled ? " (saddled)" : "";
        src.sendFeedback(() -> Text.literal("Spawned " + count + " allowed mounts" + suffix + "."), false);

        if (!saddled && spawned > 0) {
            for (int i = 0; i < spawned; i++) {
                ItemStack tool = new ItemStack(ModItems.SADDLE_SUMMON_TOOL);
                markStackWithRunId(tool, runId);
                player.giveItemStack(tool);
            }
        }

        return count;
    }

    private static boolean spawnEntityForType(ServerWorld world, ServerPlayerEntity player, EntityType<?> type, Vec3d desiredPos, boolean saddled, UUID runId) {
        Entity entity = type.create(world, SpawnReason.COMMAND);
        if (entity == null) return false;

        double spawnY = findHighestSafeY(world, entity, desiredPos);
        Vec3d spawnPos = new Vec3d(desiredPos.x, spawnY, desiredPos.z);
        float yaw = MathHelper.wrapDegrees(player.getYaw() + 180.0F);

        entity.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, yaw, 0f);
        entity.setYaw(yaw);
        entity.setPitch(0f);

        if (entity instanceof AbstractHorseEntity ahe) {
            try { ahe.setTame(true); } catch (Throwable ignored) {}
        }

        if (entity instanceof LivingEntity living) {
            living.setHeadYaw(yaw);
            living.setBodyYaw(yaw);
            if (saddled) {
                equipMountSaddle(living, runId);
            }
        }

        if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
            try { mob.setAiDisabled(true); } catch (Throwable ignored) {}
        }

        entity.addCommandTag(TEST_COMMAND_TAG);

        if (world.spawnEntity(entity)) {
            TRACKED_ENTITY_IDS.add(entity.getUuid());
            return true;
        }
        return false;
    }

    private static double findHighestSafeY(ServerWorld world, Entity entity, Vec3d desiredPos) {
        BlockPos column = BlockPos.ofFloored(desiredPos);
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        BlockPos.Mutable mutable = new BlockPos.Mutable(column.getX(), topY, column.getZ());
        int minY = world.getBottomY();

        float width = entity.getWidth();
        float height = entity.getHeight();
        double halfWidth = width / 2.0;

        while (mutable.getY() >= minY) {
            BlockState belowState = world.getBlockState(mutable);
            if (!belowState.getCollisionShape(world, mutable).isEmpty()) {
                double candidateY = mutable.getY() + 1.0;
                if (isSpaceClear(world, entity, desiredPos.x, candidateY, desiredPos.z, halfWidth, height)) {
                    return candidateY;
                }
            }
            mutable.move(Direction.DOWN);
        }

        return topY + 1.0;
    }

    private static boolean isSpaceClear(ServerWorld world, Entity entity, double x, double y, double z, double halfWidth, double height) {
        Box box = new Box(
                x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + height, z + halfWidth
        );

        BlockPos below = BlockPos.ofFloored(x, y - 0.01, z);
        BlockState belowState = world.getBlockState(below);
        if (belowState.getCollisionShape(world, below).isEmpty()) {
            return false;
        }

        return world.isSpaceEmpty(entity, box);
    }

    private static void equipMountSaddle(LivingEntity living, UUID runId) {
        ItemStack saddle = new ItemStack(ModItems.MOUNT_SADDLE);
        markStackWithRunId(saddle, runId);
        MountSaddleItem.applyEquippable(saddle, living.getType());
        EquipmentSlot slot = MountSaddleItem.resolveSlot(living.getType());
        
        if (slot == null) {
            return;
        }

        try {
            if (living.canEquip(saddle, slot)) {
                living.equipStack(slot, saddle);
                
                if (living instanceof SaddleableMountImpl saddleable) {
                    try { saddleable.echo_summon$setSaddled(true); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    
    }

    private static void markStackWithRunId(ItemStack stack, UUID runId) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        net.minecraft.nbt.NbtCompound compound = custom != null ? custom.copyNbt() : new net.minecraft.nbt.NbtCompound();
        compound.putString(TEST_RUN_ID_KEY, runId.toString());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(compound));
    }

    private static KillSummary killSpawned(ServerCommandSource src) {
        MinecraftServer server = src.getServer();
        int removedEntities = 0;

        Iterator<UUID> iterator = TRACKED_ENTITY_IDS.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Entity entity = findEntity(server, uuid);
            if (entity != null) {
                entity.discard();
                removedEntities++;
            }
            iterator.remove();
        }

        int removedItems = 0;
        if (!ACTIVE_RUN_IDS.isEmpty()) {
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                removedItems += removeRunItemsFromInventory(online);
            }
            for (ServerWorld serverWorld : server.getWorlds()) {
                removedItems += removeRunItemsFromWorld(serverWorld);
            }
            ACTIVE_RUN_IDS.clear();
        }

        return new KillSummary(removedEntities, removedItems);
    }

    private static void sendKillFeedback(ServerCommandSource src, KillSummary summary) {
        if (summary.total() == 0) {
            src.sendFeedback(() -> Text.literal("No test saddle summon tool mounts or items to remove."), false);
        } else {
            src.sendFeedback(() -> Text.literal("Removed " + summary.entities() + " mounts and " + summary.items() + " items spawned by test_saddle_summon_tool."), false);
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static int removeRunItemsFromInventory(ServerPlayerEntity player) {
        int removed = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (hasTrackedRunId(stack)) {
                removed += stack.getCount();
                inventory.setStack(i, ItemStack.EMPTY);
            }
        }
        if (removed > 0) {
            player.playerScreenHandler.sendContentUpdates();
        }
        return removed;
    }

    private static int removeRunItemsFromWorld(ServerWorld world) {
        int removed = 0;
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getStack();
                if (hasTrackedRunId(stack)) {
                    removed += stack.getCount();
                    itemEntity.discard();
                }
            }
        }
        return removed;
    }

    private static boolean hasTrackedRunId(ItemStack stack) {
        if (stack.isEmpty() || ACTIVE_RUN_IDS.isEmpty()) {
            return false;
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return false;
        }
        net.minecraft.nbt.NbtCompound compound = custom.copyNbt();
        if (!compound.contains(TEST_RUN_ID_KEY)) {
            return false;
        }
        Optional<String> storedOpt = compound.getString(TEST_RUN_ID_KEY);
        if (storedOpt.isEmpty()) {
            return false;
        }
        try {
            UUID runId = UUID.fromString(storedOpt.get());
            return ACTIVE_RUN_IDS.contains(runId);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private record KillSummary(int entities, int items) {
        static final KillSummary EMPTY = new KillSummary(0, 0);

        int total() {
            return entities + items;
        }

        boolean hasAny() {
            return entities > 0 || items > 0;
        }
    }
}
