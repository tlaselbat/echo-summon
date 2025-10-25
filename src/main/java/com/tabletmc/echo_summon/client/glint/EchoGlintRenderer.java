package com.tabletmc.echo_summon.client.glint;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.config.EchoSummonConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class EchoGlintRenderer {
    private EchoGlintRenderer() {}
    private static boolean LOGGED_DIRECT = false;
    private static boolean LOGGED_SPRITE = false;
    private static boolean LOGGED_FALLBACK = false;
    private static final java.util.concurrent.ConcurrentMap<Identifier, Boolean> RESOURCE_EXISTS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    // Facade: render a body-wide glint using the Echo engine if enabled, otherwise fallback.
    public static void renderBodyGlint(EntityModel<LivingEntityRenderState> model,
                                       MatrixStack matrices,
                                       VertexConsumerProvider buffers,
                                       int light,
                                       LivingEntityRenderState state,
                                       Identifier maskTexture) {
        renderSaddleGlint(model, matrices, buffers, light, state, maskTexture);
    }

    // Facade: render a saddle-only glint using the Echo engine if enabled, otherwise fallback.
    public static void renderSaddleGlint(EntityModel<LivingEntityRenderState> model,
                                         MatrixStack matrices,
                                         VertexConsumerProvider buffers,
                                         int light,
                                         LivingEntityRenderState state,
                                         Identifier maskTexture) {
        if (!EchoSummonConfig.enableEchoGlintEngine) {
            // Engine disabled: do not render any fallback glint
            return;
        }
        renderColoredSpritePass(model, matrices, buffers, light, state, maskTexture);
    }

    // Fallback that uses the vanilla glint shader but enforces Echo color/opacity and intensity via passes.
    private static void fallbackVanillaGlint(EntityModel<LivingEntityRenderState> model,
                                             MatrixStack matrices,
                                             VertexConsumerProvider buffers,
                                             int light,
                                             LivingEntityRenderState state) {
        model.setAngles(state);
        int passes = Math.max(1, Math.min(10, Math.round(EchoSummonConfig.mountGlintIntensity * 10f)));
        int a = Math.max(1, Math.min(255, EchoSummonConfig.mountGlintOpacity));
        int rgb = EchoSummonConfig.mountGlintColor & 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = (rgb) & 0xFF;
        for (int i = 0; i < passes; i++) {
            VertexConsumer base = buffers.getBuffer(RenderLayer.getEntityGlint());
            VertexConsumer colored = forceFixedColor(base, r, g, b, a);
            model.render(matrices, colored, light, OverlayTexture.DEFAULT_UV);
        }
    }

    // Interim custom pass that uses the equipment atlas sprite as a mask. Color/opacity/scroll/scale applied.
    private static void renderColoredSpritePass(EntityModel<LivingEntityRenderState> model,
                                                MatrixStack matrices,
                                                VertexConsumerProvider buffers,
                                                int light,
                                                LivingEntityRenderState state,
                                                Identifier spriteId) {
        if (spriteId == null) {
            if (!LOGGED_FALLBACK) { LOGGED_FALLBACK = true; ModConstants.LOGGER.info("EchoGlint: spriteId null -> falling back to vanilla tinted glint"); }
            fallbackVanillaGlint(model, matrices, buffers, light, state);
            return;
        }
        // Prefer direct texture if it exists (avoids missing-sprite purple and works without atlas reload)
        Identifier directTexture = Identifier.of(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");
        if (resourceExists(directTexture)) {
            if (!LOGGED_DIRECT) { LOGGED_DIRECT = true; ModConstants.LOGGER.info("EchoGlint: using DIRECT texture {}", directTexture); }
            renderColoredTexturePass(model, matrices, buffers, light, state, directTexture);
            return;
        }
        // Resolve sprite from our custom equipment atlas
        Sprite sprite = resolveEquipmentSprite(spriteId);
        if (sprite == null) {
            if (!LOGGED_FALLBACK) { LOGGED_FALLBACK = true; ModConstants.LOGGER.info("EchoGlint: sprite resolution failed for {} -> falling back to vanilla tinted glint", spriteId); }
            fallbackVanillaGlint(model, matrices, buffers, light, state);
            return;
        }
        if (!LOGGED_SPRITE) { LOGGED_SPRITE = true; ModConstants.LOGGER.info("EchoGlint: using SPRITE {} from atlas {}", spriteId, equipmentAtlasId()); }
        Identifier atlasTexture = equipmentAtlasTextureId();
        model.setAngles(state);
        int passes = Math.max(1, Math.min(10, Math.round(EchoSummonConfig.mountGlintIntensity * 10f)));
        int a = Math.max(1, Math.min(255, EchoSummonConfig.mountGlintOpacity));
        int rgb = EchoSummonConfig.mountGlintColor & 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = (rgb) & 0xFF;
        float scaleX = Math.max(0.1f, Math.min(8.0f, EchoSummonConfig.mountGlintScaleX));
        float scaleY = Math.max(0.1f, Math.min(8.0f, EchoSummonConfig.mountGlintScaleY));
        float scrollX = EchoSummonConfig.mountGlintScrollX;
        float scrollY = EchoSummonConfig.mountGlintScrollY;
        float t = (System.currentTimeMillis() & 0x3FFFFFFF) / 1000f; // seconds
        for (int i = 0; i < passes; i++) {
            RenderLayer layer = getTranslucentZOffsetLayer(atlasTexture);
            VertexConsumer base = buffers.getBuffer(layer);
            VertexConsumer wrapped = forceFixedColorAndSpriteUV(base, sprite, r, g, b, a, scaleX, scaleY, scrollX, scrollY, t);
            model.render(matrices, wrapped, light, OverlayTexture.DEFAULT_UV);
        }
    }

    private static Identifier equipmentAtlasId() {
        // Primary: <ns>:equipment (atlas id)
        return Identifier.of("echo_summon", "equipment");
    }

    private static Identifier equipmentAtlasTextureId() {
        // GL texture backing the atlas: textures/atlas/<atlas>.png
        Identifier atlas = equipmentAtlasId();
        return Identifier.of(atlas.getNamespace(), "textures/atlas/" + atlas.getPath() + ".png");
    }

    // Reflection handle for translucent Z-offset layer
    private static java.lang.reflect.Method TRANSLUCENT_CULL_Z_OFFSET_METHOD;

    // Attempts to call RenderLayer.getEntityTranslucentCullZOffset or getEntityTranslucentZOffset reflectively.
    // Falls back to standard translucent if unavailable on this version/mapping.
    private static RenderLayer getTranslucentZOffsetLayer(Identifier texture) {
        try {
            if (TRANSLUCENT_CULL_Z_OFFSET_METHOD == null) {
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

    private static Sprite resolveEquipmentSprite(Identifier spriteId) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            // First try the id as provided
            Sprite s = mc.getSpriteAtlas(equipmentAtlasId()).apply(spriteId);
            if (s != null) return s;
            // Try with atlas prefix (entity/equipment/)
            Identifier prefixed = Identifier.of(spriteId.getNamespace(), "entity/equipment/" + spriteId.getPath());
            return mc.getSpriteAtlas(equipmentAtlasId()).apply(prefixed);
        } catch (Throwable ignored) {
            return null;
        }
    }

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

    // Direct texture path (e.g., textures/entity/equipment/horse_mount_body.png) with color/opacity/scroll/scale
    private static void renderColoredTexturePass(EntityModel<LivingEntityRenderState> model,
                                                 MatrixStack matrices,
                                                 VertexConsumerProvider buffers,
                                                 int light,
                                                 LivingEntityRenderState state,
                                                 Identifier textureId) {
        model.setAngles(state);
        int passes = Math.max(1, Math.min(10, Math.round(EchoSummonConfig.mountGlintIntensity * 10f)));
        int a = Math.max(1, Math.min(255, EchoSummonConfig.mountGlintOpacity));
        int rgb = EchoSummonConfig.mountGlintColor & 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = (rgb) & 0xFF;
        float scaleX = Math.max(0.1f, Math.min(8.0f, EchoSummonConfig.mountGlintScaleX));
        float scaleY = Math.max(0.1f, Math.min(8.0f, EchoSummonConfig.mountGlintScaleY));
        float scrollX = EchoSummonConfig.mountGlintScrollX;
        float scrollY = EchoSummonConfig.mountGlintScrollY;
        float t = (System.currentTimeMillis() & 0x3FFFFFFF) / 1000f; // seconds
        for (int i = 0; i < passes; i++) {
            RenderLayer layer = getTranslucentZOffsetLayer(textureId);
            VertexConsumer base = buffers.getBuffer(layer);
            VertexConsumer wrapped = forceFixedColorAndUV(base, r, g, b, a, scaleX, scaleY, scrollX, scrollY, t);
            model.render(matrices, wrapped, light, OverlayTexture.DEFAULT_UV);
        }
    }

    // Dynamic proxy that overrides color(int,int,int,int) to fixed values and delegates others.
    private static VertexConsumer forceFixedColorAndSpriteUV(VertexConsumer base, Sprite sprite,
                                                             int r, int g, int b, int a,
                                                             float scaleX, float scaleY, float scrollX, float scrollY, float timeSec) {
        try {
            final int fr = Math.max(0, Math.min(255, r));
            final int fg = Math.max(0, Math.min(255, g));
            final int fb = Math.max(0, Math.min(255, b));
            final int fa = Math.max(0, Math.min(255, a));
            final float sX = scaleX;
            final float sY = scaleY;
            final float scX = scrollX;
            final float scY = scrollY;
            final float t = timeSec;
            final float u0 = sprite.getMinU();
            final float v0 = sprite.getMinV();
            final float u1 = sprite.getMaxU();
            final float v1 = sprite.getMaxV();
            java.lang.reflect.InvocationHandler ih = (proxy, method, args) -> {
                if (args != null) {
                    String name = method.getName();
                    // color override: support both int,int,int,int and float,float,float,float
                    if ("color".equals(name) && args.length == 4) {
                        try {
                            // Choose based on runtime arg types to avoid static signature assumptions
                            if (args[0] instanceof Float || args[0] instanceof Double) {
                                float rf = fr / 255f, gf = fg / 255f, bf = fb / 255f, af = fa / 255f;
                                return method.invoke(base, rf, gf, bf, af);
                            } else {
                                return method.invoke(base, fr, fg, fb, fa);
                            }
                        } catch (Throwable ignored) {}
                    }
                    // uv override: mappings vary between texture(u,v) and uv(u,v)
                    if (("texture".equals(name) || "uv".equals(name)) && args.length == 2) {
                        try {
                            float u = ((Number) args[0]).floatValue();
                            float v = ((Number) args[1]).floatValue();
                            // Local transform then remap into sprite region
                            float lu = u * sX + scX * t;
                            float lv = v * sY + scY * t;
                            // Wrap to [0,1)
                            lu = lu - (float)Math.floor(lu);
                            lv = lv - (float)Math.floor(lv);
                            float su = u0 + lu * (u1 - u0);
                            float sv = v0 + lv * (v1 - v0);
                            return method.invoke(base, su, sv);
                        } catch (Throwable ignored) {}
                    }
                    // Packed vertex path: vertex(..., r,g,b,a, u,v, overlay, light, nx,ny,nz)
                    if ("vertex".equals(name) && args.length >= 14) {
                        try {
                            // Force color (floats 0..1)
                            args[3] = fr / 255f;
                            args[4] = fg / 255f;
                            args[5] = fb / 255f;
                            args[6] = fa / 255f;
                            // Transform UVs at positions 8,9
                            float u = ((Number) args[8]).floatValue();
                            float v = ((Number) args[9]).floatValue();
                            float lu = u * sX + scX * t;
                            float lv = v * sY + scY * t;
                            lu = lu - (float)Math.floor(lu);
                            lv = lv - (float)Math.floor(lv);
                            float su = u0 + lu * (u1 - u0);
                            float sv = v0 + lv * (v1 - v0);
                            args[8] = su;
                            args[9] = sv;
                            return method.invoke(base, args);
                        } catch (Throwable ignored) {}
                    }
                }
                return method.invoke(base, args);
            };
            return (VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                    VertexConsumer.class.getClassLoader(),
                    new Class<?>[]{VertexConsumer.class}, ih);
        } catch (Throwable ignored) {
            return base;
        }
    }

    // Dynamic proxy that overrides color and remaps raw UVs with scroll/scale for a direct texture (non-atlas)
    private static VertexConsumer forceFixedColorAndUV(VertexConsumer base,
                                                       int r, int g, int b, int a,
                                                       float scaleX, float scaleY, float scrollX, float scrollY, float timeSec) {
        try {
            final int fr = Math.max(0, Math.min(255, r));
            final int fg = Math.max(0, Math.min(255, g));
            final int fb = Math.max(0, Math.min(255, b));
            final int fa = Math.max(0, Math.min(255, a));
            final float sX = scaleX;
            final float sY = scaleY;
            final float scX = scrollX;
            final float scY = scrollY;
            final float t = timeSec;
            java.lang.reflect.InvocationHandler ih = (proxy, method, args) -> {
                if (args != null) {
                    String name = method.getName();
                    // color override: support both int,int,int,int and float,float,float,float
                    if ("color".equals(name) && args.length == 4) {
                        try {
                            // Choose based on runtime arg types to avoid static signature assumptions
                            if (args[0] instanceof Float || args[0] instanceof Double) {
                                float rf = fr / 255f, gf = fg / 255f, bf = fb / 255f, af = fa / 255f;
                                return method.invoke(base, rf, gf, bf, af);
                            } else {
                                return method.invoke(base, fr, fg, fb, fa);
                            }
                        } catch (Throwable ignored) {}
                    }
                    // uv override: mappings vary between texture(u,v) and uv(u,v)
                    if (("texture".equals(name) || "uv".equals(name)) && args.length == 2) {
                        try {
                            float u = ((Number) args[0]).floatValue();
                            float v = ((Number) args[1]).floatValue();
                            float lu = u * sX + scX * t;
                            float lv = v * sY + scY * t;
                            // Wrap to [0,1)
                            lu = lu - (float)Math.floor(lu);
                            lv = lv - (float)Math.floor(lv);
                            return method.invoke(base, lu, lv);
                        } catch (Throwable ignored) {}
                    }
                    // Packed vertex path: vertex(..., r,g,b,a, u,v, overlay, light, nx,ny,nz)
                    if ("vertex".equals(name) && args.length >= 14) {
                        try {
                            // Force color (floats 0..1)
                            args[3] = fr / 255f;
                            args[4] = fg / 255f;
                            args[5] = fb / 255f;
                            args[6] = fa / 255f;
                            // Transform UVs at positions 8,9
                            float u = ((Number) args[8]).floatValue();
                            float v = ((Number) args[9]).floatValue();
                            float lu = u * sX + scX * t;
                            float lv = v * sY + scY * t;
                            lu = lu - (float)Math.floor(lu);
                            lv = lv - (float)Math.floor(lv);
                            args[8] = lu;
                            args[9] = lv;
                            return method.invoke(base, args);
                        } catch (Throwable ignored) {}
                    }
                }
                return method.invoke(base, args);
            };
            return (VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                    VertexConsumer.class.getClassLoader(),
                    new Class<?>[]{VertexConsumer.class}, ih);
        } catch (Throwable ignored) {
            return base;
        }
    }

    // Dynamic proxy that overrides only color() to a fixed RGBA, delegating all other calls untouched.
    private static VertexConsumer forceFixedColor(VertexConsumer base, int r, int g, int b, int a) {
        try {
            final int fr = Math.max(0, Math.min(255, r));
            final int fg = Math.max(0, Math.min(255, g));
            final int fb = Math.max(0, Math.min(255, b));
            final int fa = Math.max(0, Math.min(255, a));
            java.lang.reflect.InvocationHandler ih = (proxy, method, args) -> {
                if (args != null) {
                    String name = method.getName();
                    if ("color".equals(name) && args.length == 4) {
                        try {
                            if (args[0] instanceof Float || args[0] instanceof Double) {
                                float rf = fr / 255f, gf = fg / 255f, bf = fb / 255f, af = fa / 255f;
                                return method.invoke(base, rf, gf, bf, af);
                            } else {
                                return method.invoke(base, fr, fg, fb, fa);
                            }
                        } catch (Throwable ignored) {}
                    }
                    // Packed vertex with inline color floats
                    if ("vertex".equals(name) && args.length >= 14) {
                        try {
                            args[3] = fr / 255f;
                            args[4] = fg / 255f;
                            args[5] = fb / 255f;
                            args[6] = fa / 255f;
                            return method.invoke(base, args);
                        } catch (Throwable ignored) {}
                    }
                }
                return method.invoke(base, args);
            };
            return (VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                    VertexConsumer.class.getClassLoader(),
                    new Class<?>[]{VertexConsumer.class}, ih);
        } catch (Throwable ignored) {
            return base;
    }
    }
}
