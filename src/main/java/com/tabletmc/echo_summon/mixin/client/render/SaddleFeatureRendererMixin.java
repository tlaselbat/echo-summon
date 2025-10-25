package com.tabletmc.echo_summon.mixin.client.render;
import com.tabletmc.echo_summon.client.glint.EchoGlintRenderer;
import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.config.EchoSummonConfig;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

import net.minecraft.client.render.entity.feature.SaddleFeatureRenderer;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.DonkeyEntityRenderState;
import net.minecraft.client.render.entity.state.HorseEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
 

@Environment(EnvType.CLIENT)
@Mixin(SaddleFeatureRenderer.class)
public abstract class SaddleFeatureRendererMixin {

    @Final
    @Shadow private EquipmentRenderer equipmentRenderer;
    @Final
    @Shadow private EquipmentModel.LayerType layerType;
    @Final
    @Shadow private Function<LivingEntityRenderState, ItemStack> saddleStackGetter;
    // no context shadow; not present in this mapping

    @Final
    @Shadow private EntityModel<?> adultModel;
    @Final
    @Shadow private EntityModel<?> babyModel;

    // Maps for mount saddle overlays
    // - Saddle layer -> mount saddle asset id
    @Unique
    private static final Map<EquipmentModel.LayerType, RegistryKey<EquipmentAsset>> SADDLE_ASSET = new EnumMap<>(EquipmentModel.LayerType.class);
    @Unique
    private static final Map<EquipmentModel.LayerType, EquipmentModel.LayerType> BODY_LAYER_BY_SADDLE = new EnumMap<>(EquipmentModel.LayerType.class);
    // - Saddle layer -> echo body asset id
    @Unique
    private static final Map<EquipmentModel.LayerType, RegistryKey<EquipmentAsset>> BODY_ASSET_BY_SADDLE = new EnumMap<>(EquipmentModel.LayerType.class);
    @Unique
    private static final Map<EquipmentModel.LayerType, Identifier> BASE_BODY_TEXTURE = new EnumMap<>(EquipmentModel.LayerType.class);
    @Unique
    private static final Map<String, Identifier> HORSE_COLOR_TEXTURES = new java.util.HashMap<>();

    // Reflection handles for Z-offset layers (not present on all mappings)
    @Unique
    private static java.lang.reflect.Method TRANSLUCENT_CULL_Z_OFFSET_METHOD;
    @Unique
    private static java.lang.reflect.Method CUTOUT_NO_CULL_Z_OFFSET_METHOD;

    static {
      // Saddle overlays (asset IDs must match equipment asset JSON IDs = path under assets/.../equipment/)
      putSaddle(EquipmentModel.LayerType.HORSE_SADDLE,   "entity/horse_saddle_item/horse_saddle_item");
      putSaddle(EquipmentModel.LayerType.DONKEY_SADDLE,  "entity/donkey_saddle_item/donkey_saddle_item");
      putSaddle(EquipmentModel.LayerType.MULE_SADDLE,    "entity/mule_saddle_item/mule_saddle_item");
      putSaddle(EquipmentModel.LayerType.SKELETON_HORSE_SADDLE, "entity/skeleton_saddle_item/skeleton_saddle_item");
      putSaddle(EquipmentModel.LayerType.ZOMBIE_HORSE_SADDLE,   "entity/zombie_saddle_item/zombie_saddle_item");
      putSaddle(EquipmentModel.LayerType.CAMEL_SADDLE,   "entity/camel_saddle_item/camel_saddle_item");

      // Body overlays mapped by saddle layer (use full asset IDs)
      mapBody(EquipmentModel.LayerType.DONKEY_SADDLE, EquipmentModel.LayerType.HORSE_BODY,   "entity/donkey_mount_body/donkey_mount_body");
      mapBody(EquipmentModel.LayerType.MULE_SADDLE, EquipmentModel.LayerType.HORSE_BODY,   "entity/mule_mount_body/mule_mount_body");
      mapBody(EquipmentModel.LayerType.SKELETON_HORSE_SADDLE, EquipmentModel.LayerType.HORSE_BODY, "entity/skeleton_mount_body/skeleton_mount_body");
      mapBody(EquipmentModel.LayerType.ZOMBIE_HORSE_SADDLE,   EquipmentModel.LayerType.HORSE_BODY, "entity/zombie_mount_body/zombie_mount_body");
      mapBody(EquipmentModel.LayerType.HORSE_SADDLE,  EquipmentModel.LayerType.HORSE_BODY,   "entity/horse_mount_body/horse_mount_body");
      // Reflective: only maps if CAMEL_BODY exists on this mapping
      mapBodyByName(EquipmentModel.LayerType.CAMEL_SADDLE, "CAMEL_BODY",   "entity/camel_mount_body/camel_mount_body");
      // Derive mask textures from equipment asset ids (textures/entity/equipment/<asset_path>.png)
      for (var e : BODY_ASSET_BY_SADDLE.entrySet()) {
          Identifier id = e.getValue().getValue();
          if (id != null) {
              String p = id.getPath();
              // Asset paths are of form "entity/<folder>/<name>"; avoid duplicating the "entity/" segment
              if (p.startsWith("entity/")) {
                  p = p.substring("entity/".length());
              }
              Identifier tex = Identifier.of(id.getNamespace(), "textures/entity/equipment/" + p + ".png");
              BASE_BODY_TEXTURE.put(e.getKey(), tex);
          }
      }

      // Horse coat variants (keys match HorseColor enum names in lower_snake and condensed forms)
      registerHorseColor("white", "horse_mount_body_color_white.png");
      registerHorseColor("creamy", "horse_mount_body_color_creamy.png");
      registerHorseColor("chestnut", "horse_mount_body_color_chestnut.png");
      registerHorseColor("brown", "horse_mount_body_color_brown.png");
      registerHorseColor("black", "horse_mount_body_color_black.png");
      registerHorseColor("gray", "horse_mount_body_color_gray.png");
      registerHorseColor("dark_brown", "horse_mount_body_color_darkbrown.png");
    }

