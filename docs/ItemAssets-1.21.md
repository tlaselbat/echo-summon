# Minecraft 1.21+ Item Assets: Practical Guide

This guide explains how item rendering works on 1.21+ and how to wire your assets together. Non‑required paths are kept intentionally vague.

## Overview

Item rendering now begins with an item asset entry. The item asset selects a model. The model references one or more textures. The textures must be discoverable by the GUI sprite atlas under your namespace.

- Item asset entry: `assets/<namespace>/items/<item_id>.json`
- Model JSON: `assets/<namespace>/models/item/<model_name>.json`
- Texture PNG: `assets/<namespace>/textures/item/<texture_name>.png`

## 1) Item asset file (REQUIRED)

Path: `assets/<ns>/items/<id>.json`

Minimal, correct example:
```json
{
  "model": {
    "type": "minecraft:model",
    "model": "echo_mounts:item/saddle_summon_tool"
  }
}
```

Notes:
- `type: "minecraft:model"` means “use a baked JSON model”.
- `model`: model ID in `<namespace>:<path>` format. The example resolves to:
  - `assets/echo_mounts/models/item/saddle_summon_tool.json`.
- If this file exists, it takes precedence over the legacy fallback of looking up `models/item/<id>.json` by name.

Common mistakes:
- Using an older schema such as `"format": "generated"` + a `textures` object in the item asset file. That is not the current 1.21+ item asset schema.
- Typos in the model ID (wrong namespace/path).

## 2) Item model file (REQUIRED)

Path: `assets/<ns>/models/item/<name>.json`

Typical flat icon model:
```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "echo_mounts:item/saddle_summon_tool"
  }
}
```

Notes:
- `parent`:
  - `minecraft:item/generated` for flat icons.
  - `minecraft:item/handheld` for tool-like transforms (optional, when you need it).
- `textures`:
  - `layer0` is the base icon. You can add `layer1`, `layer2`, … for overlays.
- You can still use `display` transforms and `overrides` in the model as needed.

Common mistakes:
- Referencing the wrong sprite ID (e.g., missing the `item/` prefix if your atlas registers it that way).

## 3) Texture file (REQUIRED)

Path: `assets/<ns>/textures/item/<name>.png`

Notes:
- The model’s `layer0` string (e.g., `echo_mounts:item/saddle_summon_tool`) must resolve to a PNG in your item textures folder under your namespace.
- Prefer power-of-two dimensions (e.g., 16×16, 32×32) and transparent background for icons.

## Sprite atlas in this repo (echo_mounts)

- Atlas file: `assets/echo_mounts/atlases/gui.json`

```json
{
  "sources": [
    { "type": "directory", "source": "item", "prefix": "item/" }
  ]
}
```

- This registers every PNG under `assets/echo_mounts/textures/item/` as a sprite with ID:
  - `echo_mounts:item/<relative_path_without_.png>`
- Examples:
  - `textures/item/saddle_summon_tool.png` → `echo_mounts:item/saddle_summon_tool`
  - `textures/item/tools/saddle_summon_tool.png` → `echo_mounts:item/tools/saddle_summon_tool`

## Resolution order and precedence

- If `assets/<ns>/items/<id>.json` exists, it dictates which model is used for that item.
- The selected model then references textures by sprite ID.
- If the item asset is missing, the game may fall back to `assets/<ns>/models/item/<id>.json` by convention—do not rely on this in 1.21+ when multiple packs/mods are involved.

## Debugging checklist

- **Item asset present**: `assets/<ns>/items/<id>.json` points to the intended model ID.
- **Model valid**: parent is set (generated/handheld) and `textures.layer0` points to a valid sprite ID.
- **Texture exists**: PNG is present under your item textures folder with the expected name.
- **Atlas includes item sprites**: your namespace GUI atlas includes your item textures so the sprite ID exists.
- **Reload**: press F3+T. Check logs for “Missing/Skipping missing texture” or model bake errors.

