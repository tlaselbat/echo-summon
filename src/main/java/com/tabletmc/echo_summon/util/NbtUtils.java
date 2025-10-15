package com.tabletmc.echo_summon.util;

import net.minecraft.nbt.NbtCompound;

public final class NbtUtils {
    private NbtUtils() {}

    public static String getString(NbtCompound comp, String key) {
        if (comp == null || key == null) return "";
        try {
            // Reflection to tolerate different signatures (Optional<String> or String)
            java.lang.reflect.Method m = NbtCompound.class.getMethod("getString", String.class);
            Object result = m.invoke(comp, key);
            if (result instanceof java.util.Optional<?> opt) {
                Object v = opt.isPresent() ? opt.get() : "";
                return v != null ? v.toString() : "";
            }
            if (result != null) return result.toString();
        } catch (ReflectiveOperationException ignored) {
        } catch (Throwable ignored) {
        }
        return "";
    }
}
