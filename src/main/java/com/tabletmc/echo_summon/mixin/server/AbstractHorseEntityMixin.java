package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.impl.MountSaddleMountImpl;
import com.tabletmc.echo_summon.impl.SaddleableMountImpl;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractHorseEntity.class)
public abstract class AbstractHorseEntityMixin implements MountSaddleMountImpl, SaddleableMountImpl {

    // Flag to indicate if the horse has double jumped
    @Unique private boolean doubleJumped = false;

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
    @Inject(method = "tickControlled", at = @At("HEAD"))
    private void tickControlled(CallbackInfo ci) {
        // Cast the current object to an AbstractHorseEntity, which is the class being mixed into.
        AbstractHorseEntity ahe = (AbstractHorseEntity)(Object)this;

        // Check if the horse has a player rider and if it has the Mount Saddle equipped.
        // This is the condition under which the special jumping behavior will be triggered.
        if (ahe.hasPlayerRider() && hasMountSaddle()) {
            // If the horse is on the ground and not in the air, reset the doubleJumped flag.
            // This is necessary to allow the horse to jump again after landing.
            if (ahe.isOnGround()) {
                doubleJumped = false;
            }

            // Check if the horse has not already double jumped, and if it has enough jump strength.
            // Also check if the horse is in the air and not on the ground.
            // If all these conditions are true, the horse will perform a special jump.
            if (!doubleJumped && jumpStrength > 0.0F && !ahe.isOnGround())  {
                // Set the horse's velocity to its current velocity, but with the y-component modified by the jump boost velocity modifier.
                // This gives the horse an upward boost.
                double jumpAttr = ahe.getAttributeValue(EntityAttributes.JUMP_STRENGTH);
                float yBoost = Math.max(0.1F, (float) jumpAttr * 0.1F);
                ahe.setVelocity(ahe.getVelocity().x, ahe.getVelocity().y + yBoost , ahe.getVelocity().z);

                // Calculate the horizontal components of the horse's velocity based on its yaw (rotation around the y-axis).
                // These components will be used to give the horse a horizontal boost.
                float h = MathHelper.sin(ahe.getYaw() * 0.017453292F);
                float i = MathHelper.cos(ahe.getYaw() * 0.017453292F);

                // Add the horizontal boost to the horse's velocity.
                // The boost is proportional to the jump strength and the horizontal components calculated above.
                ahe.setVelocity(ahe.getVelocity().add(-0.4F * h * jumpStrength, 0.1F, 0.4F * i * jumpStrength));

                // Set the doubleJumped flag to true, to prevent the horse from jumping again until it lands.
                doubleJumped = true;
            }
        }
    }
}