    // Create a tinting proxy that multiplies vertex colors by a configurable tint and intensity.
    // We only intercept the color(int,int,int,int) call to avoid version-specific method signatures.
    @Unique
    private static VertexConsumer tinted(VertexConsumer base, float intensity, int rgb, int opacity255) {
        try {
            final float clamped = intensity < 0f ? 0f : Math.min(1f, intensity);
            final float tr = ((rgb >> 16) & 0xFF) / 255f * clamped;
            final float tg = ((rgb >> 8) & 0xFF) / 255f * clamped;
            final float tb = (rgb & 0xFF) / 255f * clamped;
            final float ta = Math.max(1, Math.min(255, opacity255)) / 255f;
            java.lang.reflect.InvocationHandler ih = (proxy, method, args) -> {
                if (args != null && args.length == 4 && "color".equals(method.getName())) {
                    try {
                        int r = (int) args[0];
                        int g = (int) args[1];
                        int b = (int) args[2];
                        int a = (int) args[3];
                        int nr = Math.min(255, Math.max(0, Math.round(r * tr)));
                        int ng = Math.min(255, Math.max(0, Math.round(g * tg)));
                        int nb = Math.min(255, Math.max(0, Math.round(b * tb)));
                        int na = Math.min(255, Math.max(0, Math.round(a * ta)));
                        return method.invoke(base, nr, ng, nb, na);
                    } catch (Throwable ignored) {
                        // Fallback to passthrough on any reflection error
                    }
                }
                return method.invoke(base, args);
            };
            return (VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                    VertexConsumer.class.getClassLoader(),
                    new Class<?>[]{VertexConsumer.class},
                    ih);
        } catch (Throwable ignored) {
            return base;
        }
    }

    @Unique
    private static void registerHorseColor(String key, String textureFile) {
        Identifier tex = ModConstants.Id("textures/entity/equipment/horse_body/" + textureFile);
        HORSE_COLOR_TEXTURES.put(key, tex);
        HORSE_COLOR_TEXTURES.put(key.replace("_", ""), tex);
    }

