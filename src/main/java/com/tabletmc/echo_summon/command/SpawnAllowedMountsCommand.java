package com.tabletmc.echo_summon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tabletmc.echo_summon.config.EchoSummonConfig;
import com.tabletmc.echo_summon.config.SpawnCommandConfig;
import com.tabletmc.echo_summon.config.SpawnConfigService;
import com.tabletmc.echo_summon.impl.SaddleableMountImpl;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.item.custom.MountSaddleItem;
import com.tabletmc.echo_summon.item.custom.MountHarnessItem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseColor;
import net.minecraft.entity.passive.HorseMarking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.registry.Registries;
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

import java.util.*;
import java.util.function.Consumer;

/**
 * Developer/testing commands for spawning allowed mounts and cleaning them up.
 *
 * Provides commands under `/echo_summon` to:
 * - spawn a curated set of allowed mounts (optionally saddled)
 * - spawn at a specific position
 * - remove previously spawned test entities and tagged items
 */
public final class SpawnAllowedMountsCommand {
    private SpawnAllowedMountsCommand() {}

    private static final String TEST_COMMAND_TAG = "echo_summon:test_saddle_summon_tool_spawn";
    private static final String UNIVERSAL_TEST_TAG = "echo_summon:test_command";
    private static final String TEST_RUN_ID_KEY = "echo_summon:test_saddle_summon_tool_run";
    private static final Set<UUID> TRACKED_ENTITY_IDS = new HashSet<>();
    private static final Set<UUID> ACTIVE_RUN_IDS = new HashSet<>();

