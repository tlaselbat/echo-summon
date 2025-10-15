package com.tabletmc.echo_summon.item;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.item.custom.HarnessSummonToolItem;
import com.tabletmc.echo_summon.item.custom.MountHarnessItem;
import com.tabletmc.echo_summon.item.custom.MountSaddleItem;
import com.tabletmc.echo_summon.item.custom.SaddleSummonToolItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public class ModItems  {

    //TODO: add trinket item for horse storage

    public static final Item SADDLE_SUMMON_TOOL = registerModItems("saddle_summon_tool",
            new SaddleSummonToolItem(
                    new Item.Settings()
                            .registryKey(RegistryKey.of(RegistryKeys.ITEM, ModConstants.Id("saddle_summon_tool")))
                            .maxCount(1)
                            .fireproof()
            )
    );
    public static final Item MOUNT_SADDLE = registerModItems("mount_saddle",
            new MountSaddleItem(
                    new Item.Settings()
                            .registryKey(RegistryKey.of(RegistryKeys.ITEM, ModConstants.Id("mount_saddle")))
                            .maxCount(1)
                            .fireproof()
            )
    );
    public static final Item HARNESS_SUMMON_TOOL = registerModItems("harness_summon_tool",
            new HarnessSummonToolItem(
                    new Item.Settings()
                            .registryKey(RegistryKey.of(RegistryKeys.ITEM, ModConstants.Id("harness_summon_tool")))
                            .maxCount(1)
                            .fireproof()
            )
    );
    public static final Item MOUNT_HARNESS = registerModItems("mount_harness",
            new MountHarnessItem(
                    new Item.Settings()
                            .registryKey(RegistryKey.of(RegistryKeys.ITEM, ModConstants.Id("mount_harness")))
                            .maxCount(1)
                            .fireproof()
            )
    );

    private static void addItemsToItemGroup(FabricItemGroupEntries entries) {

        // Add the Saddle Summon Tool to the item group
        entries.add(SADDLE_SUMMON_TOOL);
        // Add the Mount Saddle item to the item group
        entries.addAfter(Items.SADDLE, MOUNT_SADDLE);
        entries.addAfter(MOUNT_SADDLE, HARNESS_SUMMON_TOOL);
        entries.addAfter(HARNESS_SUMMON_TOOL, MOUNT_HARNESS);
    }

    private static Item registerModItems(String itemName, Item item) {
        // Register the item with the game's registry
        return Registry.register(Registries.ITEM, ModConstants.Id(itemName), item);
    }

    public static void registerModItems() {
        // Modify the TOOLS item group to include our mod's items
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(ModItems::addItemsToItemGroup);
    }
}