    @Unique
    private static void putSaddle(EquipmentModel.LayerType saddleLayer, String assetIdPath) {
        SADDLE_ASSET.put(saddleLayer, RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ModConstants.Id(assetIdPath)));
    }

    @Unique
    private static void mapBody(EquipmentModel.LayerType saddleLayer, EquipmentModel.LayerType bodyLayer, String assetIdPath) {
        BODY_LAYER_BY_SADDLE.put(saddleLayer, bodyLayer);
        BODY_ASSET_BY_SADDLE.put(saddleLayer, RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ModConstants.Id(assetIdPath)));
    }

    // Resolves a body layer enum by name if present; no-op if absent
    @Unique
    private static void mapBodyByName(EquipmentModel.LayerType saddleLayer, String bodyLayerName, String assetIdPath) {
        try {
            EquipmentModel.LayerType bodyLayer = EquipmentModel.LayerType.valueOf(bodyLayerName);
            BODY_LAYER_BY_SADDLE.put(saddleLayer, bodyLayer);
            BODY_ASSET_BY_SADDLE.put(saddleLayer, RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ModConstants.Id(assetIdPath)));
        } catch (Throwable ignored) {}
    }

    // Returns body asset for a given saddle layer, with a fallback for CAMEL when the body enum is missing
    @Unique
    private static RegistryKey<EquipmentAsset> bodyAssetForLayer(EquipmentModel.LayerType saddleLayer) {
        RegistryKey<EquipmentAsset> key = BODY_ASSET_BY_SADDLE.get(saddleLayer);
        if (key != null) return key;
        try {
            if (saddleLayer == EquipmentModel.LayerType.CAMEL_SADDLE) {
                return RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ModConstants.Id("entity/camel_mount_body/camel_mount_body"));
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @Unique
    private boolean shouldApplyBodyGlint(ItemStack saddleStack) {
        return !saddleStack.isEmpty();
    }

    // Always suppress equipment-model glint for body overlays by clearing the
    // ENCHANTMENT_GLINT_OVERRIDE on a copy of the saddle stack. Body glint is applied
    // via our unified entity glint overlay instead for consistent visuals.
    @Unique
    private static ItemStack stackForBodyRender(ItemStack saddleStack) {
        try {
            ItemStack copy = saddleStack.copy();
            copy.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
            return copy;
        } catch (Throwable ignored) {
            return saddleStack;
        }
    }

    // For saddle rendering, respect a separate toggle. When disabled, clear the equipment glint;
    // when enabled, keep default behavior so only the saddle glints.
    @Unique
    private static ItemStack stackForSaddleRender(ItemStack saddleStack) {
        // Suppress vanilla equipment glint ONLY when Echo engine saddle glint is enabled.
        // Otherwise, allow vanilla to render the saddle glint as a fallback.
        try {
            if (EchoSummonConfig.enableEchoGlintEngine && EchoSummonConfig.enableMountSaddleGlint) {
                ItemStack copy = saddleStack.copy();
                copy.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
                return copy;
            }
        } catch (Throwable ignored) {}
        return saddleStack;
    }

    @Unique
    private void renderEntityGlintOnly(EntityModel<LivingEntityRenderState> model,
                                       MatrixStack matrices,
                                       VertexConsumerProvider vertexConsumers,
                                       int light,
                                       LivingEntityRenderState renderState) {
        model.setAngles(renderState);
        // Only used for non-horses; use the more visible entity glint
        int passes = Math.max(1, Math.min(10, Math.round(EchoSummonConfig.mountGlintIntensity * 10f)));
        for (int i = 0; i < passes; i++) {
            VertexConsumer base = vertexConsumers.getBuffer(RenderLayer.getEntityGlint());
            VertexConsumer glint = base;
            if (EchoSummonConfig.mountGlintColor != 0xFFFFFF || EchoSummonConfig.mountGlintOpacity != 255) {
                // Keep intensity-driven brightness via passes; tint/opacity handled per vertex color
                glint = tinted(base, 1.0f, EchoSummonConfig.mountGlintColor, EchoSummonConfig.mountGlintOpacity);
            }
            model.render(matrices, glint, light, OverlayTexture.DEFAULT_UV);
        }
    }

    @Unique
    private void renderTranslucentBody(EntityModel<LivingEntityRenderState> model,
                                       MatrixStack matrices,
                                       VertexConsumerProvider vertexConsumers,
                                       int light,
                                       Identifier texture,
                                       boolean glint) {
        VertexConsumer base = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture));
        model.render(matrices, base, light, OverlayTexture.DEFAULT_UV);

        if (!glint) return;

        VertexConsumer glintCons = vertexConsumers.getBuffer(RenderLayer.getEntityGlint());
        model.render(matrices, glintCons, light, OverlayTexture.DEFAULT_UV);
    }

    // Render using a cutout layer with a small Z offset so it always appears above the base coat
    @Unique
    private void renderCutoutZOffsetBody(EntityModel<LivingEntityRenderState> model,
                                         MatrixStack matrices,
                                         VertexConsumerProvider vertexConsumers,
                                         int light,
                                         Identifier texture) {
        RenderLayer layer = getCutoutNoCullZOffsetLayer(texture);
        VertexConsumer base = vertexConsumers.getBuffer(layer);
        model.render(matrices, base, light, OverlayTexture.DEFAULT_UV);
    }

    // Prefer a translucent Z-offset layer when available to avoid z-fighting with the base coat
    @Unique
    private void renderTranslucentZOffsetBody(EntityModel<LivingEntityRenderState> model,
                                              MatrixStack matrices,
                                              VertexConsumerProvider vertexConsumers,
                                              int light,
                                              Identifier texture) {
        RenderLayer layer = getTranslucentZOffsetLayer(texture);
        VertexConsumer base = vertexConsumers.getBuffer(layer);
        model.render(matrices, base, light, OverlayTexture.DEFAULT_UV);
    }

    // Attempts to call RenderLayer.getEntityTranslucentCullZOffset or getEntityTranslucentZOffset reflectively.
    // Falls back to standard translucent if unavailable on this version/mapping.
    @Unique
    private static RenderLayer getTranslucentZOffsetLayer(Identifier texture) {
        try {
            if (TRANSLUCENT_CULL_Z_OFFSET_METHOD == null) {
                // Try multiple candidate names across versions/mappings
                String[] names = new String[] {"getEntityTranslucentCullZOffset", "getEntityTranslucentZOffset"};
                for (String n : names) {
                    try {
                        TRANSLUCENT_CULL_Z_OFFSET_METHOD = RenderLayer.class.getMethod(n, Identifier.class);
                        break;
                    } catch (Throwable ignored) {}
                }
            }
            if (TRANSLUCENT_CULL_Z_OFFSET_METHOD != null) {
                Object layer = TRANSLUCENT_CULL_Z_OFFSET_METHOD.invoke(null, texture);
                if (layer instanceof RenderLayer rl) return rl;
            }
        } catch (Throwable ignored) {
        }
        return RenderLayer.getEntityTranslucent(texture);
    }

    // Attempts to call RenderLayer.getEntityCutoutNoCullZOffset or getEntityCutoutZOffset reflectively.
    // Falls back to standard cutout-no-cull if unavailable.
    @Unique
    private static RenderLayer getCutoutNoCullZOffsetLayer(Identifier texture) {
        try {
            if (CUTOUT_NO_CULL_Z_OFFSET_METHOD == null) {
                String[] names = new String[] {"getEntityCutoutNoCullZOffset", "getEntityCutoutZOffset"};
                for (String n : names) {
                    try {
                        CUTOUT_NO_CULL_Z_OFFSET_METHOD = RenderLayer.class.getMethod(n, Identifier.class);
                        break;
                    } catch (Throwable ignored) {}
                }
            }
            if (CUTOUT_NO_CULL_Z_OFFSET_METHOD != null) {
                Object layer = CUTOUT_NO_CULL_Z_OFFSET_METHOD.invoke(null, texture);
                if (layer instanceof RenderLayer rl) return rl;
            }
        } catch (Throwable ignored) {
        }
        return RenderLayer.getEntityCutoutNoCull(texture);
    }

    // Resolves the optional custom saddle body overlay texture path based on the CHOSEN saddle asset.
    // Example: asset id path "entity/horse_saddle_item/horse_saddle_item" ->
    // overlay path "textures/entity/equipment/horse_saddle_item/horse_saddle_item_body.png"
    @Unique
    private static Identifier resolveSaddleBodyOverlay(RegistryKey<EquipmentAsset> saddleAsset) {
        if (saddleAsset == null) return null;
        try {
            Identifier id = saddleAsset.getValue();
            if (id == null) return null;
            String path = id.getPath(); // e.g. entity/horse_saddle_item/horse_saddle_item
            if (path.startsWith("entity/")) path = path.substring("entity/".length());
            int slash = path.lastIndexOf('/');
            String folder = slash >= 0 ? path.substring(0, slash) : "";
            String base = slash >= 0 ? path.substring(slash + 1) : path;
            // remap folder from <mount>_saddle_item -> <mount>_saddle
            String overlayFolder = folder;
            if (overlayFolder.endsWith("_saddle_item")) {
                overlayFolder = overlayFolder.substring(0, overlayFolder.length() - "_saddle_item".length()) + "_saddle";
            }
            // special-case horse variants
            if ("zombie_saddle".equals(overlayFolder)) {
                overlayFolder = "zombie_horse_saddle";
            } else if ("skeleton_saddle".equals(overlayFolder)) {
                overlayFolder = "skeleton_horse_saddle";
            }
            String texPath = "textures/entity/equipment/" + (overlayFolder.isEmpty() ? "" : overlayFolder + "/") + base + "_body.png";
            return Identifier.of(id.getNamespace(), texPath);
        } catch (Throwable ignored) {
            return null;
        }
    }


    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void echo_summon$renderEchoOverlays(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            LivingEntityRenderState renderState,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci
    ) {
        ItemStack saddleStack = this.saddleStackGetter.apply(renderState);
        if (saddleStack.isEmpty()) return;

        // Resolve equippable for later use; do not restrict to echo_summon-only items
        EquippableComponent equippable = saddleStack.get(DataComponentTypes.EQUIPPABLE);
        boolean hasEchoAsset = false;
        if (equippable != null && equippable.assetId().isPresent()) {
            try {
                Identifier aid = equippable.assetId().get().getValue();
                hasEchoAsset = aid != null && ModConstants.MOD_ID.equals(aid.getNamespace());
            } catch (Throwable ignored) {}
        }

        @SuppressWarnings("unchecked")
        EntityModel<LivingEntityRenderState> model = (EntityModel<LivingEntityRenderState>)(renderState.baby ? this.babyModel : this.adultModel);
        // Pose the equipment model using the current render state
        model.setAngles(renderState);

        // Use the same model for overlays to ensure consistent anchors with equipment body
        // (already posed above)

        // equippable already resolved above

        // Probe for saddle asset existence, but do NOT early-return; we still want to apply body glint
        // even if equipment assets are missing. We'll conditionally skip our saddle render and avoid
        // canceling to let vanilla proceed when the asset is absent.
        RegistryKey<EquipmentAsset> probeSaddle = SADDLE_ASSET.get(this.layerType);
        if (probeSaddle == null) {
            probeSaddle = equippable != null && equippable.assetId().isPresent()
                    ? equippable.assetId().get()
                    : EquipmentAssetKeys.SADDLE;
        }
        boolean hasSaddleAsset = equipmentAssetExists(probeSaddle);

        // Removed captured mount body texture (testing) block

        // Unified body glint application: applies to all supported mount types when enabled
        if (EchoSummonConfig.enableMountBodyGlint && shouldApplyBodyGlint(saddleStack)) {
            if (EchoSummonConfig.enableEchoGlintEngine) {
                Identifier sprite = resolveCustomBodyGlintSprite(this.layerType);
                EchoGlintRenderer.renderBodyGlint(model, matrices, vertexConsumers, light, renderState, sprite);
            } else {
                // Engine disabled: fallback to vanilla entity glint with Echo color/opacity via tinted consumer
                renderEntityGlintOnly(model, matrices, vertexConsumers, light, renderState);
            }
        }

        // Draw the saddle overlay last so straps sit on top of body overlays
        RegistryKey<EquipmentAsset> saddleAsset = SADDLE_ASSET.get(this.layerType);
        if (saddleAsset == null) {
            saddleAsset = equippable != null && equippable.assetId().isPresent()
                    ? equippable.assetId().get()
                    : EquipmentAssetKeys.SADDLE;
        }
        // Respect config: use vanilla saddle asset when custom saddle textures are disabled
        if (!EchoSummonConfig.enableCustomSaddleItemTexture) {
            saddleAsset = EquipmentAssetKeys.SADDLE;
        }
        // Optional custom saddle body overlay (under straps), derived from the chosen saddle asset location
        if (EchoSummonConfig.enableCustomSaddleItemBodyTexture) {
            Identifier overlayTex = resolveSaddleBodyOverlay(saddleAsset);
            if (overlayTex != null && resourceExists(overlayTex)) {
                renderTranslucentZOffsetBody(model, matrices, vertexConsumers, light, overlayTex);
            }
        }
        boolean renderedSaddle = false;
        if (equipmentAssetExists(saddleAsset)) {
            ItemStack saddleRenderStack = stackForSaddleRender(saddleStack);
            this.equipmentRenderer.render(this.layerType, saddleAsset, model, saddleRenderStack, matrices, vertexConsumers, light);
            renderedSaddle = true;
        }

        // Echo-colored saddle glint pass (avoid vanilla purple). Uses the saddle equipment sprite as mask.
        if (EchoSummonConfig.enableEchoGlintEngine && EchoSummonConfig.enableMountSaddleGlint) {
            Identifier saddleSprite = spriteFromEquipmentAsset(saddleAsset);
            EchoGlintRenderer.renderSaddleGlint(model, matrices, vertexConsumers, light, renderState, saddleSprite);
        }

        // Prevent vanilla duplicate only if we rendered the saddle ourselves. If the equipment asset
        // was missing, allow vanilla to handle it so the saddle still appears.
        if (renderedSaddle) {
            ci.cancel();
        }
    }

    // Draws optional overlays for horse color variants if textures exist
    @Unique
    private void renderHorseVariantOverlays(EntityModel<LivingEntityRenderState> model,
                                            MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers,
                                            int light,
                                            LivingEntityRenderState renderState,
                                            ItemStack saddleStack,
                                            Identifier defaultCoatTexture) {
        if (!(renderState instanceof HorseEntityRenderState horse)) return;

        String color = safeEnumName(horse.color);
        if (color.isEmpty()) return;

        // 1) Direct mapped color texture
        Identifier mapped = HORSE_COLOR_TEXTURES.get(color);
        if (mapped != null && resourceExists(mapped)) {
            renderTranslucentBody(model, matrices, vertexConsumers, light, mapped, false);
            return;
        }

        // 2) Candidate filenames
        Identifier[] candidates = getHorseColorTextureCandidates(color);
        for (Identifier tex : candidates) {
            if (resourceExists(tex)) {
                renderTranslucentBody(model, matrices, vertexConsumers, light, tex, false);
                return;
            }
        }
        // 3) No fallback when not found (base-texture fallback disabled)
    }

    @Unique
    private static String safeEnumName(Object enumVal) {
        try {
            if (enumVal instanceof Enum<?> e) {
                return e.name().toLowerCase(java.util.Locale.ROOT);
            }
        } catch (Throwable ignored) {}
        return "";
    }

    @Unique
    private static Identifier[] getHorseColorTextureCandidates(String colorKey) {
            String clean = colorKey.replace("_", "");
        List<Identifier> candidates = new ArrayList<>();
        candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_color_" + colorKey + ".png"));
        if (!clean.equals(colorKey)) {
            candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_color_" + clean + ".png"));
        }
        if ("darkbrown".equals(clean)) {
            candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_color_dark_brown.png"));
        }
        return candidates.stream().filter(Objects::nonNull).distinct().toArray(Identifier[]::new);
    }


    // Cache resource existence checks to avoid per-frame I/O
    @Unique
    private static final java.util.concurrent.ConcurrentMap<Identifier, Boolean> RESOURCE_EXISTS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    @Unique
    private static boolean resourceExists(Identifier id) {
        return RESOURCE_EXISTS_CACHE.computeIfAbsent(id, k -> {
            try {
                var rm = MinecraftClient.getInstance().getResourceManager();
                java.util.Optional<?> res = rm.getResource(k);
                return res != null && res.isPresent();
            } catch (Throwable ignored) {
                return false;
            }
        });
    }

    // Checks if an equipment asset JSON exists under assets/<ns>/equipment/<path>.json
    @Unique
    private static boolean equipmentAssetExists(RegistryKey<EquipmentAsset> key) {
        if (key == null) return false;
        try {
            Identifier id = key.getValue();
            if (id == null) return false;
            Identifier res = Identifier.of(id.getNamespace(), "equipment/" + id.getPath() + ".json");
            return resourceExists(res);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // Checks if an equipment asset JSON exists under assets/<ns>/equipment/<path>.json
    @Unique
    private static Identifier textureFromEquipmentAsset(RegistryKey<EquipmentAsset> key) {
        if (key == null) return null;
        try {
            Identifier id = key.getValue();
            if (id == null) return null;
            return Identifier.of(id.getNamespace(), "textures/entity/equipment/" + id.getPath() + ".png");
        } catch (Throwable ignored) {
            return null;
        }
    }

    // Resolves the custom body glint sprite for the given layer, honoring per-mount config overrides.
    @Unique
    private static Identifier resolveCustomBodyGlintSprite(EquipmentModel.LayerType layer) {
        String raw = null;
        try {
            if (layer == EquipmentModel.LayerType.HORSE_SADDLE) raw = com.tabletmc.echo_summon.config.EchoSummonConfig.mountGlintMaskHorse;
            else if (layer == EquipmentModel.LayerType.DONKEY_SADDLE) raw = com.tabletmc.echo_summon.config.EchoSummonConfig.mountGlintMaskDonkey;
            else if (layer == EquipmentModel.LayerType.MULE_SADDLE) raw = com.tabletmc.echo_summon.config.EchoSummonConfig.mountGlintMaskMule;
            else if (layer == EquipmentModel.LayerType.SKELETON_HORSE_SADDLE) raw = com.tabletmc.echo_summon.config.EchoSummonConfig.mountGlintMaskSkeletonHorse;
            else if (layer == EquipmentModel.LayerType.ZOMBIE_HORSE_SADDLE) raw = com.tabletmc.echo_summon.config.EchoSummonConfig.mountGlintMaskZombieHorse;
            else {
                try {
                    if (layer == EquipmentModel.LayerType.CAMEL_SADDLE) raw = com.tabletmc.echo_summon.config.EchoSummonConfig.mountGlintMaskCamel;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        // If user did not set a custom mask, fall back to default body asset sprite
        if (raw == null || raw.isBlank()) {
            RegistryKey<EquipmentAsset> body = bodyAssetForLayer(layer);
            return spriteFromEquipmentAsset(body);
        }
        String s = raw.trim();
        try {
            // Accept namespaced id like "modid:path"; otherwise default to our mod namespace
            if (s.contains(":")) return Identifier.of(s);
        } catch (Throwable ignored) {}
        return ModConstants.Id(s);
    }

    // Sprite id inside the equipment atlas, matching the texture path under textures/entity/equipment/<asset_path>.png
    // Example: asset id path "entity/horse_mount_body/horse_mount_body" ->
    // sprite id "echo_summon:entity/equipment/horse_mount_body/horse_mount_body"
    @Unique
    private static Identifier spriteFromEquipmentAsset(RegistryKey<EquipmentAsset> key) {
        if (key == null) return null;
        try {
            Identifier id = key.getValue();
            if (id == null) return null;
            // Use the full asset path so that both direct texture checks and atlas sprite lookup succeed.
            // Do NOT truncate to the file name; the atlas sprite id mirrors the full path under entity/equipment/.
            String path = id.getPath();
            if (path.startsWith("entity/")) {
                path = path.substring("entity/".length());
            }
            return Identifier.of(id.getNamespace(), "entity/equipment/" + path);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // Returns an Identifier to a chested body texture if the entity is a donkey or mule with a chest
    // and the texture exists. Otherwise returns null to fall back to normal body assets.
    @Unique
    private static Identifier getChestedBodyTextureIfPresent(EquipmentModel.LayerType saddleLayer, LivingEntityRenderState renderState) {
        boolean isChested = false;
        try {
            if (renderState instanceof DonkeyEntityRenderState donkeyState) {
                isChested = donkeyState.hasChest;
            }
        } catch (Throwable ignored) {}

        if (!isChested) return null;

        Identifier candidate = null;
        if (saddleLayer == EquipmentModel.LayerType.DONKEY_SADDLE) {
            candidate = ModConstants.Id("textures/entity/equipment/horse_body/donkey_mount_body_chested.png");
        } else if (saddleLayer == EquipmentModel.LayerType.MULE_SADDLE) {
            candidate = ModConstants.Id("textures/entity/equipment/horse_body/mule_mount_body_chested.png");
        }
        if (candidate == null) return null;

        // Only use if resource exists to avoid missing-texture quads
        try {
            var rm = MinecraftClient.getInstance().getResourceManager();
            // getResource returns Optional<Resource> on modern versions
            java.util.Optional<?> res = rm.getResource(candidate);
            if (res != null && res.isPresent()) {
                return candidate;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
