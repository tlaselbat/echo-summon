package com.tabletmc.echo_summon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.HarnessableMountImpl;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class SpawnAllowedMountsCommand {
    private SpawnAllowedMountsCommand() {}

    private static final String TEST_COMMAND_TAG = "echo_summon:test_saddle_summon_tool_spawn";
    private static final String UNIVERSAL_TEST_TAG = "echo_summon:test_command";
    private static final String TEST_RUN_ID_KEY = "echo_summon:test_saddle_summon_tool_run";
    private static final Set<UUID> TRACKED_ENTITY_IDS = new HashSet<>();
    private static final Set<UUID> ACTIVE_RUN_IDS = new HashSet<>();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(buildCommand(registryAccess));
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildCommand(CommandRegistryAccess registryAccess) {
        return CommandManager.literal("echo_summon")
                .requires(src -> src.hasPermissionLevel(2))
                // no root executor; '/echo_summon' alone does nothing
                .then(CommandManager.literal("kill").executes(SpawnAllowedMountsCommand::killUniversal))
                .then(CommandManager.literal("test")
                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(ctx -> executeAllAtPos(ctx, BlockPosArgumentType.getBlockPos(ctx, "pos")))));
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

        // Build ordered spawn list per request
        java.util.List<ConfiguredSpawn> spawns = new java.util.ArrayList<>();
        try {
            EntityType<?> ghast = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "happy_ghast"));
            EntityType<?> camel = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "camel"));
            EntityType<?> zombieHorse = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "zombie_horse"));
            EntityType<?> skeletonHorse = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "skeleton_horse"));
            EntityType<?> donkey = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "donkey"));
            EntityType<?> mule = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "mule"));
            EntityType<?> horse = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "horse"));

            if (ghast != null) spawns.add(new ConfiguredSpawn(ghast, null));
            if (camel != null) spawns.add(new ConfiguredSpawn(camel, null));
            if (zombieHorse != null) spawns.add(new ConfiguredSpawn(zombieHorse, null));
            if (skeletonHorse != null) spawns.add(new ConfiguredSpawn(skeletonHorse, null));
            if (donkey != null) spawns.add(new ConfiguredSpawn(donkey, configureChest(false))); // DONKEY (NO CHEST)
            if (mule != null) spawns.add(new ConfiguredSpawn(mule, configureChest(false)));     // MULE (NO CHEST)
            if (donkey != null) spawns.add(new ConfiguredSpawn(donkey, configureChest(true)));  // DONKEY + CHEST
            if (mule != null) spawns.add(new ConfiguredSpawn(mule, configureChest(true)));      // MULE + CHEST
            if (horse != null) {
                // Horse variants
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("WHITE",      "BLACK_DOTS")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("GRAY",       "WHITE_FIELD")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("CREAMY",     "WHITE_DOTS")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("BROWN",      "WHITE")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("DARK_BROWN", "NONE")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("CHESTNUT",   "NONE")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("BLACK",      "NONE")));
            }
        } catch (Throwable ignored) {}

        if (spawns.isEmpty()) {
            src.sendError(Text.literal("No mounts found to spawn (entity types not resolved)."));
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
                ItemStack tool = new ItemStack(ModItems.SADDLE_SUMMON_TOOL);
                markStackWithRunId(tool, runId);
                player.giveItemStack(tool);
            }
        }

        return count;
    }

    private static int executeAll(CommandContext<ServerCommandSource> ctx) {
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
        java.util.List<ConfiguredSpawn> spawns = new java.util.ArrayList<>();
        try {
            EntityType<?> ghast = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "happy_ghast"));
            EntityType<?> camel = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "camel"));
            EntityType<?> zombieHorse = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "zombie_horse"));
            EntityType<?> skeletonHorse = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "skeleton_horse"));
            EntityType<?> donkey = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "donkey"));
            EntityType<?> mule = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "mule"));
            EntityType<?> horse = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "horse"));

            if (ghast != null) spawns.add(new ConfiguredSpawn(ghast, null));
            if (camel != null) spawns.add(new ConfiguredSpawn(camel, null));
            if (zombieHorse != null) spawns.add(new ConfiguredSpawn(zombieHorse, null));
            if (skeletonHorse != null) spawns.add(new ConfiguredSpawn(skeletonHorse, null));
            if (donkey != null) spawns.add(new ConfiguredSpawn(donkey, configureChest(false))); // DONKEY (NO CHEST)
            if (mule != null) spawns.add(new ConfiguredSpawn(mule, configureChest(false)));     // MULE (NO CHEST)
            if (donkey != null) spawns.add(new ConfiguredSpawn(donkey, configureChest(true)));  // DONKEY + CHEST
            if (mule != null) spawns.add(new ConfiguredSpawn(mule, configureChest(true)));      // MULE + CHEST
            if (horse != null) {
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("WHITE",      "BLACK_DOTS")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("GRAY",       "WHITE_FIELD")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("CREAMY",     "WHITE_DOTS")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("BROWN",      "WHITE")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("DARK_BROWN", "NONE")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("CHESTNUT",   "NONE")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("BLACK",      "NONE")));
            }
        } catch (Throwable ignored) {}

        if (spawns.isEmpty()) {
            src.sendError(Text.literal("No mounts found to spawn (entity types not resolved)."));
            return 0;
        }

        // Basis vectors and row offsets
        Vec3d base = player.getPos();
        float playerYaw = player.getYaw();
        float yawRadians = playerYaw * ((float) Math.PI / 180.0F);
        Vec3d forward = new Vec3d(-MathHelper.sin(yawRadians), 0.0, MathHelper.cos(yawRadians));
        Vec3d right = new Vec3d(MathHelper.cos(yawRadians), 0.0, MathHelper.sin(yawRadians));
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
                ItemStack tool = new ItemStack(ModItems.SADDLE_SUMMON_TOOL);
                markStackWithRunId(tool, runId);
                // add universal tag
                try {
                    NbtComponent custom = tool.get(DataComponentTypes.CUSTOM_DATA);
                    net.minecraft.nbt.NbtCompound nbt = custom != null ? custom.copyNbt() : new net.minecraft.nbt.NbtCompound();
                    nbt.putBoolean(UNIVERSAL_TEST_TAG, true);
                    tool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                } catch (Throwable ignored) {}
                player.giveItemStack(tool);
            }
        } catch (Throwable ignored) {}
        src.sendFeedback(() -> Text.literal("Spawned 2 rows (default, saddled): total " + total + "."), false);
        return total;
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
        java.util.List<ConfiguredSpawn> spawns = new java.util.ArrayList<>();
        try {
            EntityType<?> ghast = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "happy_ghast"));
            EntityType<?> camel = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "camel"));
            EntityType<?> zombieHorse = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "zombie_horse"));
            EntityType<?> skeletonHorse = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "skeleton_horse"));
            EntityType<?> donkey = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "donkey"));
            EntityType<?> mule = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "mule"));
            EntityType<?> horse = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", "horse"));

            if (ghast != null) spawns.add(new ConfiguredSpawn(ghast, null));
            if (camel != null) spawns.add(new ConfiguredSpawn(camel, null));
            if (zombieHorse != null) spawns.add(new ConfiguredSpawn(zombieHorse, null));
            if (skeletonHorse != null) spawns.add(new ConfiguredSpawn(skeletonHorse, null));
            if (donkey != null) spawns.add(new ConfiguredSpawn(donkey, configureChest(false))); // DONKEY (NO CHEST)
            if (mule != null) spawns.add(new ConfiguredSpawn(mule, configureChest(false)));     // MULE (NO CHEST)
            if (donkey != null) spawns.add(new ConfiguredSpawn(donkey, configureChest(true)));  // DONKEY + CHEST
            if (mule != null) spawns.add(new ConfiguredSpawn(mule, configureChest(true)));      // MULE + CHEST
            if (horse != null) {
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("WHITE",      "BLACK_DOTS")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("GRAY",       "WHITE_FIELD")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("CREAMY",     "WHITE_DOTS")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("BROWN",      "WHITE")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("DARK_BROWN", "NONE")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("CHESTNUT",   "NONE")));
                spawns.add(new ConfiguredSpawn(horse, configureHorseVariant("BLACK",      "NONE")));
            }
        } catch (Throwable ignored) {}

        if (spawns.isEmpty()) {
            src.sendError(Text.literal("No mounts found to spawn (entity types not resolved)."));
            return 0;
        }

        // Basis vectors and row offsets
        Vec3d base = new Vec3d(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        float playerYaw = player.getYaw();
        float yawRadians = playerYaw * ((float) Math.PI / 180.0F);
        Vec3d forward = new Vec3d(-MathHelper.sin(yawRadians), 0.0, MathHelper.cos(yawRadians));
        Vec3d right = new Vec3d(MathHelper.cos(yawRadians), 0.0, MathHelper.sin(yawRadians));
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
                ItemStack tool = new ItemStack(ModItems.SADDLE_SUMMON_TOOL);
                markStackWithRunId(tool, runId);
                // add universal tag
                try {
                    NbtComponent custom = tool.get(DataComponentTypes.CUSTOM_DATA);
                    net.minecraft.nbt.NbtCompound nbt = custom != null ? custom.copyNbt() : new net.minecraft.nbt.NbtCompound();
                    nbt.putBoolean(UNIVERSAL_TEST_TAG, true);
                    tool.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                } catch (Throwable ignored) {}
                player.giveItemStack(tool);
            }
        } catch (Throwable ignored) {}
        src.sendFeedback(() -> Text.literal("Spawned 2 rows (default, saddled): total " + total + "."), false);
        return total;
    }

    private record ConfiguredSpawn(EntityType<?> type, Consumer<Entity> config) {}

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
            ModConstants.LOGGER.info("Echo Summon horse variant [{}]: color={}, marking={}, entity={} ", stage, cs, ms, e.getUuid());
        } catch (Throwable ignored) {}
    }

    private static Object resolveEnumConstant(Class<?> enumCls, String desiredName) {
        if (enumCls == null || !enumCls.isEnum() || desiredName == null) return null;
        String normDesired = desiredName.toUpperCase(java.util.Locale.ROOT).replaceAll("[_\n\r\t -]", "");
        Object[] constants = enumCls.getEnumConstants();
        if (constants == null) return null;
        for (Object c : constants) {
            String name = ((Enum<?>) c).name();
            String norm = name.toUpperCase(java.util.Locale.ROOT).replaceAll("[_\n\r\t -]", "");
            if (norm.equals(normDesired)) return c;
        }
        // try startswith match as fallback
        for (Object c : constants) {
            String name = ((Enum<?>) c).name();
            String norm = name.toUpperCase(java.util.Locale.ROOT).replaceAll("[_\n\r\t -]", "");
            if (norm.startsWith(normDesired) || normDesired.startsWith(norm)) return c;
        }
        return null;
    }

    private static Consumer<Entity> configureHorseVariant(String colorName, String markingName) {
        return entity -> {
            try {
                if (!(entity instanceof net.minecraft.entity.passive.HorseEntity)) return;
                Class<?> horseCls = entity.getClass();
                ModConstants.LOGGER.info("Echo Summon applying horse variant request: color={}, marking={}, entity={}", colorName, markingName, entity.getUuid());

                // Discover enum classes for color/marking via getters
                Class<?> colorEnumCls = null;
                Class<?> markingEnumCls = null;
                for (var gm : horseCls.getMethods()) {
                    String gn = gm.getName().toLowerCase(java.util.Locale.ROOT);
                    if (gm.getParameterCount() == 0 && gm.getReturnType().isEnum()) {
                        if ((gn.contains("color") || gn.contains("coat")) && colorEnumCls == null) colorEnumCls = gm.getReturnType();
                        if ((gn.contains("mark") || gn.contains("pattern") || gn.contains("style")) && markingEnumCls == null) markingEnumCls = gm.getReturnType();
                    }
                }

                // 1) Try a combined setter '...set...Variant(Enum, Enum)' resolving enum constants from parameter types
                for (var m : horseCls.getMethods()) {
                    String n = m.getName().toLowerCase(java.util.Locale.ROOT);
                    if (n.contains("set") && n.contains("variant") && m.getParameterCount() == 2) {
                        Class<?> p0 = m.getParameterTypes()[0];
                        Class<?> p1 = m.getParameterTypes()[1];
                        if (p0.isEnum() && p1.isEnum()) {
                            try {
                                Object c0 = resolveEnumConstant(p0, colorName);
                                Object m1 = resolveEnumConstant(p1, markingName);
                                m.setAccessible(true);
                                m.invoke(entity, c0, m1);
                                return;
                            } catch (Throwable ignored) {}
                            try {
                                Object m0 = resolveEnumConstant(p0, markingName);
                                Object c1 = resolveEnumConstant(p1, colorName);
                                m.setAccessible(true);
                                m.invoke(entity, m0, c1);
                                return;
                            } catch (Throwable ignored) {}
                        }
                    }
                }

                // 1b) Try a single-parameter variant setter where the parameter is an enum of combined variants
                for (var m : horseCls.getMethods()) {
                    String n = m.getName().toLowerCase(java.util.Locale.ROOT);
                    if (n.contains("set") && n.contains("variant") && m.getParameterCount() == 1) {
                        Class<?> p0 = m.getParameterTypes()[0];
                        if (p0.isEnum()) {
                            // Try common combined enum naming schemes: COLOR_MARKING, MARKING_COLOR, COLORMARKING
                            String[] candidates = new String[] {
                                    colorName + "_" + markingName,
                                    markingName + "_" + colorName,
                                    (colorName + markingName).replace("_", "")
                            };
                            for (String cand : candidates) {
                                try {
                                    Object v = resolveEnumConstant(p0, cand);
                                    m.setAccessible(true);
                                    m.invoke(entity, v);
                                    return;
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }

                // 1c) Try a single-parameter variant setter with a record/class; attempt to construct via (enum, enum)
                for (var m : horseCls.getMethods()) {
                    String n = m.getName().toLowerCase(java.util.Locale.ROOT);
                    if (n.contains("set") && n.contains("variant") && m.getParameterCount() == 1) {
                        Class<?> vt = m.getParameterTypes()[0];
                        // Look for a constructor (Enum, Enum)
                        for (var ctor : vt.getDeclaredConstructors()) {
                            Class<?>[] pts = ctor.getParameterTypes();
                            if (pts.length == 2 && pts[0].isEnum() && pts[1].isEnum()) {
                                try {
                                    Object c = java.lang.Enum.valueOf((Class<? extends Enum>) pts[0], colorName);
                                    Object mk = java.lang.Enum.valueOf((Class<? extends Enum>) pts[1], markingName);
                                    ctor.setAccessible(true);
                                    Object variantObj = ctor.newInstance(c, mk);
                                    m.setAccessible(true);
                                    m.invoke(entity, variantObj);
                                    return;
                                } catch (Throwable ignored) {}
                            }
                        }
                        // Look for a static factory method 'of' or similar with (Enum, Enum)
                        for (var fm : vt.getMethods()) {
                            String fn = fm.getName().toLowerCase(java.util.Locale.ROOT);
                            if ((fn.equals("of") || fn.contains("create") || fn.contains("from")) && fm.getParameterCount() == 2) {
                                Class<?>[] pts = fm.getParameterTypes();
                                if (pts[0].isEnum() && pts[1].isEnum() && vt.isAssignableFrom(fm.getReturnType())) {
                                    try {
                                        Object c = java.lang.Enum.valueOf((Class<? extends Enum>) pts[0], colorName);
                                        Object mk = java.lang.Enum.valueOf((Class<? extends Enum>) pts[1], markingName);
                                        fm.setAccessible(true);
                                        Object variantObj = fm.invoke(null, c, mk);
                                        m.setAccessible(true);
                                        m.invoke(entity, variantObj);
                                        return;
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    }
                }

                // 2) Fall back to separate color/marking setters, resolving enum per method param type
                // Color (methods may contain 'color' or 'coat')
                for (var m : horseCls.getMethods()) {
                    String n = m.getName().toLowerCase(java.util.Locale.ROOT);
                    if (n.contains("set") && (n.contains("color") || n.contains("coat")) && m.getParameterCount() == 1) {
                        Class<?> pt = m.getParameterTypes()[0];
                        if (pt.isEnum()) {
                            try {
                                Object c = resolveEnumConstant(pt, colorName);
                                m.setAccessible(true);
                                m.invoke(entity, c);
                                break;
                            } catch (Throwable ignored) {}
                        }
                    }
                }
                // Marking (methods may contain 'mark', 'pattern', or 'style')
                for (var m : horseCls.getMethods()) {
                    String n = m.getName().toLowerCase(java.util.Locale.ROOT);
                    if (n.contains("set") && (n.contains("mark") || n.contains("pattern") || n.contains("style")) && m.getParameterCount() == 1) {
                        Class<?> pt = m.getParameterTypes()[0];
                        if (pt.isEnum()) {
                            try {
                                Object mk = resolveEnumConstant(pt, markingName);
                                m.setAccessible(true);
                                m.invoke(entity, mk);
                                break;
                            } catch (Throwable ignored) {}
                        }
                    }
                }

                // 2.5) DataTracker fallback: locate a static TrackedData field named like 'VARIANT' and set encoded value
                try {
                    if (colorEnumCls != null && markingEnumCls != null) {
                        Object cObj = resolveEnumConstant(colorEnumCls, colorName);
                        Object mObj = resolveEnumConstant(markingEnumCls, markingName);
                        if (cObj != null && mObj != null) {
                            int colorIdx = ((Enum<?>) cObj).ordinal();
                            int markIdx = ((Enum<?>) mObj).ordinal();
                            int enc = (colorIdx & 0xFF) | ((markIdx & 0xFF) << 8);

                            // get DataTracker
                            java.lang.reflect.Method getDT = null;
                            for (var m : horseCls.getMethods()) {
                                if (m.getName().equals("getDataTracker") && m.getParameterCount() == 0) { getDT = m; break; }
                            }
                            if (getDT != null) {
                                Object tracker = getDT.invoke(entity);
                                Class<?> trackedDataCls = Class.forName("net.minecraft.entity.data.TrackedData");

                                // find static field on class hierarchy named like VARIANT
                                Class<?> scan = horseCls;
                                java.lang.reflect.Field variantField = null;
                                while (scan != null && variantField == null) {
                                    for (var f : scan.getDeclaredFields()) {
                                        if (trackedDataCls.isAssignableFrom(f.getType())) {
                                            String fn = f.getName().toLowerCase(java.util.Locale.ROOT);
                                            if (fn.contains("variant")) { variantField = f; break; }
                                        }
                                    }
                                    scan = scan.getSuperclass();
                                }
                                if (variantField != null) {
                                    variantField.setAccessible(true);
                                    Object key = null;
                                    try { key = variantField.get(null); } catch (Throwable t) { /* may be instance field */ key = variantField.get(entity); }
                                    if (key != null) {
                                        // tracker.set(TrackedData, value)
                                        java.lang.reflect.Method setM = null;
                                        for (var m : tracker.getClass().getMethods()) {
                                            if (m.getName().equals("set") && m.getParameterCount() == 2) {
                                                Class<?> p0 = m.getParameterTypes()[0];
                                                if (p0.isAssignableFrom(trackedDataCls)) { setM = m; break; }
                                            }
                                        }
                                        if (setM != null) {
                                            setM.setAccessible(true);
                                            setM.invoke(tracker, key, Integer.valueOf(enc));
                                            try { ModConstants.LOGGER.info("Echo Summon applied VARIANT tracked data enc={} via field={} on {}", enc, variantField.getName(), tracker.getClass().getName()); } catch (Throwable ignored3) {}
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                // 2.6) DataTracker fallback: set separate COLOR / MARKING tracked data entries if present
                try {
                    java.lang.reflect.Method getDT = null;
                    for (var m : horseCls.getMethods()) {
                        if (m.getName().equals("getDataTracker") && m.getParameterCount() == 0) { getDT = m; break; }
                    }
                    if (getDT != null) {
                        Object tracker = getDT.invoke(entity);
                        Class<?> trackedDataCls = Class.forName("net.minecraft.entity.data.TrackedData");
                        // locate DataTracker.get/set
                        java.lang.reflect.Method dtGet = null, dtSet = null;
                        for (var m : tracker.getClass().getMethods()) {
                            if (m.getName().equals("get") && m.getParameterCount() == 1 && trackedDataCls.isAssignableFrom(m.getParameterTypes()[0])) dtGet = m;
                            if (m.getName().equals("set") && m.getParameterCount() == 2 && trackedDataCls.isAssignableFrom(m.getParameterTypes()[0])) dtSet = m;
                        }
                        if (dtGet != null && dtSet != null) {
                            Class<?> scan = horseCls;
                            java.lang.reflect.Field[] fields;
                            boolean applied = false;
                            while (scan != null) {
                                fields = scan.getDeclaredFields();
                                for (var f : fields) {
                                    if (!trackedDataCls.isAssignableFrom(f.getType())) continue;
                                    f.setAccessible(true);
                                    Object key = null;
                                    try { key = f.get(null); } catch (Throwable t) { key = f.get(entity); }
                                    if (key == null) continue;
                                    Object cur = null;
                                    try { cur = dtGet.invoke(tracker, key); } catch (Throwable ignored2) {}
                                    if (cur == null) continue;

                                    if (cur.getClass().isEnum()) {
                                        // Match by enum type rather than field name
                                        if (colorEnumCls != null && cur.getClass().isAssignableFrom(colorEnumCls)) {
                                            Object c = resolveEnumConstant(cur.getClass(), colorName);
                                            if (c != null) { dtSet.invoke(tracker, key, c); applied = true; try { ModConstants.LOGGER.info("Echo Summon applied COLOR enum via tracked data field on {}", tracker.getClass().getName()); } catch (Throwable ignored3) {} }
                                        }
                                        if (markingEnumCls != null && cur.getClass().isAssignableFrom(markingEnumCls)) {
                                            Object mk = resolveEnumConstant(cur.getClass(), markingName);
                                            if (mk != null) { dtSet.invoke(tracker, key, mk); applied = true; try { ModConstants.LOGGER.info("Echo Summon applied MARKING enum via tracked data field on {}", tracker.getClass().getName()); } catch (Throwable ignored3) {} }
                                        }
                                    } else if (cur instanceof Number num) {
                                        // numeric encoding uses ordinal
                                        if (colorEnumCls != null) {
                                            Object cObj = resolveEnumConstant(colorEnumCls, colorName);
                                            if (cObj != null) {
                                                int ord = ((Enum<?>) cObj).ordinal();
                                                Object val = (cur instanceof Byte) ? Byte.valueOf((byte) ord) : (cur instanceof Short) ? Short.valueOf((short) ord) : Integer.valueOf(ord);
                                                dtSet.invoke(tracker, key, val); applied = true; try { ModConstants.LOGGER.info("Echo Summon applied COLOR ordinal={} via tracked data field on {}", ord, tracker.getClass().getName()); } catch (Throwable ignored3) {}
                                            }
                                        }
                                        if (markingEnumCls != null) {
                                            Object mObj = resolveEnumConstant(markingEnumCls, markingName);
                                            if (mObj != null) {
                                                int ord = ((Enum<?>) mObj).ordinal();
                                                Object val = (cur instanceof Byte) ? Byte.valueOf((byte) ord) : (cur instanceof Short) ? Short.valueOf((short) ord) : Integer.valueOf(ord);
                                                dtSet.invoke(tracker, key, val); applied = true; try { ModConstants.LOGGER.info("Echo Summon applied MARKING ordinal={} via tracked data field on {}", ord, tracker.getClass().getName()); } catch (Throwable ignored3) {}
                                            }
                                        }
                                    }
                                }
                                scan = scan.getSuperclass();
                            }
                            if (applied) return;
                        }
                    }
                } catch (Throwable ignored) {}

                // 3) Final fallback: try to set via NBT by invoking readCustomDataFromNbt
                try {
                    var readNbt = horseCls.getMethod("readCustomDataFromNbt", net.minecraft.nbt.NbtCompound.class);
                    readNbt.setAccessible(true);
                    String lcColor = colorName.toLowerCase(java.util.Locale.ROOT);
                    String lcMark  = markingName.toLowerCase(java.util.Locale.ROOT);
                    net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
                    net.minecraft.nbt.NbtCompound variant = new net.minecraft.nbt.NbtCompound();
                    variant.putString("color", lcColor);
                    variant.putString("marking", lcMark);
                    nbt.put("variant", variant);
                    readNbt.invoke(entity, nbt);
                    return;
                } catch (Throwable ignored) {}

                try {
                    var readNbt = horseCls.getMethod("readCustomDataFromNbt", net.minecraft.nbt.NbtCompound.class);
                    readNbt.setAccessible(true);
                    String lcColor = colorName.toLowerCase(java.util.Locale.ROOT);
                    String lcMark  = markingName.toLowerCase(java.util.Locale.ROOT);
                    net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
                    nbt.putString("color", lcColor);
                    nbt.putString("style", lcMark);
                    readNbt.invoke(entity, nbt);
                    return;
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
        };
    }

    private static Consumer<Entity> configureChest(boolean hasChest) {
        return entity -> {
            try {
                // Donkey/Mule in 1.21.x typically expose a boolean setter for chest
                for (String methodName : new String[]{"setCarryingChest", "setHasChest"}) {
                    try {
                        var m = entity.getClass().getMethod(methodName, boolean.class);
                        m.setAccessible(true);
                        m.invoke(entity, hasChest);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Throwable ignored) {}
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
            // Apply custom configuration before equipping saddle
            if (config != null) {
                try { config.accept(entity); } catch (Throwable ignored) {}
                logHorseVariant(entity, "pre-config");
            }
            if (saddled) {
                // Mirror harness/saddle selection used in spawnEntityForType
                try {
                    Identifier typeId = EntityType.getId(living.getType());
                    if (typeId != null && typeId.equals(Identifier.of("minecraft", "happy_ghast"))) {
                        equipMountHarness(living, runId);
                    } else {
                        equipMountSaddle(living, runId);
                    }
                } catch (Throwable ignored) {
                    // Fallback to saddle on any error
                    equipMountSaddle(living, runId);
                }
            }
        }

        if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
            try { mob.setAiDisabled(true); } catch (Throwable ignored) {}
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
                        // schedule 2nd pass next tick as well
                        world.getServer().execute(() -> {
                            try { config.accept(entity); } catch (Throwable ignored) {}
                            logHorseVariant(entity, "post-spawn-2");
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
                // Equip harness for happy_ghast; otherwise equip saddle
                try {
                    Identifier typeId = EntityType.getId(living.getType());
                    if (typeId != null && typeId.equals(Identifier.of("minecraft", "happy_ghast"))) {
                        equipMountHarness(living, runId);
                    } else {
                        equipMountSaddle(living, runId);
                    }
                } catch (Throwable ignored) {
                    // Fallback to saddle if any error occurs
                    equipMountSaddle(living, runId);
                }
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

    private static void equipMountHarness(LivingEntity living, UUID runId) {
        ItemStack harness = new ItemStack(ModItems.MOUNT_HARNESS);
        markStackWithRunId(harness, runId);
        MountHarnessItem.applyEquippable(harness, living.getType());
        EquipmentSlot slot = MountHarnessItem.resolveSlot(living.getType());

        if (slot == null) {
            return;
        }

        try {
            if (living.canEquip(harness, slot)) {
                living.equipStack(slot, harness);
                if (living instanceof HarnessableMountImpl harnessable) {
                    try { harnessable.echo_summon$setHarnessed(true); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

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
