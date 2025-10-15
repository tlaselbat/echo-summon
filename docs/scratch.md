
## Gameplay Flow

- **Capture**
    - Player right-clicks an allowed mount with `echo_mounts:harness_summon_tool` (`HarnessSummonToolItem`) while sneaking.
    - Server serializes the entity NBT, links a dedicated `mount_harness`, and removes the entity from the world (`ServerNetworking.java`).
- **Summon**
    - Player right-clicks the summon tool in air to rematerialize the stored mount at their location and automatically mount it.
- **Dismiss**
    - While a mount is summoned, right-clicking the tool dismisses the mount safely.
- **Release**
    - Sneak + use in air recreates the stored entity in the world and hands its `mount_harness` back to the player.

Cooldown (`ModConstants.SUMMON_COOLDOWN_TICKS`) prevents spamming across all actions.

## Item Catalog (`ModItems.java`)

- **Harness Summon Tool** (`echo_mounts:harness_summon_tool`)
    - Custom logic lives in `HarnessSummonToolItem`.
    - Stores a single mount's NBT inside `DataComponentTypes.CUSTOM_DATA` using key `echo_mounts:stored_mount`.
    - Sets `CustomModelData` values (100–106) for model variants per mount type.
- **Mount Harness** (`echo_mounts:mount_harness`)
    - Custom equippable (`MountHarnessItem`) that auto-applies mount-specific `EquipmentAsset` definitions so harnesses render correctly.
    - Links back to its parent summon tool via `echo_mounts:harness_summon_tool_item_id`.
All four items are added to the vanilla TOOLS creative tab in the order harness → harness summon tool → harness summon tool → mount harness.

## Detailed Item Behavior

- **Harness Summon Tool** (`HarnessSummonToolItem` in `src/main/java/com/tabletmc/echo_mounts/item/custom/HarnessSummonToolItem.java`)
    - Actions (client-side sends `StringPayload`, server validates/executes in `net/ServerNetworking.java`):
        - Use on entity: if target entity’s type is `minecraft:happy_ghast`, the tool has no stored mount, and not on cooldown → send `capture:<uuid>`.
        - Use in air:
            - If stored happy_ghast is summoned → `dismiss`.
            - If not riding and a mount is dismissed → `summon` (spawn at player and auto-mount).
            - If sneaking and a mount is stored → `release` (spawn in world, return the `mount_harness`).
    - Server validation/processing (`ServerNetworking.init()`):
        - Verifies the tool in hand is the summon tool; checks allowed entity tag; rejects capturing entities already tagged with `echo_mounts:harness_summon_tool_summoned`.

            - Persists full entity NBT to the item `CUSTOM_DATA` under key `echo_mounts:stored_mount`.
            - Modifies entity ai so it doesnt move.
            - Ensures and records a stable tool ID under `harness_summon_tool_item_id` for linkage.
            - Creates a `mount_harness` pre-configured for the entity, stores `mount_type` and linkage in `mount_harness_data`, and equips it to the entity before serialization.
            - Removes entity from world and applies cooldown (`ModConstants.SUMMON_COOLDOWN_TICKS`).
            - Does not call `player.startRiding`.
        - On summon:
            - Loads entity from stored NBT; validates against allowed tag; auto-tames horses for vanilla behavior.
            - Sets up spawn at the player's position with the player's yaw/pitch, and copies the player's velocity and fall distance to the mount.
            - Applies the command tag `echo_mounts:harness_summon_tool_summoned` and equips a `mount_harness` reconstructed from `mount_harness_data` (re-applies next tick if needed).
            - Calls `player.startRiding(mount, true)` only on the item's stored mount to force immediate mounting (teleports the player onto that mount even if slightly obstructed).
            - Applies cooldown.
        - On dismiss:
            - Works only if the player is currently riding and the mount has the `echo_mounts:harness_summon_tool_summoned` tag (i.e., it was summoned by the tool).
            - Despawns the mount safely and applies cooldown.
            - Leaves the summon tool's stored data unchanged.
        - On release:
            - Spawns the stored entity in front of the player using a raycast-driven safe position finder.
            - Transfers the equipped `mount_harness` (or a reconstructed one from stored mapping data) to the player’s inventory.
            - Restores entity AI back to normal.
            - Clears `echo_mounts:stored_mount`, removes `CustomModelData`, deletes any inventory `mount_harness` items linked to the tool’s `harness_summon_tool_item_id`, then applies cooldown.
    - Data components and keys:
        - `DataComponentTypes.CUSTOM_DATA` → `echo_mounts:stored_mount` (entity NBT), `harness_summon_tool_item_id` (stable UUID-like string), and nested `mount_harness_data`.
        - `DataComponentTypes.CUSTOM_MODEL_DATA` → integer variant per entity type for model overrides (100 = default, 101..106 mapped for horse-like types).
    - Edge cases and guards:
        - Missing `mount_type` in legacy data is auto-healed on summon/release.
        - Capturing rejects entities not in the allowed tag or already marked as summoned.
        - Client checks local cooldown to avoid spamming; server enforces the authoritative cooldown.

