Echo Summon Changelog
======================
Date: 2025-10-15

Summary of Changes
------------------
1. Locked the Mount Saddle to its mount when closing the horse screen by purging extracted saddles, preserving their NBT, and re-equipping the mount in `src/main/java/com/tabletmc/echo_summon/mixin/server/HorseScreenHandlerMixin.java`.
2. Prevented removing the Mount Saddle with shears by setting `.canBeSheared(false)` for both equippable components in `src/main/java/com/tabletmc/echo_summon/item/custom/MountSaddleItem.java`.
3. Ensured released mounts keep their bound saddle by reconstructing or sanitizing it in-place within `src/main/java/com/tabletmc/echo_summon/net/ServerNetworking.java`.
4. Persisted donkey and mule chest inventories when auto-dismissed, explicitly dismissed, or released by syncing live mount NBT back into the saddle summon tool in `src/main/java/com/tabletmc/echo_summon/net/ServerNetworking.java` and `src/main/java/com/tabletmc/echo_summon/mixin/server/ServerPlayerMixin.java`.
5. Reset saddle summon tool metadata and cleared summon tags during release so the tool can capture new mounts immediately while keeping custom model data accurate in `src/main/java/com/tabletmc/echo_summon/net/ServerNetworking.java`.

Open Follow-ups
---------------
- **[Automated coverage]** Add an integration or gameplay test that exercises capture → summon → modify inventory → dismiss/release to guard against regressions.
  - **[UX polish]** Evaluate whether the summon tool should provide tooltip feedback when it no longer contains a stored mount after release.

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
