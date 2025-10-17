package com.tabletmc.echo_summon.mixin.client.render;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.item.ModItems;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.feature.SaddleFeatureRenderer;
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

    @Shadow private EntityModel<?> adultModel;
    @Shadow private EntityModel<?> babyModel;

    // Maps for mount saddle overlays
    // - Saddle layer -> mount saddle asset id
    private static final Map<EquipmentModel.LayerType, RegistryKey<EquipmentAsset>> SADDLE_ASSET = new EnumMap<>(EquipmentModel.LayerType.class);
    private static final Map<EquipmentModel.LayerType, EquipmentModel.LayerType> BODY_LAYER_BY_SADDLE = new EnumMap<>(EquipmentModel.LayerType.class);
    // - Saddle layer -> echo body asset id
    private static final Map<EquipmentModel.LayerType, RegistryKey<EquipmentAsset>> BODY_ASSET_BY_SADDLE = new EnumMap<>(EquipmentModel.LayerType.class);
    // - Saddle layer -> custom texture overlay (used when no equipment layer type exists)
    private static final Map<EquipmentModel.LayerType, Identifier> CUSTOM_BODY_TEXTURE = new EnumMap<>(EquipmentModel.LayerType.class);
    private static boolean LOGGED_VARIANT_ONCE = false;

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
      CUSTOM_BODY_TEXTURE.put(EquipmentModel.LayerType.CAMEL_SADDLE,
              ModConstants.Id("textures/entity/equipment/camel_body/camel_mount_body.png"));
      // Llama/happy_ghast overlays are not rendered by SaddleFeatureRenderer; handled separately.
    }

    private static void putSaddle(EquipmentModel.LayerType saddleLayer, String assetIdPath) {
        SADDLE_ASSET.put(saddleLayer, RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ModConstants.Id(assetIdPath)));
    }

    private static void mapBody(EquipmentModel.LayerType saddleLayer, EquipmentModel.LayerType bodyLayer, String assetIdPath) {
        BODY_LAYER_BY_SADDLE.put(saddleLayer, bodyLayer);
        BODY_ASSET_BY_SADDLE.put(saddleLayer, RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ModConstants.Id(assetIdPath)));
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
        if (bodyLayer != null) {
            // Render the base species body asset for non-horse species only to avoid double models
            RegistryKey<EquipmentAsset> bodyAsset = BODY_ASSET_BY_SADDLE.get(this.layerType);
            if (this.layerType != EquipmentModel.LayerType.HORSE_SADDLE && bodyAsset != null) {
                this.equipmentRenderer.render(bodyLayer, bodyAsset, model, saddleStack, matrices, vertexConsumers, light);
            }

            // Overlay variant-specific textures when present (only applies to horses via instanceof check)
            // Render on overlayModel (context model if available) so anchors match the base body
            renderHorseVariantOverlays(overlayModel, matrices, vertexConsumers, light, renderState, saddleStack);

            // Special-case donkey/mule chest state to overlay chested texture if present
            Identifier chestedTex = getChestedBodyTextureIfPresent(this.layerType, renderState);
            if (chestedTex != null) {
                renderCustomBody(model, matrices, vertexConsumers, light, chestedTex);
            }
        } else {
            Identifier customTexture = CUSTOM_BODY_TEXTURE.get(this.layerType);
            if (customTexture != null) {
                renderCustomBody(model, matrices, vertexConsumers, light, customTexture);
            }
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

    private void renderCustomBody(EntityModel<LivingEntityRenderState> model,
                                  MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers,
                                  int light,
                                  Identifier texture) {
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        model.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
    }

    // Draws optional overlays for horse variants based on color and markings, if textures exist
    private void renderHorseVariantOverlays(EntityModel<LivingEntityRenderState> model,
                                            MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers,
                                            int light,
                                            LivingEntityRenderState renderState,
                                            ItemStack saddleStack) {
        if (!(renderState instanceof HorseEntityRenderState horse)) return;

        String color = safeEnumName(horse.color);
        String marking = safeEnumName(horse.marking);

        // Draw color first, then marking on top
        if (!color.isEmpty()) {
            String[] colorKeys = new String[] { color, color.replace("_", "") };
            for (String key : colorKeys) {
                Identifier texPath = ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_color_" + key + ".png");
                if (resourceExists(texPath)) {
                    renderTranslucentBody(model, matrices, vertexConsumers, light, texPath);
                    break;
                }
            }
        }

        if (!marking.isEmpty() && !"none".equals(marking)) {
            String[] markingKeys = new String[] { marking, marking.replace("_", "") };
            for (String key : markingKeys) {
                // Try vanilla markings first (these are aligned to the base horse UVs)
                Identifier vanillaMarking = Identifier.of("minecraft", "textures/entity/horse/horse_markings_" + key + ".png");
                if (resourceExists(vanillaMarking)) {
                    renderTranslucentBody(model, matrices, vertexConsumers, light, vanillaMarking);
                    break;
                }
                // Fallback to our equipment-space marking texture
                Identifier equipMarking = ModConstants.Id("textures/entity/equipment/horse_body/horse_mount_body_marking_" + key + ".png");
                if (resourceExists(equipMarking)) {
                    renderTranslucentBody(model, matrices, vertexConsumers, light, equipMarking);
                    break;
                }
            }
        }
    }

    private void renderTranslucentBody(EntityModel<LivingEntityRenderState> model,
                                       MatrixStack matrices,
                                       VertexConsumerProvider vertexConsumers,
                                       int light,
                                       Identifier texture) {
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture));
        model.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
    }

    // alpha debug helper removed

    private static String safeEnumName(Object enumVal) {
        try {
            if (enumVal instanceof Enum<?> e) return e.name().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {}
        return "";
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