- **Mount Harness** (`MountHarnessItem` in `src/main/java/com/tabletmc/echo_mounts/item/custom/MountHarnessItem.java`)
    - Use on entity (server-side):
        - Resolves the correct `EquipmentSlot` via `resolveSlot()`; equips only if slot is empty and entity can equip.
        - Applies `EquippableComponent` via `applyEquippable()` using the appropriate `EquipmentAsset` model for the entity type.
        - For harness-slot entities, toggles `HarnessableMountImpl.echo_mounts$setHarnessd(true)` after equip.
    - Equipment assets:
        - Uses registry keys under `assets/echo_mounts/equipment/...` to render harness layers per supported entities (e.g., horse, donkey, mule, camel, skeleton/zombie horse).
    - Tags and integration:
        - Included in `data/minecraft/tags/items/harnesses.json` so vanilla logic treats it as a harness.
    - Interop with the summon tool:
        - `mount_harness_data` persists linkage (`harness_summon_tool_item_id`) and `mount_type`. On release, the harness is returned to the player.
    - Cannot be removed from entity equipment slot by player
  
- **Cooldown and UX**
    - Server applies cooldown via `player.getItemCooldownManager().set(stack, ModConstants.SUMMON_COOLDOWN_TICKS)`.
    - Client consults the cooldown manager to short-circuit repeated actions.
    - `client/tooltip/HarnessSummonToolTooltipClient` provides contextual tooltip hints for stored state (expand as needed).

## Networking Pipeline (`net/`)

- **Payload**
    - Client sends `StringPayload` values (`capture:<uuid>`, `summon`, `dismiss`, `release`).
- **Server Handler** (`ServerNetworking.init()`)
    - Validates player state, mount tags, and cooldowns.
    - Persists or restores entity data, equips harnesses, and manipulates inventories.
    - Ensures summoned mounts carry the `echo_mounts:harness_summon_tool_summoned` command tag for cleanup logic.
- **Client Hooks**
    - `ClientNetworking.init()` registers C2S payload codec and leaves S2C open for future use.
    - Tooltip overlays (`HarnessSummonToolTooltipClient`) and keybind support live under `client/`.

## Utilities

- **Allowed Mounts**
    - `minecraft:happy_ghast` (entity type tag) defines which entities can be captured.
- **Harness Tag**
    - `data/minecraft/tags/items/harnesses.json` includes `echo_mounts:mount_harness` so vanilla logic treats it like a harness.

## Asset & Rendering Notes

- Item asset pipeline follows the 1.21+ flow documented in `docs/ItemAssets-1.21.md`.
- Mount harnesses leverage equipment asset JSONs under `assets/echo_mounts/equipment/` to swap mount body/harness layers.
- Texture naming conventions match base filenames (see project memory about resolving equipment textures).

## Extension Points

- **Custom Models**
    - Replace runtime `CustomModelData` overrides with item asset predicates once assets are authored (see TODO in `ClientInitializer`).

## Initialization Order (`ModInitializer.java`)

- Registers items, networking, and testing commands on the server entry point.
- Client initializer registers networking, keybinds, tooltips, and rendering features.

Keep this document updated as new items or systems (e.g., harness features) gain functionality.
