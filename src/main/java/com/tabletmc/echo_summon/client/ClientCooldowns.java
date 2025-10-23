package com.tabletmc.echo_summon.client;

import com.tabletmc.echo_summon.item.custom.SaddleSummonToolItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

public final class ClientCooldowns {
    private ClientCooldowns() {}

    public static void applyCooldownToAllSummonTools(ClientPlayerEntity player, int ticks) {
        if (player == null) return;
        try {
            var inv = player.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s.getItem() instanceof SaddleSummonToolItem) {
                    player.getItemCooldownManager().set(s, ticks);
                }
            }
            // Offhand
            ItemStack off = player.getOffHandStack();
            if (off.getItem() instanceof SaddleSummonToolItem) {
                player.getItemCooldownManager().set(off, ticks);
            }
            // Main hand
            ItemStack main = player.getMainHandStack();
            if (main.getItem() instanceof SaddleSummonToolItem) {
                player.getItemCooldownManager().set(main, ticks);
            }
        } catch (Throwable ignored) {}
    }
}
