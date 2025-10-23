package com.tabletmc.echo_summon.util;

import net.minecraft.nbt.NbtCompound;

public final class NbtUtils {
    private NbtUtils() {}

    public static String getString(NbtCompound comp, String key) {
        if (comp == null || key == null) return "";
        try {
            // If the key is missing, return empty
            if (!comp.contains(key)) return "";
        } catch (Throwable ignored) {}

        try {
            // Minecraft 1.21+ NbtCompound#getString returns Optional<String>
            String s = "";
            try {
                java.util.Optional<String> opt = comp.getString(key);
                if (opt != null && opt.isPresent()) {
                    s = opt.get();
                }
            } catch (Throwable ignored2) {
                // Older mappings may differ; ignore and keep default "" if unavailable.
            }
            /// Sanitize any legacy values mistakenly serialized as "Optional[... ]"
            if (s.startsWith("Optional[") && s.endsWith("]")) {
                s = s.substring("Optional[".length(), s.length() - 1);
            }
            return s;
        } catch (Throwable ignored) {}
        return "";
    }
}
