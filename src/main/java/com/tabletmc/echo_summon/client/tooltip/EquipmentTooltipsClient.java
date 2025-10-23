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

public final class EquipmentTooltipsClient {
    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.isOf(ModItems.MOUNT_SADDLE)) {
                lines.add(Text.translatable("item.echo_summon.mount_saddle.tooltip.basic"));
                NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
                if (custom == null) return;
                if (!Screen.hasShiftDown()) return;
                NbtCompound data = custom.copyNbt();

                String mType = NbtUtils.getString(data, "mount_type");
                if (!mType.isEmpty()) {
                    try {
                        Identifier id = Identifier.of(mType);
                        EntityType<?> et = Registries.ENTITY_TYPE.get(id);
                        if (et != null) {
                            lines.add(Text.translatable("item.echo_summon.mount_saddle.tooltip.mount_type", et.getName()));
                        } else {
                            lines.add(Text.translatable("item.echo_summon.mount_saddle.tooltip.mount_type", Text.literal(mType)));
                        }
                    } catch (Exception e) {
                        lines.add(Text.translatable("item.echo_summon.mount_saddle.tooltip.mount_type", Text.literal(mType)));
                    }
                }

                String toolId = NbtUtils.getString(data, ModConstants.SADDLE_SUMMON_TOOL_ID_KEY);
                if (!toolId.isEmpty()) {
                    lines.add(Text.translatable("item.echo_summon.mount_saddle.tooltip.linked_tool"));
                }
            }
        });
    }
    
}
