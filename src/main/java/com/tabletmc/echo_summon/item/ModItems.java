package com.tabletmc.echo_summon.item;

import com.tabletmc.echo_summon.ModConstants;
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
 
/**
 * Registers all mod items (tool and equipment) and exposes helpers to add them to item groups.
 */
public class ModItems  {

    public static final Item SUMMON_TOOL = registerModItems("summon_tool",
            new SaddleSummonToolItem(
                    new Item.Settings()
                            .registryKey(RegistryKey.of(RegistryKeys.ITEM, ModConstants.Id("summon_tool")))
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
        entries.add(SUMMON_TOOL);
        // Add the Mount Saddle item to the item group
        entries.addAfter(Items.SADDLE, MOUNT_SADDLE);
        // Harness summon tool obsolete: do not list
        // Still list Mount Harness for testing/utility
        entries.addAfter(MOUNT_SADDLE, MOUNT_HARNESS);
    }

    /**
     * Register a mod item into the global registry with the mod's identifier.
     */
    private static Item registerModItems(String itemName, Item item) {
        // Register the item with the game's registry
        return Registry.register(Registries.ITEM, ModConstants.Id(itemName), item);
    }

    /**
     * Hook called during mod init to register items into the vanilla Tools item group.
     */
    public static void registerModItems() {
        // Modify the TOOLS item group to include our mod's items
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(ModItems::addItemsToItemGroup);
    }
}