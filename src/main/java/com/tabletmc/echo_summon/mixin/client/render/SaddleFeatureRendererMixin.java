package com.tabletmc.echo_summon.mixin.client.render;
import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.config.EchoSummonConfig;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.minecraft.client.MinecraftClient;
import com.tabletmc.echo_summon.item.ModItems;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Arrays;

@Environment(EnvType.CLIENT)
@Mixin(SaddleFeatureRenderer.class)
public abstract class SaddleFeatureRendererMixin {

    @Shadow private EquipmentRenderer equipmentRenderer;
    @Shadow private EquipmentModel.LayerType layerType;
    @Shadow private Function<LivingEntityRenderState, ItemStack> saddleStackGetter;
    // no context shadow; not present in this mapping

    @Shadow private EntityModel<?> adultModel;
    @Shadow private EntityModel<?> babyModel;

    // Maps for mount saddle overlays
    // - Saddle layer -> mount saddle asset id
    private static final Map<EquipmentModel.LayerType, RegistryKey<EquipmentAsset>> SADDLE_ASSET = new EnumMap<>(EquipmentModel.LayerType.class);
    private static final Map<EquipmentModel.LayerType, EquipmentModel.LayerType> BODY_LAYER_BY_SADDLE = new EnumMap<>(EquipmentModel.LayerType.class);
    // - Saddle layer -> echo body asset id
    private static final Map<EquipmentModel.LayerType, RegistryKey<EquipmentAsset>> BODY_ASSET_BY_SADDLE = new EnumMap<>(EquipmentModel.LayerType.class);
    private static final Map<EquipmentModel.LayerType, Identifier> BASE_BODY_TEXTURE = new EnumMap<>(EquipmentModel.LayerType.class);
    private static final Map<String, Identifier> HORSE_COLOR_TEXTURES = new java.util.HashMap<>();
    private static final Map<String, Identifier> HORSE_MARKING_TEXTURES = new java.util.HashMap<>();

    // Reflection handles for translucent Z-offset layers (not present on all mappings)
    private static java.lang.reflect.Method TRANSLUCENT_CULL_Z_OFFSET_METHOD;
    private static java.lang.reflect.Method TRANSLUCENT_Z_OFFSET_METHOD;

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
      BASE_BODY_TEXTURE.put(EquipmentModel.LayerType.HORSE_SADDLE,
              ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body.png"));
      BASE_BODY_TEXTURE.put(EquipmentModel.LayerType.DONKEY_SADDLE,
              ModConstants.Id("textures/entity/equipment/horse_body/donkey_mount_body.png"));
      BASE_BODY_TEXTURE.put(EquipmentModel.LayerType.MULE_SADDLE,
              ModConstants.Id("textures/entity/equipment/horse_body/mule_mount_body.png"));
      BASE_BODY_TEXTURE.put(EquipmentModel.LayerType.SKELETON_HORSE_SADDLE,
              ModConstants.Id("textures/entity/equipment/horse_body/skeleton_mount_body.png"));
      BASE_BODY_TEXTURE.put(EquipmentModel.LayerType.ZOMBIE_HORSE_SADDLE,
              ModConstants.Id("textures/entity/equipment/horse_body/zombie_mount_body.png"));
      BASE_BODY_TEXTURE.put(EquipmentModel.LayerType.CAMEL_SADDLE,
              ModConstants.Id("textures/entity/equipment/camel_body/camel_mount_body.png"));

      // Horse coat variants (keys match HorseColor enum names in lower_snake and condensed forms)
      registerHorseColor("white", "horse_mount_body_color_white.png");
      registerHorseColor("creamy", "horse_mount_body_color_creamy.png");
      registerHorseColor("chestnut", "horse_mount_body_color_chestnut.png");
      registerHorseColor("brown", "horse_mount_body_color_brown.png");
      registerHorseColor("black", "horse_mount_body_color_black.png");
      registerHorseColor("gray", "horse_mount_body_color_gray.png");
      registerHorseColor("dark_brown", "horse_mount_body_color_darkbrown.png");
      // Horse markings (keys match common HorseMarking enum names)
      registerHorseMarking("white", "horse_mount_body_marking_white.png");
      registerHorseMarking("white_field", "horse_mount_body_marking_whitefield.png");
      registerHorseMarking("white_dots", "horse_mount_body_marking_whitedots.png");
      registerHorseMarking("black_dots", "horse_mount_body_marking_blackdots.png");
    }

