package com.tabletmc.echo_summon.mixin.server;

import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures any mob spawned by the echo_summon test command remains stationary.
 * We re-apply AI disable and zero velocity every tick in case other systems flip state.
 */
@Mixin(MobEntity.class)
public abstract class TestSpawnAiLockMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void echo_summon$lockTestSpawn(CallbackInfo ci) {
        MobEntity self = (MobEntity)(Object)this;
        java.util.Set<String> tags;
        try {
            tags = self.getCommandTags();
        } catch (Throwable t) {
            return;
        }
        if (tags.contains("echo_summon:test_saddle_summon_tool_spawn") || tags.contains("echo_summon:test_command")) {
            try { self.setAiDisabled(true); } catch (Throwable ignored) {}
            try { self.setVelocity(net.minecraft.util.math.Vec3d.ZERO); } catch (Throwable ignored) {}
            try { self.fallDistance = 0.0f; } catch (Throwable ignored) {}
        }
    }
}
