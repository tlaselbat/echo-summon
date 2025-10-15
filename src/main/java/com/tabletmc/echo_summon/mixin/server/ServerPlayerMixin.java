package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.MountSaddleMountImpl;
import com.tabletmc.echo_summon.impl.ServerPlayerEntityImpl;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.net.ServerNetworking;
import com.tabletmc.echo_summon.util.NbtUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerMixin implements ServerPlayerEntityImpl {

    @Shadow public abstract void sendMessage(Text message, boolean actionBar);

    @Unique private AnimalEntity storedHorse;
    @Unique private boolean tpWasRiding = false;
    @Unique private Entity tpLastVehicle = null;

    @Override
    public void summonMount(boolean mountPlayer) {
        if (storedHorse == null) {
            sendMessage(Text.of("No Horse Found!"), true);
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        storedHorse.fallDistance = player.fallDistance;
        storedHorse.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());

        if (mountPlayer) {
            player.startRiding(storedHorse, true);
        }

        storedHorse.setVelocity(player.getVelocity());
        player.getWorld().spawnEntity(storedHorse);

        if (!mountPlayer) {
            storedHorse.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, false, false));
        }
    }

    @Override
    public void dismountHorse(boolean mountPlayer) {
        if (storedHorse == null) {
            return; // No horse to dismount
        }
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Remove the horse from the player's riding entity
        if (player.getRootVehicle() != null) {
            player.stopRiding();
            player.getRootVehicle().dismountVehicle();
        }
    }

    @Override
    public void storeMount(AnimalEntity mount) {
        if (storedHorse != null && !storedHorse.getUuid().equals(mount.getUuid())) {
            sendMessage(Text.of("[echo_summon]: Replaced Old Horse"), false);
            summonMount(false);
        }

        if (mount.getRemovalReason() != null) {
            storedHorse = null;
        } else {
            storedHorse = mount;
        }
    }

    /**
     * Injects a method that is called when the player starts riding an entity.
     *
     * @param entity The entity that the player is riding.
     * @param force  Whether the player is forced to start riding.
     * @param cir    The callback info for the method injection.
     */
    @Inject(method = "startRiding", at = @At("TAIL"), require = 0)
    public void startRiding(Entity entity, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof AnimalEntity horse && ((MountSaddleMountImpl) horse).hasMountSaddle()) {
            storeMount(horse);
        }
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void echo_summon$preventSaddleDrop(boolean dropEntireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ItemStack selected = player.getMainHandStack();
        if (echo_summon$isMountSaddle(selected)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void echo_summon$preventSaddleDrop(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (echo_summon$isMountSaddle(stack)) {
            cir.setReturnValue(null);
            cir.cancel();
        }
    }

    // stopRiding is now handled in EntityMixin to avoid descriptor issues on ServerPlayerEntity.

    // Replace stopRiding hook with a stable tick-based transition detector to avoid recursion
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void echo_summon$afterTick(CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        boolean nowRiding = self.hasVehicle();
        Entity currentVehicle = nowRiding ? self.getVehicle() : null;

        if (nowRiding) {
            // Vehicle changed while still riding: dismiss previous saddle summon if present
            if (tpLastVehicle instanceof LivingEntity previousLiving && previousLiving != currentVehicle) {
                ServerNetworking.handleSaddleAutoDismiss(self, previousLiving);
            }
            tpLastVehicle = currentVehicle;
        } else if (tpWasRiding) {
            // Fully dismounted from vehicle: dismiss prior saddle summon if present
            if (tpLastVehicle instanceof LivingEntity living) {
                ServerNetworking.handleSaddleAutoDismiss(self, living);
            }
            tpLastVehicle = null;
        }

        tpWasRiding = nowRiding;
    }

    @Unique
    private static boolean echo_summon$isMountSaddle(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(ModItems.MOUNT_SADDLE)) {
            return false;
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return false;
        }
        NbtCompound data = custom.copyNbt();
        String storedId = NbtUtils.getString(data, ModConstants.STORED_MOUNT_ID_KEY);
        return !storedId.isEmpty();
    }
}