    /**
     * Registers the `/echo_summon` command tree.
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(buildCommand(registryAccess));
        });
    }

    /**
     * Builds the `echo_summon` command with subcommands for kill/test.
     */
    private static LiteralArgumentBuilder<ServerCommandSource> buildCommand(CommandRegistryAccess registryAccess) {
        return CommandManager.literal("echo_summon")
                .requires(src -> src.hasPermissionLevel(2))
                // '/echo_summon kill' removes previously spawned test entities
                .then(CommandManager.literal("kill").executes(SpawnAllowedMountsCommand::killUniversal))
                // '/echo_summon test' spawns the default unsaddled set in front of the player
                .then(CommandManager.literal("test")
                        .executes(SpawnAllowedMountsCommand::execute)
                        // '/echo_summon test saddled' spawns the saddled set in front of the player
                        .then(CommandManager.literal("saddled").executes(ctx -> execute(ctx, true)))
                        // '/echo_summon test <x y z>' spawns at a specific position in two rows
                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(ctx -> executeAllAtPos(ctx, BlockPosArgumentType.getBlockPos(ctx, "pos")))));
    }

    private static int execute(CommandContext<ServerCommandSource> ctx) {
        return execute(ctx, false);
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, boolean saddled) {
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

        // Build ordered spawn list per request
        java.util.List<ConfiguredSpawn> spawns = loadConfiguredSpawns();

        if (spawns.isEmpty()) {
            src.sendError(Text.literal("No mounts found to spawn (entity types not resolved)."));
            return 0;
        }

        UUID runId = UUID.randomUUID();
        ACTIVE_RUN_IDS.add(runId);

        Vec3d base = Objects.requireNonNull(player).getPos();
        float playerYaw = player.getYaw();
        float yawRadians = playerYaw * ((float) Math.PI / 180.0F);
        Vec3d forward = new Vec3d(-MathHelper.sin(yawRadians), 0.0, MathHelper.cos(yawRadians));
        Vec3d right = new Vec3d(MathHelper.cos(yawRadians), 0.0, MathHelper.sin(yawRadians));
        Vec3d forwardOffset = forward.multiply(2.0);
        double spacing = 2.0;
        int totalToSpawn = spawns.size();
        double centerOffset = totalToSpawn > 0 ? (totalToSpawn - 1) * 0.5 : 0.0;
        int index = 0;
        int spawned = 0;

        for (ConfiguredSpawn spec : spawns) {
            Vec3d lateral = right.multiply(spacing * (index - centerOffset));
            Vec3d pos = base.add(forwardOffset).add(lateral);
            // Shift Happy Ghast 4 blocks to the opposite side (left relative to player right)
            try {
                Identifier tId = EntityType.getId(spec.type);
                if (tId != null && tId.equals(Identifier.of("minecraft", "happy_ghast"))) {
                    pos = pos.add(right.multiply(-4.0));
                }
            } catch (Throwable ignored) {}
            if (spawnEntityForTypeConfigured(world, player, spec.type, pos, saddled, runId, spec.config)) {
                spawned++;
            }
            index++;
        }

        final int count = spawned;
        String suffix = saddled ? " (saddled)" : "";
        src.sendFeedback(() -> Text.literal("Spawned " + count + " allowed mounts" + suffix + "."), false);

        if (!saddled && spawned > 0) {
            for (int i = 0; i < spawned; i++) {
                ItemStack tool = new ItemStack(ModItems.SUMMON_TOOL);
                markStackWithRunId(tool, runId);
                player.giveItemStack(tool);
            }
        }

        return count;
    }

    private static int executeAllAtPos(CommandContext<ServerCommandSource> ctx, BlockPos origin) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            src.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        ServerWorld world = src.getWorld();

        UUID runId = UUID.randomUUID();
        ACTIVE_RUN_IDS.add(runId);

        // Build ordered spawn list identical to '/test_saddle_summon_tool'
        java.util.List<ConfiguredSpawn> spawns = loadConfiguredSpawns();

        if (spawns.isEmpty()) {
            src.sendError(Text.literal("No mounts found to spawn (entity types not resolved)."));
            return 0;
        }

        // Basis vectors and row offsets
        Vec3d base = new Vec3d(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        // Default orientation: West (-X). Ignore player orientation.
        Vec3d forward = new Vec3d(-1.0, 0.0, 0.0);
        Vec3d right = new Vec3d(0.0, 0.0, 1.0);
        double rowSpacing = 12.0;
        Vec3d row1Offset = forward.multiply(2.0);
        Vec3d row2Offset = forward.multiply(2.0 + rowSpacing);

        java.util.function.BiFunction<Vec3d, Boolean, Integer> spawnRow = (Vec3d rowOffset, Boolean saddled) -> {
            int spawned = 0;
            double spacing = 2.0;
            int totalToSpawn = spawns.size();
            double centerOffset = totalToSpawn > 0 ? (totalToSpawn - 1) * 0.5 : 0.0;
            int index = 0;
            for (ConfiguredSpawn spec : spawns) {
                Vec3d lateral = right.multiply(spacing * (index - centerOffset));
                Vec3d pos = base.add(rowOffset).add(lateral);
                // Shift Happy Ghast 4 blocks to the opposite side (left relative to player right)
                try {
                    Identifier tId = EntityType.getId(spec.type);
                    if (tId != null && tId.equals(Identifier.of("minecraft", "happy_ghast"))) {
                        pos = pos.add(right.multiply(-4.0));
                    }
                } catch (Throwable ignored) {}
                if (spawnEntityForTypeConfigured(world, player, spec.type, pos, saddled, runId, spec.config)) spawned++;
                index++;
            }
            return spawned;
        };

        int totalSpawned = 0;
        totalSpawned += spawnRow.apply(row1Offset, false); // row 1: unsaddled
        totalSpawned += spawnRow.apply(row2Offset, true);  // row 2: saddled

        int total = totalSpawned;
        // Always give 15 summon tools (tagged)
        try {
            for (int i = 0; i < 15; i++) {
                ItemStack tool = new ItemStack(ModItems.SUMMON_TOOL);
                markStackWithRunId(tool, runId);
                // add universal tag
                try {
                    NbtComponent custom = tool.get(DataComponentTypes.CUSTOM_DATA);
                    net.minecraft.nbt.NbtCompound nbt = custom != null ? custom.copyNbt() : new net.minecraft.nbt.NbtCompound();
                    nbt.putBoolean(UNIVERSAL_TEST_TAG, true);
                    tool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                } catch (Throwable ignored) {}
                Objects.requireNonNull(player).giveItemStack(tool);
            }
        } catch (Throwable ignored) {}
        src.sendFeedback(() -> Text.literal("Spawned 2 rows (default, saddled): total " + total + "."), false);
        return total;
    }

    private record ConfiguredSpawn(EntityType<?> type, Consumer<Entity> config) {}

    private static java.util.List<ConfiguredSpawn> loadConfiguredSpawns() {
        java.util.List<ConfiguredSpawn> spawns = new java.util.ArrayList<>();
        SpawnCommandConfig config = SpawnConfigService.getInstance().getConfig();
        for (SpawnCommandConfig.SpawnEntry entry : config.spawnEntries()) {
            try {
                EntityType<?> type = Registries.ENTITY_TYPE.get(entry.entityId());
                Consumer<Entity> consumer = null;
                if (entry.horseVariant() != null) {
                    SpawnCommandConfig.HorseVariant variant = entry.horseVariant();
                    consumer = combineConsumers(consumer, configureHorseVariant(variant.color(), variant.marking()));
                }
                if (entry.hasChest() != null) {
                    consumer = combineConsumers(consumer, configureChest(entry.hasChest()));
                }
                spawns.add(new ConfiguredSpawn(type, consumer));
            } catch (Throwable ignored) {}
        }
        return spawns;
    }

    private static Consumer<Entity> combineConsumers(Consumer<Entity> first, Consumer<Entity> second) {
        if (first == null) return second;
        if (second == null) return first;
        return entity -> {
            first.accept(entity);
            second.accept(entity);
        };
    }

    private static void logHorseVariant(Entity e, String stage) {
        try {
            if (!(e instanceof net.minecraft.entity.passive.HorseEntity)) return;
            Class<?> cls = e.getClass();
            Object color = null;
            Object marking = null;
            for (var m : cls.getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().isEnum()) {
                    String n = m.getName().toLowerCase(java.util.Locale.ROOT);
                    try {
                        if ((n.contains("color") || n.contains("coat")) && color == null) color = m.invoke(e);
                        if ((n.contains("mark") || n.contains("pattern") || n.contains("style")) && marking == null) marking = m.invoke(e);
                    } catch (Throwable ignored) {}
                }
            }
            String cs = (color instanceof Enum<?> en) ? en.name() : "unknown";
            String ms = (marking instanceof Enum<?> en) ? en.name() : "unknown";
        } catch (Throwable ignored) {}
    }

    private static Consumer<Entity> configureHorseVariant(String colorName, String markingName) {
        HorseColor color = resolveHorseColor(colorName);
        HorseMarking marking = resolveHorseMarking(markingName);
        if (color == null || marking == null) {
            return entity -> {};
        }

        int colorOrd = color.ordinal();
        int markingOrd = marking.ordinal();

        return entity -> {
            if (!(entity instanceof net.minecraft.entity.passive.HorseEntity horse)) {
                return;
            }
            int encoded = (colorOrd & 0xFF) | ((markingOrd & 0xFF) << 8);
            try {
                horse.getDataTracker().set(net.minecraft.entity.passive.HorseEntity.VARIANT, encoded);
            } catch (Throwable ignored) {}
        };
    }

    private static Consumer<Entity> configureChest(boolean hasChest) {
        return entity -> {
            if (!(entity instanceof net.minecraft.entity.passive.AbstractDonkeyEntity donkey)) {
                return;
            }
            try {
                donkey.setHasChest(hasChest);
            } catch (Throwable ignored) {}
        };
    }

    private static HorseColor resolveHorseColor(String name) {
        if (name == null) {
            return null;
        }
        String key = name.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        return switch (key) {
            case "WHITE" -> HorseColor.WHITE;
            case "CREAMY" -> HorseColor.CREAMY;
            case "CHESTNUT" -> HorseColor.CHESTNUT;
            case "BROWN" -> HorseColor.BROWN;
            case "BLACK" -> HorseColor.BLACK;
            case "GRAY" -> HorseColor.GRAY;
            case "DARK_BROWN", "DARKBROWN" -> HorseColor.DARK_BROWN;
            default -> null;
        };
    }

    private static HorseMarking resolveHorseMarking(String name) {
        if (name == null) {
            return null;
        }
        String key = name.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        return switch (key) {
            case "NONE" -> HorseMarking.NONE;
            case "WHITE" -> HorseMarking.WHITE;
            case "WHITE_FIELD", "WHITEFIELD" -> HorseMarking.WHITE_FIELD;
            case "WHITE_DOTS", "WHITEDOTS" -> HorseMarking.WHITE_DOTS;
            case "BLACK_DOTS", "BLACKDOTS" -> HorseMarking.BLACK_DOTS;
            default -> null;
        };
    }

    private static boolean spawnEntityForTypeConfigured(ServerWorld world, ServerPlayerEntity player, EntityType<?> type, Vec3d desiredPos, boolean saddled, UUID runId, Consumer<Entity> config) {
        Entity entity = type.create(world, SpawnReason.COMMAND);
        if (entity == null) return false;

        double spawnY = findHighestSafeY(world, entity, desiredPos);
        // Raise happy ghast spawns by +2 blocks above ground
        try {
            Identifier tId = EntityType.getId(entity.getType());
            if (tId != null && tId.equals(Identifier.of("minecraft", "happy_ghast"))) {
                spawnY += 2.0;
            }
        } catch (Throwable ignored) {}
        Vec3d spawnPos = new Vec3d(desiredPos.x, spawnY, desiredPos.z);
        // Face West (-X) so spawns are shoulder-to-shoulder and not facing the player
        float yaw = 90.0F;

        entity.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, yaw, 0f);
        entity.setYaw(yaw);
        entity.setPitch(0f);

        if (entity instanceof AbstractHorseEntity ahe) {
            try { ahe.setTame(true); } catch (Throwable ignored) {}
        }

        if (entity instanceof LivingEntity living) {
            living.setHeadYaw(yaw);
            living.setBodyYaw(yaw);
            // Apply custom configuration before equipping saddle
            if (config != null) {
                try { config.accept(entity); } catch (Throwable ignored) {}
                logHorseVariant(entity, "pre-config");
            }
            if (saddled) {
                try {
                    Identifier typeId = EntityType.getId(living.getType());
                    boolean isHappyGhast = typeId != null && "happy_ghast".equals(typeId.getPath());
                    if (isHappyGhast) {
                        // Equip mount harness to Happy Ghast in the saddled row
                        net.minecraft.item.ItemStack harness = new net.minecraft.item.ItemStack(ModItems.MOUNT_HARNESS);
                        MountHarnessItem.applyEquippable(harness, living.getType());
                        living.equipStack(EquipmentSlot.BODY, harness);
                    } else {
                        equipMountSaddle(living, runId);
                    }
                } catch (Throwable ignored) {
                    equipMountSaddle(living, runId);
                }
            }
        }

        if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
            try {
                // Always disable AI for test-spawned entities to prevent wandering/movement
                mob.setAiDisabled(true);
            } catch (Throwable ignored) {}
        }

        entity.addCommandTag(TEST_COMMAND_TAG);
        entity.addCommandTag(UNIVERSAL_TEST_TAG);

        if (world.spawnEntity(entity)) {
            TRACKED_ENTITY_IDS.add(entity.getUuid());
            // Re-apply configuration after spawn (twice) to avoid init resetting to white
            if (config != null) {
                try {
                    world.getServer().execute(() -> {
                        try { config.accept(entity); } catch (Throwable ignored) {}
                        logHorseVariant(entity, "post-spawn-1");
                        // Also reinforce AI disabled on test-spawned mobs
                        if (entity instanceof net.minecraft.entity.mob.MobEntity mob1) {
                            try { mob1.setAiDisabled(true); } catch (Throwable ignored) {}
                        }
                        // schedule 2nd pass next tick as well
                        world.getServer().execute(() -> {
                            try { config.accept(entity); } catch (Throwable ignored) {}
                            logHorseVariant(entity, "post-spawn-2");
                            if (entity instanceof net.minecraft.entity.mob.MobEntity mob2) {
                                try { mob2.setAiDisabled(true); } catch (Throwable ignored) {}
                            }
                            // schedule 3rd pass to handle late init/AI ticks
                            world.getServer().execute(() -> {
                                try { config.accept(entity); } catch (Throwable ignored) {}
                                logHorseVariant(entity, "post-spawn-3");
                                if (entity instanceof net.minecraft.entity.mob.MobEntity mob3) {
                                    try { mob3.setAiDisabled(true); } catch (Throwable ignored) {}
                                }
                            });
                        });
                    });
                } catch (Throwable ignored) {}
            } else {
                // Even without extra config, schedule AI disable on subsequent ticks for safety
                try {
                    world.getServer().execute(() -> {
                        if (entity instanceof net.minecraft.entity.mob.MobEntity mob1) {
                            try { mob1.setAiDisabled(true); } catch (Throwable ignored) {}
                        }
                        world.getServer().execute(() -> {
                            if (entity instanceof net.minecraft.entity.mob.MobEntity mob2) {
                                try { mob2.setAiDisabled(true); } catch (Throwable ignored) {}
                            }
                        });
                    });
                } catch (Throwable ignored) {}
            }
            return true;
        }
        return false;
    }

    private static boolean spawnEntityForType(ServerWorld world, ServerPlayerEntity player, EntityType<?> type, Vec3d desiredPos, boolean saddled, UUID runId) {
        Entity entity = type.create(world, SpawnReason.COMMAND);
        if (entity == null) return false;

        double spawnY = findHighestSafeY(world, entity, desiredPos);
        // Raise happy ghast spawns by +2 blocks above ground
        try {
            Identifier tId = EntityType.getId(entity.getType());
            if (tId != null && tId.equals(Identifier.of("minecraft", "happy_ghast"))) {
                spawnY += 2.0;
            }
        } catch (Throwable ignored) {}
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
                try {
                    Identifier typeId = EntityType.getId(living.getType());
                    boolean isHappyGhast = typeId != null && "happy_ghast".equals(typeId.getPath());
                    if (isHappyGhast) {
                        net.minecraft.item.ItemStack harness = new net.minecraft.item.ItemStack(ModItems.MOUNT_HARNESS);
                        MountHarnessItem.applyEquippable(harness, living.getType());
                        living.equipStack(EquipmentSlot.BODY, harness);
                    } else {
                        equipMountSaddle(living, runId);
                    }
                } catch (Throwable ignored) {
                    equipMountSaddle(living, runId);
                }
            }
        }

        if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
            try {
                // Always disable AI for test-spawned entities to prevent wandering/movement
                mob.setAiDisabled(true);
            } catch (Throwable ignored) {}
        }

        entity.addCommandTag(TEST_COMMAND_TAG);

        if (world.spawnEntity(entity)) {
            TRACKED_ENTITY_IDS.add(entity.getUuid());
            // Reinforce AI disabled for test-spawned mobs across a couple of ticks
            try {
                world.getServer().execute(() -> {
                    if (entity instanceof net.minecraft.entity.mob.MobEntity mob1) {
                        try { mob1.setAiDisabled(true); } catch (Throwable ignored) {}
                    }
                    world.getServer().execute(() -> {
                        if (entity instanceof net.minecraft.entity.mob.MobEntity mob2) {
                            try { mob2.setAiDisabled(true); } catch (Throwable ignored) {}
                        }
                    });
                });
            } catch (Throwable ignored) {}
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

    // equipMountHarness removed: harness system is obsolete

    private static void markStackWithRunId(ItemStack stack, UUID runId) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        net.minecraft.nbt.NbtCompound compound = custom != null ? custom.copyNbt() : new net.minecraft.nbt.NbtCompound();
        compound.putString(TEST_RUN_ID_KEY, runId.toString());
        // mark universal tag on items created by this command
        compound.putBoolean(UNIVERSAL_TEST_TAG, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(compound));
    }

    private static int killUniversal(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        MinecraftServer server = src.getServer();
        int removedEntities = 0;
        int removedItems = 0;

        // Remove entities (including item entities) with the universal command tag
        for (ServerWorld world : server.getWorlds()) {
            java.util.List<Entity> toRemove = new java.util.ArrayList<>();
            for (Entity e : world.iterateEntities()) {
                try {
                    if (e.getCommandTags().contains(UNIVERSAL_TEST_TAG)) {
                        toRemove.add(e);
                    } else if (e instanceof ItemEntity itemEntity) {
                        if (hasUniversalTestTag(itemEntity.getStack())) {
                            toRemove.add(e);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            for (Entity e : toRemove) {
                try {
                    e.discard();
                    removedEntities++;
                } catch (Throwable ignored) {}
            }
        }

        // Remove tagged items from all player inventories
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            try {
                var inv = p.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack s = inv.getStack(i);
                    if (hasUniversalTestTag(s)) {
                        removedItems += s.getCount();
                        inv.setStack(i, ItemStack.EMPTY);
                    }
                }
                p.playerScreenHandler.sendContentUpdates();
            } catch (Throwable ignored) {}
        }

        int total = removedEntities + removedItems;
        if (total == 0) {
            src.sendFeedback(() -> Text.literal("No entities or items with echo_summon:test_command found."), false);
        } else {
            final int fe = removedEntities;
            final int fi = removedItems;
            src.sendFeedback(() -> Text.literal("Removed " + fe + " entities and " + fi + " items with echo_summon:test_command."), false);
        }
        return total;
    }

    private static boolean hasUniversalTestTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return false;
        net.minecraft.nbt.NbtCompound nbt = custom.copyNbt();
        return nbt.contains(UNIVERSAL_TEST_TAG);
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
        String stored = storedOpt.get();
        try {
            UUID runId = UUID.fromString(stored);
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
