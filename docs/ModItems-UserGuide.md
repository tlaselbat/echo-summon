# Transport Plus – Mod Items User Guide

Welcome! This page explains the four Transport Plus items and how to use them. It’s written for players — short, simple, and to the point.

## Quick Start

- **Get the items**
  - Find them in the Creative inventory under the **Tools** tab.
  - All four items stack to 1 and are fireproof.

- **Capture a mount (Saddle Summon Tool)**
  - Hold the Saddle Summon Tool.
  - Right-click an allowed mount to store it in the tool.

- **Summon / Release / Dismiss (Saddle Summon Tool)**
  - Not riding + Right-click: summon the stored mount and hop on.
  - Sneak + Right-click: release the stored mount back into the world and clear the tool.
  - Riding + Right-click: dismiss a mount that was summoned by the tool.

- **Harness flow (Happy Ghast)**
  - Happy Ghast uses a **harness**, not a saddle.
  - Use the **Harness Summon Tool** to capture/summon; use **Mount Harness** to equip the ghast when needed.

## Supported Mounts

- **Saddle Summon Tool**
  - Horse, Donkey, Mule, Camel, Skeleton Horse, Zombie Horse, Happy Ghast.
  - The tool’s icon changes to match the stored mount.

- **Harness Summon Tool**
  - Happy Ghast.

## Items at a Glance

- **Saddle Summon Tool**
  - Capture one allowed mount, then summon, release, or dismiss it with right-click actions.
  - 1 second cooldown between actions to prevent spam.

- **Mount Saddle**
  - Special saddle used by vanilla saddle-slot mounts (horses, donkeys, mules, skeleton/zombie horses, camels).
  - Right-click a mount with an empty saddle slot to equip.

- **Harness Summon Tool**
  - Like the Saddle Summon Tool, but for harness-based mounts (Happy Ghast).
  - 1 second cooldown between actions.

- **Mount Harness**
  - Equippable harness for supported mounts (used with the Happy Ghast).
  - Right-click the ghast (empty body/harness slot) to equip.

## Handy Test Command (optional)

- **/test_saddle_summon_tool** (requires cheats/permission level 2)
  - Spawns the allowed mounts in a row in front of you.
  - If used without arguments and spawns succeed, you’ll also receive the same number of Saddle Summon Tools for quick testing.
  - Variants:
    - `/test_saddle_summon_tool` → Spawn allowed mounts.
    - `/test_saddle_summon_tool saddled` → Auto-equips a Mount Saddle on each spawned saddleable mount. Note: does not equip a harness on the Happy Ghast (equip a harness yourself).
    - `/test_saddle_summon_tool kill` → Attempts to remove mounts/items from previous test runs (may not remove everything).

## Tips

- **Cooldown**: Most actions use a short (1s) cooldown.
- **Horses**: Captured/summoned horses are marked tame so you can ride and see the saddle.
- **Slots**: If a mount refuses to equip, make sure its saddle or body slot is empty.

## Customize (advanced, optional)

- You can change which entities are allowed with data packs:
  - Saddle Summon Tool: `data/transport_plus/tags/entity_type/saddle_summon_tool_allowed_mounts.json`
  - Harness Summon Tool: `data/transport_plus/tags/entity_type/harness_summon_tool_allowed_mounts.json`
- The Mount Saddle is added to the vanilla `minecraft:saddles` tag.
