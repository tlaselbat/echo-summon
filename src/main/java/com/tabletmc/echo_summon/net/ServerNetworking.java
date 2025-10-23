package com.tabletmc.echo_summon.net;

import com.tabletmc.echo_summon.ModConstants;
import com.tabletmc.echo_summon.net.payload.StringPayload;
import com.tabletmc.echo_summon.net.service.SaddleSummonService;
import com.tabletmc.echo_summon.net.service.SummonToolLookup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Registers and handles server-side networking for the saddle summon tool actions.
 *
 * Suppresses all player chat feedback for summon/dismiss/release/capture.
 * Validation and logging remain intact.
 */
public final class ServerNetworking {
    private ServerNetworking() {}

    private static final Set<String> SIMPLE_SADDLE_ACTIONS = Set.of(
            "saddle_dismiss",
            "saddle_summon",
            "saddle_release"
    );
    private static final String SADDLE_CAPTURE_PREFIX = "saddle_capture:";
    private static final Pattern CAPTURE_ID_PATTERN = Pattern.compile("^[0-9a-f-]{1,64}$");
    private static final long INVALID_LOG_INTERVAL_MS = 5_000L;
    private static final AtomicLong LAST_INVALID_LOG = new AtomicLong();

    public static void init() {
        PayloadTypeRegistry.playC2S().register(StringPayload.PACKET_ID, StringPayload.PACKET_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StringPayload.PACKET_ID, (StringPayload handler, ServerPlayNetworking.Context context) -> {
            ServerPlayerEntity player = context.player();
            String action = handler.stringPayload();

            if (!isAllowedAction(action)) {
                logInvalidPayload(player, action);
                return;
            }

            if (player != null) {
                var server = player.getServer();
                if (server != null) {
                    server.execute(() -> handleSaddleAction(player, action));
                }
            }
        });
    }

    public static void handleSaddleAutoDismiss(ServerPlayerEntity player, LivingEntity mount) {
        SaddleSummonService.handleAutoDismiss(player, mount);
    }

    private static void handleSaddleAction(ServerPlayerEntity player, String action) {
        if (player == null) {
            return;
        }
        ItemStack summonTool = SummonToolLookup.findSummonToolInHand(player);
        if (summonTool.isEmpty() && "saddle_dismiss".equals(action)) {
            // Allow dismiss even if the tool is not currently in hand by resolving it from the ridden entity tag
            try {
                if (player.hasVehicle() && player.getVehicle() instanceof LivingEntity living) {
                    String toolId = echo_summon$extractSaddleToolId(living);
                    if (!toolId.isEmpty()) {
                        ItemStack resolved = SummonToolLookup.findSaddleSummonToolById(player, toolId);
                        if (!resolved.isEmpty()) {
                            summonTool = resolved;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            // If still empty, fall through
        }
        if (summonTool.isEmpty()) {
            return;
        }
        SaddleSummonService.handleAction(player, summonTool, action);
    }

    /**
     * Small helper to extract the saddle summon tool id from a living entity's command tags.
     *
     * @param living The entity to extract the tool id from.
     * @return The extracted tool id, or an empty string if not found.
     */
    private static String echo_summon$extractSaddleToolId(LivingEntity living) {
        String prefix = ModConstants.SADDLE_TOOL_TAG_PREFIX;
        for (String tag : living.getCommandTags()) {
            if (tag.startsWith(prefix)) {
                String raw = tag.substring(prefix.length());
                if (raw.startsWith("Optional[") && raw.endsWith("]")) {
                    raw = raw.substring("Optional[".length(), raw.length() - 1);
                }
                return raw;
            }
        }
        return "";
    }

    private static boolean isAllowedAction(String action) {
        if (action == null || action.isEmpty()) {
            return false;
        }
        if (SIMPLE_SADDLE_ACTIONS.contains(action)) {
            return true;
        }
        if (action.startsWith(SADDLE_CAPTURE_PREFIX)) {
            String suffix = action.substring(SADDLE_CAPTURE_PREFIX.length());
            return !suffix.isEmpty() && CAPTURE_ID_PATTERN.matcher(suffix).matches();
        }
        return false;
    }

    /**
     * Logs an invalid payload message if the given player and action are not valid.
     *
     * @param player The player that sent the payload.
     * @param action The action that was sent.
     */
    private static void logInvalidPayload(ServerPlayerEntity player, String action) {
        long now = System.currentTimeMillis();
        long prev = LAST_INVALID_LOG.get();
        if (now - prev > INVALID_LOG_INTERVAL_MS && LAST_INVALID_LOG.compareAndSet(prev, now)) {
            String name = player != null ? player.getGameProfile().getName() : "<unknown>";
            ModConstants.LOGGER.warn("[Networking] Rejected payload '{}' from {}", action, name);
        }
    }
}