    private static void registerHorseColor(String key, String textureFile) {
        Identifier tex = ModConstants.Id("textures/entity/equipment/horse_body/" + textureFile);
        HORSE_COLOR_TEXTURES.put(key, tex);
        HORSE_COLOR_TEXTURES.put(key.replace("_", ""), tex);
    }

    private static void registerHorseMarking(String key, String textureFile) {
        Identifier tex = ModConstants.Id("textures/entity/equipment/horse_body/" + textureFile);
        HORSE_MARKING_TEXTURES.put(key, tex);
        HORSE_MARKING_TEXTURES.put(key.replace("_", ""), tex);
    }

    private static void putSaddle(EquipmentModel.LayerType saddleLayer, String assetIdPath) {
        SADDLE_ASSET.put(saddleLayer, RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ModConstants.Id(assetIdPath)));
    }

    private static void mapBody(EquipmentModel.LayerType saddleLayer, EquipmentModel.LayerType bodyLayer, String assetIdPath) {
        BODY_LAYER_BY_SADDLE.put(saddleLayer, bodyLayer);
        BODY_ASSET_BY_SADDLE.put(saddleLayer, RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ModConstants.Id(assetIdPath)));
    }

    // Resolves a body layer enum by name if present; no-op if absent
    private static void mapBodyByName(EquipmentModel.LayerType saddleLayer, String bodyLayerName, String assetIdPath) {
        try {
            EquipmentModel.LayerType bodyLayer = EquipmentModel.LayerType.valueOf(bodyLayerName);
            BODY_LAYER_BY_SADDLE.put(saddleLayer, bodyLayer);
            BODY_ASSET_BY_SADDLE.put(saddleLayer, RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ModConstants.Id(assetIdPath)));
        } catch (Throwable ignored) {}
    }

    private boolean shouldApplyBodyGlint(ItemStack saddleStack) {
        return !saddleStack.isEmpty();
    }

    private void renderEntityGlintOnly(EntityModel<LivingEntityRenderState> model,
                                       MatrixStack matrices,
                                       VertexConsumerProvider vertexConsumers,
                                       int light,
                                       LivingEntityRenderState renderState) {
        model.setAngles(renderState);
        // Only used for non-horses; use the more visible entity glint
        VertexConsumer glint = vertexConsumers.getBuffer(RenderLayer.getEntityGlint());
        model.render(matrices, glint, light, OverlayTexture.DEFAULT_UV);
    }

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
    private void renderCutoutZOffsetBody(EntityModel<LivingEntityRenderState> model,
                                         MatrixStack matrices,
                                         VertexConsumerProvider vertexConsumers,
                                         int light,
                                         Identifier texture) {
        try {
            VertexConsumer base = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCullZOffset(texture));
            model.render(matrices, base, light, OverlayTexture.DEFAULT_UV);
        } catch (Throwable t) {
            // Fallback if ZOffset layer is unavailable on this environment
            VertexConsumer base = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
            model.render(matrices, base, light, OverlayTexture.DEFAULT_UV);
        }
    }

    // Prefer a translucent Z-offset layer when available to avoid z-fighting with the base coat
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
    private static RenderLayer getTranslucentZOffsetLayer(Identifier texture) {
        try {
            if (TRANSLUCENT_CULL_Z_OFFSET_METHOD == null) {
                try {
                    TRANSLUCENT_CULL_Z_OFFSET_METHOD = RenderLayer.class.getMethod("getEntityTranslucentCullZOffset", Identifier.class);
                } catch (Throwable ignored) {}
            }
            if (TRANSLUCENT_CULL_Z_OFFSET_METHOD != null) {
                Object layer = TRANSLUCENT_CULL_Z_OFFSET_METHOD.invoke(null, texture);
                if (layer instanceof RenderLayer rl) return rl;
            }

            if (TRANSLUCENT_Z_OFFSET_METHOD == null) {
                try {
                    TRANSLUCENT_Z_OFFSET_METHOD = RenderLayer.class.getMethod("getEntityTranslucentZOffset", Identifier.class);
                } catch (Throwable ignored) {}
            }
            if (TRANSLUCENT_Z_OFFSET_METHOD != null) {
                Object layer = TRANSLUCENT_Z_OFFSET_METHOD.invoke(null, texture);
                if (layer instanceof RenderLayer rl) return rl;
            }
        } catch (Throwable ignored) {
        }
        return RenderLayer.getEntityTranslucent(texture);
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
        boolean bodyEnabled = EchoSummonConfig.enableCustomMountBodyRendering;
        ItemStack saddleStack = this.saddleStackGetter.apply(renderState);
        if (saddleStack.isEmpty()) return;

        // Only apply custom rendering when our mount saddle is equipped (or the asset is from echo_summon)
        boolean isEchoMountSaddle = saddleStack.isOf(ModItems.MOUNT_SADDLE);
        EquippableComponent equippable = saddleStack.get(DataComponentTypes.EQUIPPABLE);
        boolean hasEchoAsset = false;
        if (equippable != null && !equippable.assetId().isEmpty()) {
            try {
                Identifier aid = equippable.assetId().get().getValue();
                hasEchoAsset = aid != null && ModConstants.MOD_ID.equals(aid.getNamespace());
            } catch (Throwable ignored) {}
        }
        if (!(isEchoMountSaddle || hasEchoAsset)) {
            // Not our saddle: let vanilla renderer proceed without canceling
            return;
        }

        @SuppressWarnings("unchecked")
        EntityModel<LivingEntityRenderState> model = (EntityModel<LivingEntityRenderState>)(renderState.baby ? this.babyModel : this.adultModel);
        // Pose the equipment model using the current render state
        model.setAngles(renderState);

        // Use the same model for overlays to ensure consistent anchors with equipment body
        EntityModel<LivingEntityRenderState> overlayModel = model;
        overlayModel.setAngles(renderState);

        // equippable already resolved above

        // If the saddle equipment asset is missing from resources, let vanilla renderer handle it to avoid missing overlays
        RegistryKey<EquipmentAsset> probeSaddle = SADDLE_ASSET.get(this.layerType);
        if (probeSaddle == null) {
            probeSaddle = equippable != null && !equippable.assetId().isEmpty()
                    ? equippable.assetId().get()
                    : EquipmentAssetKeys.SADDLE;
        }
        if (!equipmentAssetExists(probeSaddle)) {
            return; // do not cancel; vanilla rendering will proceed
        }

        // Body overlay (species-specific when available) — gated by config
        if (bodyEnabled) {
            EquipmentModel.LayerType bodyLayer = BODY_LAYER_BY_SADDLE.get(this.layerType);
            Identifier baseTexture = BASE_BODY_TEXTURE.get(this.layerType);
            if (bodyLayer != null) {
                RegistryKey<EquipmentAsset> bodyAsset = BODY_ASSET_BY_SADDLE.get(this.layerType);
                boolean isHorse = this.layerType == EquipmentModel.LayerType.HORSE_SADDLE;

                // For non-horses, draw the base body asset; for horses, rely on our variant overlays instead
                if (!isHorse && bodyAsset != null) {
                    this.equipmentRenderer.render(bodyLayer, bodyAsset, model, saddleStack, matrices, vertexConsumers, light);
                }

                if (isHorse) {
                    // Overlay variant-specific textures when present (only applies to horses via instanceof check)
                    // Render on overlayModel (context model if available) so anchors match the base body
                    renderHorseVariantOverlays(overlayModel, matrices, vertexConsumers, light, renderState, saddleStack, baseTexture);
                    // Overlay markings (white, white_field, white_dots, black_dots) when present
                    renderHorseMarkingOverlays(overlayModel, matrices, vertexConsumers, light, renderState, saddleStack);
                } else {
                    // Disabled base-texture fallback for non-horses
                }

                // Special-case donkey/mule chest state to overlay chested texture if present
                Identifier chestedTex = getChestedBodyTextureIfPresent(this.layerType, renderState);
                if (chestedTex != null) {
                    renderTranslucentBody(model, matrices, vertexConsumers, light, chestedTex, false);
                }
            } else {
                // No body layer is available on this mapping. Provide a camel-specific fallback so that
                // the camel body overlay still renders even when CAMEL_BODY is missing.
                try {
                    if (this.layerType == EquipmentModel.LayerType.CAMEL_SADDLE && baseTexture != null && resourceExists(baseTexture)) {
                        // Render the base camel body texture directly using a translucent Z-offset layer
                        // to avoid z-fighting with the vanilla coat.
                        renderTranslucentZOffsetBody(overlayModel, matrices, vertexConsumers, light, baseTexture);
                    }
                } catch (Throwable ignored) {
                    // If CAMEL_SADDLE enum is not present in this environment, simply skip.
                }
            }
        }

        // Unified glint application: controlled by config
        if (EchoSummonConfig.enableMountBodyGlint && bodyEnabled && shouldApplyBodyGlint(saddleStack)) {
            renderEntityGlintOnly(model, matrices, vertexConsumers, light, renderState);
        }

        // Draw the saddle overlay last so straps sit on top of body overlays
        RegistryKey<EquipmentAsset> saddleAsset = SADDLE_ASSET.get(this.layerType);
        if (saddleAsset == null) {
            saddleAsset = equippable != null && !equippable.assetId().isEmpty()
                    ? equippable.assetId().get()
                    : EquipmentAssetKeys.SADDLE;
        }
        this.equipmentRenderer.render(this.layerType, saddleAsset, model, saddleStack, matrices, vertexConsumers, light);

        // We handled rendering for this layer, prevent vanilla duplicate
        ci.cancel();
    }

    // Draws optional overlays for horse color variants if textures exist
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

    // Draws optional overlays for horse markings if textures exist (white, white_field, white_dots, black_dots)
    private void renderHorseMarkingOverlays(EntityModel<LivingEntityRenderState> model,
                                            MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers,
                                            int light,
                                            LivingEntityRenderState renderState,
                                            ItemStack saddleStack) {
        if (!(renderState instanceof HorseEntityRenderState horse)) return;

        String marking = resolveHorseMarkingKey(horse);
        if (marking.isEmpty()) return;

        Identifier mapped = HORSE_MARKING_TEXTURES.get(marking);
        if (mapped != null && resourceExists(mapped)) {
            renderTranslucentBody(model, matrices, vertexConsumers, light, mapped, false);
            return;
        }

        Identifier[] candidates = getHorseMarkingTextureCandidates(marking);
        for (Identifier tex : candidates) {
            if (resourceExists(tex)) {
                renderTranslucentBody(model, matrices, vertexConsumers, light, tex, false);
                return;
            }
        }
    }

    private static String safeEnumName(Object enumVal) {
        try {
            if (enumVal instanceof Enum<?> e) {
                return e.name().toLowerCase(java.util.Locale.ROOT);
            }
        } catch (Throwable ignored) {}
        return "";
    }

    // Attempts to obtain a marking enum name from the render state reflectively
    private static String resolveHorseMarkingKey(HorseEntityRenderState horse) {
        if (horse == null) return "";
        // Try common field names first
        try {
            var f = horse.getClass().getField("marking");
            Object v = f.get(horse);
            String s = safeEnumName(v);
            if (!s.isEmpty()) return s;
        } catch (Throwable ignored) {}
        try {
            var f = horse.getClass().getField("pattern");
            Object v = f.get(horse);
            String s = safeEnumName(v);
            if (!s.isEmpty()) return s;
        } catch (Throwable ignored) {}
        // Scan for any enum field whose name hints marking/pattern/style
        try {
            for (var f : horse.getClass().getFields()) {
                String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                if (!(n.contains("mark") || n.contains("pattern") || n.contains("style"))) continue;
                Object v = f.get(horse);
                String s = safeEnumName(v);
                if (!s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static Identifier[] getHorseColorTextureCandidates(String colorKey) {
        String snake = colorKey;
        String clean = colorKey.replace("_", "");
        List<Identifier> candidates = new ArrayList<>();
        candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_color_" + snake + ".png"));
        if (!clean.equals(snake)) {
            candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_color_" + clean + ".png"));
        }
        if ("darkbrown".equals(clean)) {
            candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_color_dark_brown.png"));
        }
        return candidates.stream().filter(Objects::nonNull).distinct().toArray(Identifier[]::new);
    }

    private static Identifier[] getHorseMarkingTextureCandidates(String markingKey) {
        String snake = markingKey;
        String clean = markingKey.replace("_", "");
        List<Identifier> candidates = new ArrayList<>();
        candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_marking_" + snake + ".png"));
        if (!clean.equals(snake)) {
            candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_marking_" + clean + ".png"));
        }
        // explicit synonyms
        if ("whitefield".equals(clean)) {
            candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_marking_white_field.png"));
        }
        if ("whitedots".equals(clean)) {
            candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_marking_white_dots.png"));
        }
        if ("blackdots".equals(clean)) {
            candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_marking_black_dots.png"));
        }
        return candidates.stream().filter(Objects::nonNull).distinct().toArray(Identifier[]::new);
    }


    // Cache resource existence checks to avoid per-frame I/O
    private static final java.util.concurrent.ConcurrentMap<Identifier, Boolean> RESOURCE_EXISTS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

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

    // Returns an Identifier to a chested body texture if the entity is a donkey or mule with a chest
    // and the texture exists. Otherwise returns null to fall back to normal body assets.
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
