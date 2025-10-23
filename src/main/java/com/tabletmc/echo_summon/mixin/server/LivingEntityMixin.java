package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.item.ModItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "dropInventory(Lnet/minecraft/server/world/ServerWorld;)V", at = @At("HEAD"))
    private void echo_summon$preventMountSaddleDeathDrop(ServerWorld world, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        ItemStack saddle = self.getEquippedStack(EquipmentSlot.SADDLE);
        if (!saddle.isEmpty() && saddle.isOf(ModItems.MOUNT_SADDLE)) {
            self.equipStack(EquipmentSlot.SADDLE, ItemStack.EMPTY);
        }
    }
}
