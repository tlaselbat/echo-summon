# Transport Plus: Developer Overview

## High-Level Concept

Transport Plus revamps mount handling so players can store real entities inside items, summon them on demand, and keep their equipment synchronized between summons. The core gameplay loop revolves around the `saddle_summon_tool` and `mount_saddle` items working together to capture, summon, and release supported mounts.

## Gameplay Flow

- **Capture**
  - Player right-clicks an allowed mount with `echo_summon:saddle_summon_tool` (`SaddleSummonToolItem`) while sneaking.
  - Server serializes the entity NBT, links a dedicated `mount_saddle`, and removes the entity from the world (`ServerNetworking.java`).
- **Summon**
  - Player right-clicks the summon tool in air to rematerialize the stored mount at their location and automatically mount it.
- **Dismiss**
  - While mounted on a summoned creature, right-clicking the tool dismisses the mount safely.
- **Release**
  - Sneak + use in air recreates the stored entity in the world and hands its `mount_saddle` back to the player.

Cooldown (`ModConstants.SUMMON_COOLDOWN_TICKS`) prevents spamming across all actions.

## Item Catalog (`ModItems.java`)

- **Saddle Summon Tool** (`echo_summon:saddle_summon_tool`)
  - Custom logic lives in `SaddleSummonToolItem`.
  - Stores a single mount's NBT inside `DataComponentTypes.CUSTOM_DATA` using key `echo_summon:stored_mount`.
  - Sets `CustomModelData` values (100–106) for model variants per mount type.
- **Mount Saddle** (`echo_summon:mount_saddle`)
  - Custom equippable (`MountSaddleItem`) that auto-applies mount-specific `EquipmentAsset` definitions so saddles render correctly.
  - Links back to its parent summon tool via `echo_summon:saddle_summon_tool_item_id`.
- **Harness Summon Tool** (`echo_summon:harness_summon_tool`)
  - Currently a placeholder `Item`. Shares registry pattern with the saddle tool and reserved for future harness capture features.
- **Mount Harness** (`echo_summon:mount_harness`)
  - Placeholder equippable to pair with the harness summon tool in future iterations. No custom behavior yet.

All four items are added to the vanilla TOOLS creative tab in the order saddle → saddle summon tool → harness summon tool → mount harness.

## Detailed Item Behavior

- **Saddle Summon Tool** (`SaddleSummonToolItem` in `src/main/java/com/tabletmc/echo_summon/item/custom/SaddleSummonToolItem.java`)
  - Actions (client-side sends `StringPayload`, server validates/executes in `net/ServerNetworking.java`):
    - Use on entity: if target entity’s type is in `echo_summon:saddle_summon_tool_allowed_mounts`, the tool has no stored mount, and not on cooldown → send `capture:<uuid>`.
    - Use in air:
      - If riding a summoned mount → `dismiss`.
      - If sneaking and a mount is stored → `release` (spawn in world, return the `mount_saddle`).
      - If not riding and a mount is stored → `summon` (spawn at player and auto-mount).
  - Server validation/processing (`ServerNetworking.init()`):
    - Verifies the tool in hand is the summon tool; checks allowed entity tag; rejects capturing entities already tagged with `echo_summon:saddle_summon_tool_summoned`.
    - On capture:
      - Persists full entity NBT to the item `CUSTOM_DATA` under key `echo_summon:stored_mount`.
      - Ensures and records a stable tool ID under `saddle_summon_tool_item_id` for linkage.
      - Creates a `mount_saddle` pre-configured for the entity (see `MountSaddleItem.applyEquippable()`), stores `mount_type` and linkage in `mount_saddle_data`, and equips it to the entity before serialization.
      - Removes entity from world and applies cooldown (`ModConstants.SUMMON_COOLDOWN_TICKS`).
    - On summon:
      - Loads entity from stored NBT; validates against allowed tag; auto-tames horses for vanilla behavior.
      - Sets up spawn at the player's position with the player's yaw/pitch, and copies the player's velocity and fall distance to the mount.
      - Applies the command tag `echo_summon:saddle_summon_tool_summoned` and equips a `mount_saddle` reconstructed from `mount_saddle_data` (re-applies next tick if needed).
      - Calls `player.startRiding(mount, true)` to force immediate mounting (teleports the player onto the mount even if slightly obstructed).
      - Applies cooldown.
    - On dismiss:
      - Works only if the player is currently riding and the mount has the `echo_summon:saddle_summon_tool_summoned` tag (i.e., it was summoned by the tool).
      - Despawns the mount safely and applies cooldown.
      - Leaves the summon tool's stored data unchanged.
    - On release:
      - Spawns the stored entity in front of the player using a raycast-driven safe position finder.
      - Transfers the equipped `mount_saddle` (or a reconstructed one from stored mapping data) to the player’s inventory.
      - Clears `echo_summon:stored_mount`, removes `CustomModelData`, deletes any inventory `mount_saddle` items linked to the tool’s `saddle_summon_tool_item_id`, then applies cooldown.
  - Data components and keys:
    - `DataComponentTypes.CUSTOM_DATA` → `echo_summon:stored_mount` (entity NBT), `saddle_summon_tool_item_id` (stable UUID-like string), and nested `mount_saddle_data`.
    - `DataComponentTypes.CUSTOM_MODEL_DATA` → integer variant per entity type for model overrides (100 = default, 101..106 mapped for horse-like types).
  - Edge cases and guards:
    - Missing `mount_type` in legacy data is auto-healed on summon/release.
    - Capturing rejects entities not in the allowed tag or already marked as summoned.
    - Client checks local cooldown to avoid spamming; server enforces the authoritative cooldown.

