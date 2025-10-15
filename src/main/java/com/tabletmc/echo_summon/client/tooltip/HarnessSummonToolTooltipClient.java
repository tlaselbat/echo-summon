package com.tabletmc.echo_summon.client.tooltip;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.util.NbtUtils;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;

public final class HarnessSummonToolTooltipClient {
    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!stack.isOf(ModItems.HARNESS_SUMMON_TOOL)) return;

            NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (custom == null) {
                lines.add(Text.translatable("item.echo_summon.harness_summon_tool.tooltip.empty"));
                return;
            }

            NbtCompound comp = custom.copyNbt();
            Optional<NbtCompound> mountOpt = comp.getCompound(ModConstants.STORED_MOUNT_KEY);
            if (mountOpt.isEmpty()) {
                lines.add(Text.translatable("item.echo_summon.harness_summon_tool.tooltip.empty"));
                return;
            }

            NbtCompound mount = mountOpt.get();
            String idStr = NbtUtils.getString(mount, "id");
            if (!idStr.isEmpty()) {
                try {
                    Identifier entId = Identifier.of(idStr);
                    EntityType<?> entType = Registries.ENTITY_TYPE.get(entId);
                    if (entType != null) {
                        lines.add(Text.translatable("item.echo_summon.harness_summon_tool.tooltip.stored", entType.getName()));
                    } else {
                        lines.add(Text.translatable("item.echo_summon.harness_summon_tool.tooltip.stored", Text.literal(entId.toString())));
                    }
                } catch (Exception e) {
                    lines.add(Text.translatable("item.echo_summon.harness_summon_tool.tooltip.stored", Text.literal(idStr)));
                }
            } else {
                lines.add(Text.translatable("item.echo_summon.harness_summon_tool.tooltip.stored", Text.literal("unknown")));
            }

            boolean isHappyGhast = comp.getBoolean(ModConstants.STORED_IS_HAPPY_GHAST_KEY).orElse(false);
            if (!isHappyGhast && !idStr.isEmpty()) {
                try {
                    Identifier eid = Identifier.of(idStr);
                    isHappyGhast = eid.equals(ModConstants.HAPPY_GHAST_ID);
                } catch (Exception ignored) {}
            }
            if (isHappyGhast) {
                lines.add(Text.translatable("item.echo_summon.harness_summon_tool.tooltip.happy_ghast"));
            }

            if (!Screen.hasShiftDown()) {
                lines.add(Text.translatable("item.echo_summon.harness_summon_tool.tooltip.shift"));
                return;
            }

            String ownerName = NbtUtils.getString(mount, "owner_name");
            if (!ownerName.isEmpty()) {
                lines.add(Text.translatable("item.echo_summon.harness_summon_tool.tooltip.owner", ownerName));
            }
            if (!idStr.isEmpty()) {
                lines.add(Text.translatable("item.echo_summon.harness_summon_tool.tooltip.id", idStr));
            }
        });
    }
}
