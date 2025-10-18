package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.impl.ServerPlayerEntityImpl;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerManager.class)
public class PlayerManagerMixin {
    @Inject(method="respawnPlayer", at=@At("RETURN"))
    public void respawnPlayer(ServerPlayerEntity player, boolean alive, Entity.RemovalReason removalReason, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        // Removed: do not auto-store or transfer mount state on respawn.
    }
}
