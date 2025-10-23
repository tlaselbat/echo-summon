package com.tabletmc.echo_summon.client.tooltip;

import com.tabletmc.echo_summon.item.ModItems;
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
import com.tabletmc.echo_summon.util.NbtUtils;

public final class SaddleSummonToolTooltipClient {
    private static final String STORED_MOUNT_KEY = "echo_summon:stored_mount";

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!stack.isOf(ModItems.SUMMON_TOOL)) return;

            NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (custom == null) {
                lines.add(Text.translatable("item.echo_summon.summon_tool.tooltip.empty"));
                return;
            }

            NbtCompound comp = custom.copyNbt();
            Optional<NbtCompound> mountOpt = comp.getCompound(STORED_MOUNT_KEY);
            if (mountOpt.isEmpty()) {
                mountOpt = comp.getCompound("echo_summon_stored_horse"); // legacy
            }

            if (mountOpt.isEmpty()) {
                lines.add(Text.translatable("item.echo_summon.summon_tool.tooltip.empty"));
                return;
            }

            NbtCompound mount = mountOpt.get();
            String id = NbtUtils.getString(mount, "id");
            if (!id.isEmpty()) {
                try {
                    Identifier entId = Identifier.of(id);
                    EntityType<?> type2 = Registries.ENTITY_TYPE.get(entId);
                    if (type2 != null) {
                        lines.add(Text.translatable("item.echo_summon.summon_tool.tooltip.stored", type2.getName()))
                        ;
                    } else {
                        lines.add(Text.translatable("item.echo_summon.summon_tool.tooltip.stored", Text.literal(entId.toString())));
                    }
                } catch (Exception e) {
                    lines.add(Text.translatable("item.echo_summon.summon_tool.tooltip.stored", Text.literal(id)));
                }
            } else {
                lines.add(Text.translatable("item.echo_summon.summon_tool.tooltip.stored", Text.literal("unknown")));
            }

            if (!Screen.hasShiftDown()) {
                lines.add(Text.translatable("item.echo_summon.summon_tool.tooltip.shift"));
                return;
            }

            // SHIFT details
            mount.getFloat("Health").ifPresent(h -> {
                lines.add(Text.translatable("item.echo_summon.summon_tool.tooltip.health", String.format("%.1f", h)));
            });

            String ownerName = NbtUtils.getString(mount, "owner_name");
            if (!ownerName.isEmpty()) {
                lines.add(Text.translatable("item.echo_summon.summon_tool.tooltip.owner", ownerName));
            }

            if (!id.isEmpty()) {
                lines.add(Text.translatable("item.echo_summon.summon_tool.tooltip.id", id));
            }
        });
    }
}
