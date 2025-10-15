package com.tabletmc.echo_summon.item.custom;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.HarnessableMountImpl;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Mount Harness item responsible for equipping harness assets to supported mounts (e.g., Happy Ghast).
 */
public class MountHarnessItem extends Item {

    private static final Identifier HAPPY_GHAST_ID = Identifier.of("minecraft", "happy_ghast");
    private static final RegistryKey<EquipmentAsset> HAPPY_GHAST_HARNESS_ASSET = assetKey("entity/happy_ghast_harness/happy_ghast_harness_item");

    public MountHarnessItem(Settings settings) {
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

        if (entity instanceof HarnessableMountImpl harnessable) {
            harnessable.echo_summon$setHarnessed(true);
        } else if (entity instanceof MobEntity mob) {
            try {
                mob.setAiDisabled(true);
            } catch (Throwable ignored) {}
        }

        return ActionResult.SUCCESS;
    }

    public static void applyEquippable(ItemStack stack, EntityType<?> entityType) {
        EquippableComponent component = buildComponentFor(entityType);
        if (component != null) {
            stack.set(DataComponentTypes.EQUIPPABLE, component);
        }
    }

    private static EquippableComponent buildComponentFor(EntityType<?> entityType) {
        Identifier id = EntityType.getId(entityType);
        if (id != null && id.equals(HAPPY_GHAST_ID)) {
            return EquippableComponent.builder(EquipmentSlot.BODY)
                    .model(HAPPY_GHAST_HARNESS_ASSET)
                    .equipOnInteract(true)
                    .allowedEntities(entityType)
                    .dispensable(false)
                    .swappable(false)
                    .canBeSheared(false)
                    .build();
        }
        return null;
    }

    public static EquipmentSlot resolveSlot(EntityType<?> entityType) {
        Identifier id = EntityType.getId(entityType);
        if (id != null && id.equals(HAPPY_GHAST_ID)) {
            return EquipmentSlot.BODY;
        }
        return null;
    }

    private static RegistryKey<EquipmentAsset> assetKey(String path) {
        return RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ModConstants.Id(path));
    }
}
