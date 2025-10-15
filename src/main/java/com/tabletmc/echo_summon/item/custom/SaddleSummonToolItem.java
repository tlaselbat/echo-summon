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

public class SaddleSummonToolItem extends Item {
    private static final TagKey<EntityType<?>> ALLOWED_MOUNTS_TAG = TagKey.of(RegistryKeys.ENTITY_TYPE, ModConstants.Id(ModConstants.ALLOWED_MOUNTS_TAG_PATH));
    public SaddleSummonToolItem(Settings settings) {
        super(settings);
    }
    // 1.21.8: Item#use returns ActionResult instead of TypedActionResult<ItemStack>
    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        // Client-side: send action to server (C2S)
        if (world.isClient) {
            // Avoid spamming if client already has cooldown for this item stack
            if (player.getItemCooldownManager().isCoolingDown(stack)) {
                return ActionResult.PASS;
            }
            // If currently riding, allow manual dismissal of the current mount (server validates tag)
            if (player.hasVehicle()) {
                ClientPlayNetworking.send(new StringPayload("saddle_dismiss"));
                return ActionResult.SUCCESS;
            }
            // Sneak + Right-Click => release stored mount (spawn in front, remove from reins)
            if (player.isSneaking()) {
                if (hasStoredMount(stack)) {
                    ClientPlayNetworking.send(new StringPayload("saddle_release"));
                    return ActionResult.SUCCESS;
                }
                return ActionResult.PASS;
            }

            // Normal Right-Click in air => summon stored mount (mount player)
            if (!player.hasVehicle() && hasStoredMount(stack)) {
                ClientPlayNetworking.send(new StringPayload("saddle_summon"));
                return ActionResult.SUCCESS;
            }

            // Otherwise let other handlers process (e.g., useOnEntity)
            return ActionResult.PASS;
        }

        // Server receives via ServerNetworking.registerGlobalReceiver (C2S), no need to send S2C here
        return ActionResult.SUCCESS_SERVER;
    }

    // Capture when right-clicking an allowed mount entity while not riding and the summon tool is empty
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!user.getWorld().isClient) {
            // Consume interaction server-side to prevent vanilla mounting/GUI when capture would proceed
            boolean coolingDown = user.getItemCooldownManager().isCoolingDown(stack);
            boolean allowed = entity.getType().isIn(ALLOWED_MOUNTS_TAG);
            boolean empty = !hasStoredMount(stack);
            if (!coolingDown && allowed && empty) {
                return ActionResult.SUCCESS_SERVER;
            }
            return ActionResult.PASS;
        }
        if (user.getItemCooldownManager().isCoolingDown(stack)) return ActionResult.PASS;
        if (!entity.getType().isIn(ALLOWED_MOUNTS_TAG)) return ActionResult.PASS;
        if (hasStoredMount(stack)) return ActionResult.PASS;
        // Server validates anti-dupe; client just sends request with UUID
        ClientPlayNetworking.send(new StringPayload("saddle_capture:" + entity.getUuidAsString()));
        return ActionResult.SUCCESS;
    }

    private static boolean hasStoredMount(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return false;
        NbtCompound comp = custom.copyNbt();
        return comp.getCompound(ModConstants.STORED_MOUNT_KEY).map(n -> !n.isEmpty()).orElse(false);
    }
}
