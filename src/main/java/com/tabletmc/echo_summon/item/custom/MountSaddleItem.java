package com.tabletmc.echo_summon.item.custom;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.SaddleableMountImpl;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerEntity;
import java.util.Map;
import java.util.Set;

/**
 * Mount Saddle - A special saddle that links a stored mount to its saddle summon tool.
 * Stores mapping data: stored_mount_id and saddle_summon_tool_item_id in CUSTOM_DATA.
 */
public class MountSaddleItem extends Item {
    public static final String STORED_MOUNT_ID_KEY = "stored_mount_id";
    public static final String ECHO_MOUNT_SUMMON_TOOL_ID_KEY = ModConstants.SADDLE_SUMMON_TOOL_ID_KEY;

    private static final RegistryKey<EquipmentAsset> HORSE_SADDLE_ASSET = assetKey("entity/horse_saddle_item/horse_saddle_item");
    private static final RegistryKey<EquipmentAsset> DONKEY_SADDLE_ASSET = assetKey("entity/donkey_saddle_item/donkey_saddle_item");
    private static final RegistryKey<EquipmentAsset> MULE_SADDLE_ASSET = assetKey("entity/mule_saddle_item/mule_saddle_item");
    private static final RegistryKey<EquipmentAsset> SKELETON_SADDLE_ASSET = assetKey("entity/skeleton_saddle_item/skeleton_saddle_item");
    private static final RegistryKey<EquipmentAsset> ZOMBIE_SADDLE_ASSET = assetKey("entity/zombie_saddle_item/zombie_saddle_item");
    private static final RegistryKey<EquipmentAsset> CAMEL_SADDLE_ASSET = assetKey("entity/camel_saddle_item/camel_saddle_item");
    private static final Map<EntityType<?>, RegistryKey<EquipmentAsset>> SADDLE_ASSETS = Map.ofEntries(
            Map.entry(EntityType.HORSE, HORSE_SADDLE_ASSET),
            Map.entry(EntityType.DONKEY, DONKEY_SADDLE_ASSET),
            Map.entry(EntityType.MULE, MULE_SADDLE_ASSET),
            Map.entry(EntityType.SKELETON_HORSE, SKELETON_SADDLE_ASSET),
            Map.entry(EntityType.ZOMBIE_HORSE, ZOMBIE_SADDLE_ASSET),
            Map.entry(EntityType.CAMEL, CAMEL_SADDLE_ASSET)
    );

    public MountSaddleItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        EquipmentSlot slot = resolveSlot(entity.getType());
        if (slot == null) {
            return ActionResult.PASS;
        }

        ItemStack equipped = entity.getEquippedStack(slot);
        if (!equipped.isEmpty()) {
            return ActionResult.PASS;
        }

        if (user.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }

        if (stack.isEmpty()) {
            return ActionResult.PASS;
        }

        ItemStack candidate = stack.copy();
        candidate.setCount(1);
        applyEquippable(candidate, entity.getType());

        if (!entity.canEquip(candidate, slot)) {
            return ActionResult.PASS;
        }

        entity.equipStack(slot, candidate);
        stack.decrement(1);

        if (entity instanceof SaddleableMountImpl saddleable) {
            saddleable.echo_summon$setSaddled(true);
        }

        return ActionResult.SUCCESS;
    }

    public static void applyEquippable(ItemStack stack, EntityType<?> entityType) {
        EquippableComponent component = buildComponentFor(entityType);
        if (component != null) {
            stack.set(DataComponentTypes.EQUIPPABLE, component);
        }
        try {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        } catch (Throwable ignored) {
        }
    }

    private static EquippableComponent buildComponentFor(EntityType<?> entityType) {
        RegistryKey<EquipmentAsset> assetKey = SADDLE_ASSETS.getOrDefault(entityType, HORSE_SADDLE_ASSET);
        return buildSaddleComponent(entityType, assetKey);
    }

    private static EquippableComponent buildSaddleComponent(EntityType<?> entityType, RegistryKey<EquipmentAsset> assetKey) {
        return EquippableComponent.builder(EquipmentSlot.SADDLE)
                .model(assetKey)
                .allowedEntities(entityType)
                .equipOnInteract(true)
                .canBeSheared(false)
                .build();
    }

    private static EquippableComponent buildBodyComponent(RegistryKey<EquipmentAsset> assetKey,
                                                          EntityType<?>... allowedEntities) {
        return EquippableComponent.builder(EquipmentSlot.BODY)
                .model(assetKey)
                .allowedEntities(allowedEntities)
                .equipOnInteract(true)
                .canBeSheared(false)
                .build();
    }

    private static RegistryKey<EquipmentAsset> assetKey(String path) {
        return RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ModConstants.Id(path));
    }

    public static EquipmentSlot resolveSlot(EntityType<?> entityType) {
        if (SADDLE_ASSETS.containsKey(entityType) ||
            entityType == EntityType.HORSE ||
            entityType == EntityType.DONKEY ||
            entityType == EntityType.MULE ||
            entityType == EntityType.SKELETON_HORSE ||
            entityType == EntityType.ZOMBIE_HORSE ||
            entityType == EntityType.CAMEL) {
            return EquipmentSlot.SADDLE;
        }
        return null;
    }
}