- **Mount Saddle** (`MountSaddleItem` in `src/main/java/com/tabletmc/echo_summon/item/custom/MountSaddleItem.java`)
  - Use on entity (server-side):
    - Resolves the correct `EquipmentSlot` via `resolveSlot()`; equips only if slot is empty and entity can equip.
    - Applies `EquippableComponent` via `applyEquippable()` using the appropriate `EquipmentAsset` model for the entity type.
    - For saddle-slot entities, toggles `SaddleableMountImpl.echo_summon$setSaddled(true)` after equip.
  - Equipment assets:
    - Uses registry keys under `assets/echo_summon/equipment/...` to render saddle layers per supported entities (e.g., horse, donkey, mule, camel, skeleton/zombie horse).
  - Tags and integration:
    - Included in `data/minecraft/tags/items/saddles.json` so vanilla logic treats it as a saddle.
  - Interop with the summon tool:
    - `mount_saddle_data` persists linkage (`saddle_summon_tool_item_id`) and `mount_type`. On release, the saddle is returned to the player.

- **Harness Summon Tool** (`harness_summon_tool`) and **Mount Harness** (`mount_harness`)
  - Current status: placeholder items registered in `ModItems` and placed after `mount_saddle` in the TOOLS group.
  - Planned direction: mirror the saddle flow with harness-specific capture logic, equipment assets, and UI affordances.

- **Cooldown and UX**
  - Server applies cooldown via `player.getItemCooldownManager().set(stack, ModConstants.SUMMON_COOLDOWN_TICKS)`.
  - Client consults the cooldown manager to short-circuit repeated actions.
  - `client/tooltip/SaddleSummonToolTooltipClient` provides contextual tooltip hints for stored state (expand as needed).

## Networking Pipeline (`net/`)

- **Payload**
  - Client sends `StringPayload` values (`capture:<uuid>`, `summon`, `dismiss`, `release`).
- **Server Handler** (`ServerNetworking.init()`)
  - Validates player state, mount tags, and cooldowns.
  - Persists or restores entity data, equips saddles, and manipulates inventories.
  - Ensures summoned mounts carry the `echo_summon:saddle_summon_tool_summoned` command tag for cleanup logic.
- **Client Hooks**
  - `ClientNetworking.init()` registers C2S payload codec and leaves S2C open for future use.
  - Tooltip overlays (`SaddleSummonToolTooltipClient`) and keybind support live under `client/`.

## Commands & Utilities

- **`/test_saddle_summon_tool`** (`SpawnAllowedMountsCommand`)
  - QA helper driven by `SpawnConfigService` using the default `SpawnCommandConfig` entries in `config/`.
  - Spawns the configured entity list (default mirrors legacy selection) and applies optional chest/variant configuration per entry.
  - Subcommands: `kill` (despawn test entities/items), `saddled` (spawn pre-equipped mounts).
  - Annotates generated items so cleanup routines can track them.
  - Controlled via `EchoSummonConfig.disableTestCommandMountAI`; when `false` (default) mounts retain normal AI for interactive testing.

## Regression Checklist

- **Capture/Summon Flow**
  - `/give @s echo_summon:saddle_summon_tool` and capture a tagged mount.
  - Verify stored mount metadata (`/data get entity`).
  - Summon, dismiss, and release to confirm cooldowns and saddle return.
- **Harness Flow**
  - Use the harness tool to capture and summon, ensuring harness data persists and dismiss works.
- **Spawn Command**
  - `/echo_summon test` and `/echo_summon test saddled` spawn configured entities per `SpawnCommandConfig`.
  - `/echo_summon kill` removes all tagged mounts/items.
- **Item Cleanup**
  - Ensure tagged run items (`echo_summon:test_command`) are removed from inventories and world drops after kill.

## Tags & Data (`src/main/resources/data/`)

- **Allowed Mounts**
  - `echo_summon:saddle_summon_tool_allowed_mounts` (entity type tag) defines which entities can be captured.
- **Saddle Tag**
  - `data/minecraft/tags/items/saddles.json` includes `echo_summon:mount_saddle` so vanilla logic treats it like a saddle.

## Asset & Rendering Notes

- Item asset pipeline follows the 1.21+ flow documented in `docs/ItemAssets-1.21.md`.
- Mount saddles leverage equipment asset JSONs under `assets/echo_summon/equipment/` to swap mount body/saddle layers.
- Texture naming conventions match base filenames (see project memory about resolving equipment textures).

## Extension Points

- **Harness System**
  - Future work: implement capture logic mirroring saddles, define harness-specific equipment assets, and update networking to support dual storage slots.
- **Custom Models**
  - Replace runtime `CustomModelData` overrides with item asset predicates once assets are authored (see TODO in `ClientInitializer`).
- **Data-Driven Mount Lists**
  - Expand `echo_summon:saddle_summon_tool_allowed_mounts` to include modded mounts for compatibility.

## Initialization Order (`ModInitializer.java`)

- Registers items, networking, and testing commands on the server entry point.
- Client initializer registers networking, keybinds, tooltips, and rendering features.

Keep this document updated as new items or systems (e.g., harness features) gain functionality.
