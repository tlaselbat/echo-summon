# Echo Summon: Body Glint Mask Textures

Place per-mount body glint mask PNGs in this folder. Filenames expected by defaults in EchoSummonConfig:

- horse_body_glint_mask.png
- donkey_body_glint_mask.png
- mule_body_glint_mask.png
- skeleton_horse_body_glint_mask.png
- zombie_horse_body_glint_mask.png
- camel_body_glint_mask.png

Notes:
- These are used as masks for the unified mount body glint.
- You can override per-mount mask paths in the config (Mod Menu -> Mount body glint settings) using either:
  - A sprite id: "modid:path" that resolves against the `echo_summon:equipment` atlas, or
  - A relative texture path (without `textures/` and `.png`), e.g. `entity/equipment/horse_body/horse_body_glint_mask`.
- If a mask texture is missing, the renderer falls back to a tinted vanilla glint so the effect still appears.
- To add your own pack textures, respect the atlas registration at `assets/echo_summon/atlases/equipment.json`.
