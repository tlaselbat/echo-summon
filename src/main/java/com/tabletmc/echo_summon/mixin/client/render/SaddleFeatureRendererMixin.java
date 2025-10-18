package com.tabletmc.echo_summon.mixin.client.render;
import com.tabletmc.echo_summon.ModConstants;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumers;
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

/**
 * Custom-render Mount Saddle overlays using the new equipment asset IDs present in resources.
 * Renders both the saddle overlay (current renderer layer) and a full-body overlay per mount species.
 */
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
      // Disable body overlays for species without compatible body layers
      // Custom overlays rendered manually (no matching equipment layer types)
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

      // Horse markings (keys match HorseMarking enum names in lower_snake form)
      registerHorseMarking("white", Identifier.of("minecraft", "textures/entity/horse/horse_markings_white.png"));
      registerHorseMarking("white_dots", Identifier.of("minecraft", "textures/entity/horse/horse_markings_whitedots.png"));
      registerHorseMarking("white_field", Identifier.of("minecraft", "textures/entity/horse/horse_markings_whitefield.png"));
      registerHorseMarking("black_dots", Identifier.of("minecraft", "textures/entity/horse/horse_markings_blackdots.png"));
    }

    private static void registerHorseColor(String key, String textureFile) {
        Identifier tex = ModConstants.Id("textures/entity/equipment/horse_body/" + textureFile);
        HORSE_COLOR_TEXTURES.put(key, tex);
        HORSE_COLOR_TEXTURES.put(key.replace("_", ""), tex);
    }

    private static void registerHorseMarking(String key, Identifier texture) {
        Identifier tex = texture;
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

        // Proceed for any equipped saddle (vanilla or mod) so overlays work universally

        @SuppressWarnings("unchecked")
        EntityModel<LivingEntityRenderState> model = (EntityModel<LivingEntityRenderState>)(renderState.baby ? this.babyModel : this.adultModel);
        // Pose the equipment model using the current render state
        model.setAngles(renderState);

        // Use the same model for overlays to ensure consistent anchors with equipment body
        EntityModel<LivingEntityRenderState> overlayModel = model;
        overlayModel.setAngles(renderState);

        EquippableComponent equippable = saddleStack.get(DataComponentTypes.EQUIPPABLE);

        // Body overlay (species-specific when available)
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
            } else if (baseTexture != null && resourceExists(baseTexture)) {
                renderTranslucentBody(overlayModel, matrices, vertexConsumers, light, baseTexture, false);
            }

            // Special-case donkey/mule chest state to overlay chested texture if present
            Identifier chestedTex = getChestedBodyTextureIfPresent(this.layerType, renderState);
            if (chestedTex != null) {
                renderTranslucentBody(model, matrices, vertexConsumers, light, chestedTex, false);
            }
        } else {
            if (baseTexture != null && resourceExists(baseTexture)) {
                renderTranslucentBody(model, matrices, vertexConsumers, light, baseTexture, false);
            }
        }

        // Unified glint application: single pass for all mounts after body overlays (and before saddle overlay).
        if (shouldApplyBodyGlint(saddleStack)) {
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

        ci.cancel();
    }

    // Draws optional overlays for horse variants based on color and markings, if textures exist
    private void renderHorseVariantOverlays(EntityModel<LivingEntityRenderState> model,
                                            MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers,
                                            int light,
                                            LivingEntityRenderState renderState,
                                            ItemStack saddleStack,
                                            Identifier defaultCoatTexture) {
        if (!(renderState instanceof HorseEntityRenderState horse)) return;

        String color = safeEnumName(horse.color);
        String marking = safeEnumName(horse.marking);

        boolean renderedColor = false;
        // Draw color variant if present
        if (!color.isEmpty()) {
            Identifier mappedColor = HORSE_COLOR_TEXTURES.get(color);
            if (mappedColor == null) {
                ModConstants.LOGGER.info("Echo Summon: horse color '{}' not in map, falling back to candidates.", color);
            } else {
                ModConstants.LOGGER.info("Echo Summon: rendering mapped horse coat '{}' -> {}", color, mappedColor);
                renderTranslucentBody(model, matrices, vertexConsumers, light, mappedColor, false);
                renderedColor = true;
            }
            if (!renderedColor) {
                Identifier[] colorCandidates = getHorseColorTextureCandidates(color);
                for (Identifier texPath : colorCandidates) {
                    if (resourceExists(texPath)) {
                        ModConstants.LOGGER.info("Echo Summon: rendering horse coat variant '{}' with texture {}", color, texPath);
                        renderTranslucentBody(model, matrices, vertexConsumers, light, texPath, false);
                        renderedColor = true;
                        break;
                    }
                }
                if (!renderedColor && colorCandidates.length > 0) {
                    ModConstants.LOGGER.info("Echo Summon: horse coat overlay missing for color '{}' (candidates: {}).", color, java.util.Arrays.toString(colorCandidates));
                }
            }
        }

        if (!renderedColor && defaultCoatTexture != null) {
            ModConstants.LOGGER.info("Echo Summon: using default coat texture {} for horse color '{}'", defaultCoatTexture, color);
            renderTranslucentBody(model, matrices, vertexConsumers, light, defaultCoatTexture, false);
        }

        if (!marking.isEmpty() && !"none".equals(marking)) {
            Identifier mappedMarking = HORSE_MARKING_TEXTURES.get(marking);
            boolean renderedMarking = false;
            if (mappedMarking != null) {
                // Always render mapped vanilla marking textures without resource existence checks.
                // Resource reloads can briefly make getResource() return empty during the same frame.
                ModConstants.LOGGER.info("Echo Summon: rendering mapped horse marking '{}' -> {}", marking, mappedMarking);
                // Slight scale-up to ensure the overlay sits just above the coat in depth, avoiding occlusion
                matrices.push();
                matrices.scale(1.0005F, 1.0005F, 1.0005F);
                // Use translucent to preserve semi-transparency in vanilla marking textures
                renderTranslucentBody(model, matrices, vertexConsumers, light, mappedMarking, false);
                matrices.pop();
                renderedMarking = true;
            }
            if (!renderedMarking) {
                Identifier[] markingCandidates = getHorseMarkingTextureCandidates(marking);
                for (Identifier texPath : markingCandidates) {
                    boolean available = resourceExists(texPath);
                    if (available) {
                        ModConstants.LOGGER.info("Echo Summon: rendering horse marking '{}' with texture {}", marking, texPath);
                        matrices.push();
                        matrices.scale(1.0005F, 1.0005F, 1.0005F);
                        // Use translucent to preserve semi-transparency
                        renderTranslucentBody(model, matrices, vertexConsumers, light, texPath, false);
                        matrices.pop();
                        renderedMarking = true;
                        break;
                    }
                }
                if (!renderedMarking) {
                    ModConstants.LOGGER.info("Echo Summon: horse marking overlay missing for marking '{}' (map/candidates exhausted).", marking);
                }
            }
        }
    }

    private static String safeEnumName(Object enumVal) {
        try {
            if (enumVal instanceof Enum<?> e) {
                return e.name().toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }
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

        // Vanilla namespace (UV-aligned to the base model)
        candidates.add(Identifier.of("minecraft", "textures/entity/horse/horse_markings_" + snake + ".png"));
        if (!clean.equals(snake)) {
            candidates.add(Identifier.of("minecraft", "textures/entity/horse/horse_markings_" + clean + ".png"));
        }

        // Mod namespace singular/plural variations (used when vanilla files are missing)
        candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_marking_" + snake + ".png"));
        candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_markings_" + snake + ".png"));
        if (!clean.equals(snake)) {
            candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_marking_" + clean + ".png"));
            candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_markings_" + clean + ".png"));
        }

        // Legacy fallback (e.g., without prefix)
        candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/" + clean + ".png"));
        if (!clean.equals(snake)) {
            candidates.add(ModConstants.Id("textures/entity/equipment/horse_body/" + snake + ".png"));
        }
        return candidates.stream().filter(Objects::nonNull).distinct().toArray(Identifier[]::new);
    }

    private static boolean resourceExists(Identifier id) {
        try {
            var rm = MinecraftClient.getInstance().getResourceManager();
            java.util.Optional<?> res = rm.getResource(id);
            return res != null && res.isPresent();
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
