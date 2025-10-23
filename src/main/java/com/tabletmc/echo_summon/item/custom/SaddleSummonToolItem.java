package com.tabletmc.echo_summon.item.custom;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.client.ClientCooldowns;
import com.tabletmc.echo_summon.keybinds.KeybindTickEvents;
import com.tabletmc.echo_summon.net.payload.StringPayload;
import com.tabletmc.echo_summon.net.service.SummonPersistence;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class SaddleSummonToolItem extends Item {
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
            // Sneak + Right-Click => release stored mount or dismiss if currently riding for saddle
            if (player.isSneaking()) {
                if (hasStoredMount(stack)) {
                    ClientPlayNetworking.send(new StringPayload("saddle_release"));
                    return ActionResult.SUCCESS;
                }
                return ActionResult.PASS;
            }

            // Normal Right-Click => for saddle: toggle (summon/dismiss)
            if (hasStoredMount(stack)) {
                if (player.hasVehicle()) {
                    ClientPlayNetworking.send(new StringPayload("saddle_dismiss"));
                    try { ClientCooldowns.applyCooldownToAllSummonTools((ClientPlayerEntity) player, ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored) {}
                    try { KeybindTickEvents.lockSneakFor(ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored) {}
                    return ActionResult.SUCCESS;
                }
                ClientPlayNetworking.send(new StringPayload("saddle_summon"));
                // Avoid double-starting the client GUI cooldown; rely on server sync for the indicator.
                try { KeybindTickEvents.lockSneakFor(ModConstants.SUMMON_COOLDOWN_TICKS); } catch (Throwable ignored) {}
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
            boolean allowedSaddle = ModConstants.isSaddleAllowed(entity.getType());
            boolean empty = !hasStoredMount(stack);
            if (!coolingDown && empty && allowedSaddle) {
                return ActionResult.SUCCESS_SERVER;
            }
            return ActionResult.PASS;
        }
        if (user.getItemCooldownManager().isCoolingDown(stack)) return ActionResult.PASS;
        if (hasStoredMount(stack)) return ActionResult.PASS;
        boolean isSaddle = ModConstants.isSaddleAllowed(entity.getType());
        if (!isSaddle) return ActionResult.PASS;
        // Server validates anti-dupe; client just sends request with UUID
        ClientPlayNetworking.send(new StringPayload("saddle_capture:" + entity.getUuidAsString()));
        return ActionResult.SUCCESS;
    }

    private static boolean hasStoredMount(ItemStack stack) {
        return SummonPersistence.hasStoredMount(stack);
    }

    private static NbtCompound getStoredMount(ItemStack stack) {
        return SummonPersistence.getStoredMount(stack);
    }
}
