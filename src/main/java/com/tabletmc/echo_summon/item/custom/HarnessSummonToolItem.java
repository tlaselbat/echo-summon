package com.tabletmc.echo_summon.item.custom;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.net.payload.StringPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Summon tool dedicated to harness-based mounts (e.g., happy ghast).
 */
public class HarnessSummonToolItem extends Item {
    private static final TagKey<EntityType<?>> ALLOWED_MOUNTS_TAG =
            TagKey.of(RegistryKeys.ENTITY_TYPE, ModConstants.Id(ModConstants.HARNESS_ALLOWED_MOUNTS_TAG_PATH));

    public HarnessSummonToolItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) {
            if (player.getItemCooldownManager().isCoolingDown(stack)) {
                return ActionResult.PASS;
            }
            if (player.isSneaking()) {
                if (hasStoredMount(stack)) {
                    ClientPlayNetworking.send(new StringPayload("harness_release_or_dismiss"));
                    return ActionResult.SUCCESS;
                }
                return ActionResult.PASS;
            }
            if (hasStoredMount(stack)) {
                // Toggle between dismiss and summon server-side based on whether the stored mount is currently summoned
                ClientPlayNetworking.send(new StringPayload("harness_toggle"));
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        }
        return ActionResult.SUCCESS_SERVER;
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        boolean client = user.getWorld().isClient;
        if (user.getItemCooldownManager().isCoolingDown(stack)) {
            return ActionResult.PASS;
        }

        boolean hasStored = hasStoredMount(stack);
        ActionResult handledResult = client ? ActionResult.SUCCESS : ActionResult.SUCCESS_SERVER;

        if (user.isSneaking()) {
            if (hasStored) {
                if (client) {
                    ClientPlayNetworking.send(new StringPayload("harness_release_or_dismiss"));
                }
                return handledResult;
            }
            return ActionResult.PASS;
        }

        if (hasStored) {
            if (client) {
                ClientPlayNetworking.send(new StringPayload("harness_toggle"));
            }
            return handledResult;
        }

        if (!entity.getType().isIn(ALLOWED_MOUNTS_TAG)) {
            return ActionResult.PASS;
        }

        if (client) {
            ClientPlayNetworking.send(new StringPayload("harness_capture:" + entity.getUuidAsString()));
        }
        return handledResult;
    }

    private static boolean hasStoredMount(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return false;
        }
        NbtCompound comp = custom.copyNbt();
        return comp.getCompound(ModConstants.STORED_MOUNT_KEY).map(nbt -> !nbt.isEmpty()).orElse(false);
    }
}
