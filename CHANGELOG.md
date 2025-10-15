Echo Summon Changelog
======================
Date: 2025-10-14

Summary of Changes
------------------
1. Updated the saddle summon tool item asset (`src/main/resources/assets/echo_summon/items/saddle_summon_tool.json`) to the 1.21 `minecraft:select` format with namespaced custom model data selectors.
2. Adjusted the server-side custom model data handling (`src/main/java/com/tabletmc/echo_summon/net/ServerNetworking.java`) to write string keys matching the new asset cases and to remove the component when no variant applies.
3. Simplified the base model definition (`src/main/resources/assets/echo_summon/models/item/saddle_summon_tool.json`) now that predicate overrides are handled client-side via the item asset.
4. Added mule body overlay mapping in `SaddleFeatureRendererMixin` so mule summons correctly reference their equipment body layer.
5. Fixed Mount Saddle duplication: when closing the horse UI, purge any Mount Saddle linked to the current mount from handler slots, the cursor stack, and the player's inventory. File: `src/main/java/com/tabletmc/echo_summon/mixin/server/HorseScreenHandlerMixin.java`.
6. Prevented dropping Mount Saddles: cancel both `dropSelectedItem` (boolean return) and `dropItem(ItemStack, ...)` when the item is a Mount Saddle carrying `stored_mount_id`. Also corrected the inject target and switched to `getMainHandStack()` to avoid private field access. File: `src/main/java/com/tabletmc/echo_summon/mixin/server/ServerPlayerMixin.java`.
7. Added a permanent, curse-like glint to the Mount Saddle by setting `DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE` inside `MountSaddleItem.applyEquippable()`. File: `src/main/java/com/tabletmc/echo_summon/item/custom/MountSaddleItem.java`.
8. Implemented harness summon auto-dismiss for Happy Ghast: while the player is holding the harness summon tool, the summoned ghast is automatically dismissed if the player is more than 20 blocks away or changes dimension. Added helpers in `ServerNetworking` and a player tick hook in `ServerPlayerMixin` to locate and dismiss the linked ghast. Files: `src/main/java/com/tabletmc/echo_summon/net/ServerNetworking.java`, `src/main/java/com/tabletmc/echo_summon/mixin/server/ServerPlayerMixin.java`.

Open Follow-ups
---------------
- Supply the actual mule body texture at `assets/echo_summon/textures/entity/equipment/mule_mount_body.png` (currently missing) to resolve the non-rendering overlay.
- Rebuild or reload resources in-game to validate the visual updates.
- Validate `DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE` support for the target runtime/mappings; glint application is wrapped in a try/catch and will no-op if unavailable.
