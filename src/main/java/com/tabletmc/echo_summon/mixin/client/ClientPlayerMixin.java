package com.tabletmc.echo_summon.mixin.client;

import com.tabletmc.echo_summon.impl.MountSaddleMountImpl;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerMixin {
    @Inject(method = "startRiding", at = @At("HEAD"))
    public void startRiding(Entity entity, boolean force, CallbackInfoReturnable<Boolean> cir) {
        // Only run armor update on entities that actually implement our interface
        if (entity instanceof MountSaddleMountImpl armor) {
            armor.updateMountSaddle();
        }
    }
}
