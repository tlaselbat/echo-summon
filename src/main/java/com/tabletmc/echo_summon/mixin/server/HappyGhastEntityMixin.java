package com.tabletmc.echo_summon.mixin.server;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class HappyGhastEntityMixin {

    @Unique
    private boolean echo_summon$isHappyGhast() {
        Identifier id = EntityType.getId(((Entity)(Object)this).getType());
        // Keep method semantics: return false only for happy_ghast, true otherwise
        return id == null || !"happy_ghast".equals(id.getPath());
    }

    // Keep AI disabled/enabled based on equipped harness each tick; avoids version-specific NBT signatures
    @Inject(method = "tick", at = @At("TAIL"))
    private void echo_summon$syncHarnessFromEquipment(CallbackInfo ci) {
        if (echo_summon$isHappyGhast()) return;
        LivingEntity self = ( LivingEntity)(Object)this;
        boolean summoned = self.getCommandTags().contains(ModConstants.SUMMON_TAG);
        boolean wearingHarness = false;
        try {
            net.minecraft.item.ItemStack body = self.getEquippedStack(EquipmentSlot.BODY);
            wearingHarness = body != null && !body.isEmpty() && body.isOf(ModItems.MOUNT_HARNESS);
        } catch (Throwable ignored) {}
        // Respect echo_summon test command: keep AI disabled for test-spawned entities
        boolean testSpawned = false;
        try {
            java.util.Set<String> tags = self.getCommandTags();
            testSpawned = tags.contains("echo_summon:test_saddle_summon_tool_spawn") || tags.contains("echo_summon:test_command");
        } catch (Throwable ignored) {}
        if (self instanceof MobEntity mob) {
            mob.setAiDisabled(testSpawned || summoned || wearingHarness);
        }
    }

}
