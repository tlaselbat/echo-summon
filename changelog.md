Echo Summon Changelog
======================
Date: 2025-10-14

Summary of Changes
------------------
1. Updated the saddle summon tool item asset (`src/main/resources/assets/echo_summon/items/saddle_summon_tool.json`) to the 1.21 `minecraft:select` format with namespaced custom model data selectors.
2. Adjusted the server-side custom model data handling (`src/main/java/com/tabletmc/echo_summon/net/ServerNetworking.java`) to write string keys matching the new asset cases and to remove the component when no variant applies.
3. Simplified the base model definition (`src/main/resources/assets/echo_summon/models/item/saddle_summon_tool.json`) now that predicate overrides are handled client-side via the item asset.
4. Added mule body overlay mapping in `SaddleFeatureRendererMixin` so mule summons correctly reference their equipment body layer.

Open Follow-ups
---------------
- Supply the actual mule body texture at `assets/echo_summon/textures/entity/equipment/mule_mount_body.png` (currently missing) to resolve the non-rendering overlay.
- Rebuild or reload resources in-game to validate the visual updates.
