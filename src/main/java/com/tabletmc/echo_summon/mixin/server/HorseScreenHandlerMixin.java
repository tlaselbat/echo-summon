package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.impl.MountSaddleMountImpl;
import com.tabletmc.echo_summon.impl.ServerPlayerEntityImpl;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HorseScreenHandler.class)
public abstract class HorseScreenHandlerMixin {
    @Shadow @Final private AbstractHorseEntity entity;
    /**
     * Injected method that is called when the horse screen handler is closed.
     * This method updates Echo Saddle state and stores the mount when appropriate.
     *
     * @param player the player who closed the horse screen handler
     * @param ci     the callback info
     */
    @Inject(method = "onClosed", at = @At("HEAD"))
    public void transferSlot(PlayerEntity player, CallbackInfo ci) {
        // Check if the entity is a horse
        if (entity instanceof HorseEntity horse) {
            // Update the horse's Mount Saddle state
            ((MountSaddleMountImpl) horse).updateMountSaddle();

            // Check if the player is a server player
            if (player instanceof ServerPlayerEntity serverPlayer) {
                // Check if the horse is the player's vehicle and has the Mount Saddle
                if (horse.equals(serverPlayer.getVehicle()) && ((MountSaddleMountImpl) horse).hasMountSaddle()) {
                    // Store the horse's data on the server player
                    ((ServerPlayerEntityImpl) serverPlayer).storeMount(horse);
                }
            }
        }
    }
}
