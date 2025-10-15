package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.impl.HarnessableMountImpl;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.item.custom.MountHarnessItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class HappyGhastEntityMixin implements HarnessableMountImpl {

    @Unique
    private boolean echo_summon$harnessed;

    @Unique
    private boolean echo_summon$isHappyGhast() {
        Identifier id = EntityType.getId(((Entity)(Object)this).getType());
        return id != null && id.equals(Identifier.of("minecraft","happy_ghast"));
    }

    @Override
    public void echo_summon$setHarnessed(boolean harnessed) {
        if (!echo_summon$isHappyGhast()) return;
        this.echo_summon$harnessed = harnessed;
        if ((Object)this instanceof MobEntity mob) {
            mob.setAiDisabled(harnessed);
        }
    }

    // Keep AI disabled/enabled based on equipped harness each tick; avoids version-specific NBT signatures
    @Inject(method = "tick", at = @At("TAIL"))
    private void echo_summon$syncHarnessFromEquipment(CallbackInfo ci) {
        if (!echo_summon$isHappyGhast()) return;
        LivingEntity self = (LivingEntity)(Object)this;
        EquipmentSlot slot = MountHarnessItem.resolveSlot(self.getType());
        if (slot == null) return;
        ItemStack equipped = self.getEquippedStack(slot);
        if (!equipped.isEmpty() && equipped.isOf(ModItems.MOUNT_HARNESS)) {
            EquippableComponent eq = equipped.get(DataComponentTypes.EQUIPPABLE);
            boolean correctSlot = eq != null && eq.slot() == slot;
            if (correctSlot && !echo_summon$harnessed) {
                echo_summon$setHarnessed(true);
            }
        } else {
            if (echo_summon$harnessed) {
                echo_summon$setHarnessed(false);
            }
        }
    }
}