Quick isolation tricks:
- Point the item asset to a known vanilla model:
  ```json
  { "model": { "type": "minecraft:model", "model": "minecraft:item/iron_ingot" } }
  ```
- Or point the model’s `layer0` to a vanilla texture:
  ```json
  { "parent": "minecraft:item/generated", "textures": { "layer0": "minecraft:item/iron_ingot" } }
  ```

## Common pitfalls

- Wrong 1.21+ item asset schema (e.g., `"format": "generated"` in `items/*.json`).
- Mismatched IDs between the model texture string and the atlas-registered sprite IDs.
- Editing only `models/item/<id>.json` while `items/<id>.json` points to a different model.
- Forgetting to reload (F3+T).

## Migration tips from pre‑1.21

- Pre‑1.21 projects often worked with just `models/item/<id>.json` + the texture file.
- On 1.21+, always add `items/<id>.json` to explicitly select the model, especially when other packs/mods exist.
- Keep atlas configuration consistent with how you reference sprites in models (prefixes, folder layout, etc.).

## TL;DR checklist

- Add `assets/<ns>/items/<id>.json` with:
  ```json
  { "model": { "type": "minecraft:model", "model": "<ns>:item/<name>" } }
  ```
- Add `assets/<ns>/models/item/<name>.json` with:
  ```json
  { "parent": "minecraft:item/generated", "textures": { "layer0": "<ns>:item/<name>" } }
  ```
- Ensure your GUI atlas for the namespace includes your item textures and yields matching sprite IDs.
- Reload (F3+T).

## Changing paths and renaming (items, models, textures)

- Concrete steps for this repo (`echo_mounts`), using `saddle_summon_tool` and `mount_saddle`.

### A) Move or rename a texture PNG

- Original setup:
  - Texture: `assets/echo_mounts/textures/item/saddle_summon_tool.png`
{{ ... }}
  ```
- Only change the model’s `textures.layer0` if you also moved/renamed the texture (see A).

### C) Rename the item ID (command/give ID)

- Suppose you want `saddle_summon_tool` → `echo_whistle`.
  - Create `assets/echo_mounts/items/echo_whistle.json` and point it to your model:
  ```json
  { "model": { "type": "minecraft:model", "model": "echo_mounts:item/saddle_summon_tool" } }
  ```
  or, if you also renamed the model:
  ```json
  { "model": { "type": "minecraft:model", "model": "echo_mounts:item/echo_whistle" } }
  ```
{{ ... }}

### D) Change the namespace

- If moving from `echo_mounts` to, say, `tp`:
  - Move files under `assets/tp/...`
  - Update all IDs inside JSON:
    - In item asset: `"model": "tp:item/..."`
    - In model textures: `"layer0": "tp:item/..."`
  - Update `assets/tp/atlases/gui.json` accordingly.

### E) Change how your atlas prefixes sprites (advanced)

- Sprite IDs are computed from your atlas configuration. If you alter the prefix or folder used by the atlas, update the model texture IDs to match.
- Quick formula (conceptual):
  - Sprite ID = `<ns>:` + `<atlas_prefix>` + `<relative_path_inside_source_folder_without_.png>`
  - Example: if a file is at `textures/item/reins/echo_reins.png` and your atlas yields `item/` + relative path, the sprite becomes `<ns>:item/reins/echo_reins`.

### F) Bulk rename tips

- Search for the old IDs across your namespace to avoid stragglers:
  - In item assets: search `"model": { ... "model": "<ns>:item/..." }`
  - In models: search `"textures": { "layer0": "<ns>:item/..." }`
- Apply changes, then reload (F3+T) and scan the log for missing texture/model lines.

### G) Validate after changes

- Give yourself the item (e.g., `/give @s <ns>:<new_id>`), check inventory icon and in-hand.
- If purple/black:
  - Re-check that the item asset points to the intended model ID.
  - Re-check that the model’s `layer0` points to a sprite that exists.
  - Re-check your namespace GUI atlas includes the texture location.
