package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.MountSaddleMountImpl;
import com.tabletmc.echo_summon.impl.SaddleableMountImpl;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractHorseEntity.class)
public abstract class AbstractHorseEntityMixin implements MountSaddleMountImpl, SaddleableMountImpl {

    // The horse's jump strength
    @Shadow protected float jumpStrength;

    @Shadow protected abstract void setHorseFlag(int flag, boolean value);
    // Flag to indicate if the horse has the Mount Saddle equipped
    @Unique private boolean mountArmor = false;

    // Method to check if the horse is wearing the Mount Saddle
    @Override
    public boolean hasMountSaddle() {
        return mountArmor;
    }

    // Ensure renderer logic that queries isSaddled()/hasSaddle() returns true
    // when any SADDLE-slot equippable (e.g., Echo Saddle) is equipped.
    // Optional targets to avoid mapping differences.
    // Removed: 1.21.8 does not expose these methods on horses; rendering is driven client-side.

 
 
    // The render layer depends on a saddled flag; we maintain it in tick below.

    // Keep the horse saddled flag in sync on both sides so the saddle model renders when a valid SADDLE-slot equippable is present
    // Inject at TAIL to override vanilla's own flag update (which only recognizes the vanilla saddle item)
    @Inject(method = "tick", at = @At("TAIL"))
    private void echo_summon$tickSetSaddledFlag(CallbackInfo ci) {
        ItemStack saddleStack = ((LivingEntity) (Object) this).getEquippedStack(EquipmentSlot.SADDLE);
        EquippableComponent eq = saddleStack.get(DataComponentTypes.EQUIPPABLE);
        boolean hasSaddleEquippable = eq != null && eq.slot() == EquipmentSlot.SADDLE;
        setHorseFlag(4, hasSaddleEquippable);
    }
    // Removed: method does not exist on 1.21.8 horses; we keep client rendering mixin instead.

    /**
     * Updates the Mount Saddle flag based on the horse's current saddle slot item.
     *
     * This method checks if the horse is wearing the Mount Saddle and sets the flag accordingly.
     */
    @Override
    public void updateMountSaddle() {
        // Read from the saddle slot instead of body armor
        ItemStack saddleStack = ((LivingEntity) (Object) this).getEquippedStack(EquipmentSlot.SADDLE);
        Item current = saddleStack.getItem();

        EquippableComponent eq = saddleStack.get(DataComponentTypes.EQUIPPABLE);
        boolean isSaddleSlot = eq != null && eq.slot() == EquipmentSlot.SADDLE;
        boolean isOurSaddle;
        try {
            isOurSaddle = Registries.ITEM.getId(current).equals(ModConstants.Id("mount_saddle"));
        } catch (Throwable t) {
            isOurSaddle = false;
        }
        mountArmor = isSaddleSlot && isOurSaddle;
    }
    // NBT persistence hooks removed for 1.21.8; saddle flag is recomputed via updateMountSaddle().

    @Override
    public void echo_summon$setSaddled(boolean saddled) {
        setHorseFlag(4, saddled);
    }

    /**
     * This method is injected into the 'tickControlled' method of the AbstractHorseEntity class.
     * It is called at the head of the method, meaning it will be executed before the original code in 'tickControlled'.
     */
}
