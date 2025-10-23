package com.tabletmc.echo_summon.net.payload;

import com.tabletmc.echo_summon.ModConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.Locale;

public record StringPayload(String stringPayload) implements CustomPayload {

    public StringPayload {
        stringPayload = sanitize(stringPayload);
    }

    public static final Id<StringPayload> PACKET_ID = new Id<>(ModConstants.Id("string_payload"));

    public static final PacketCodec<RegistryByteBuf, StringPayload> PACKET_CODEC = PacketCodec.of(
            StringPayload::write,
            StringPayload::read
    );
    public static void write(StringPayload value, RegistryByteBuf buf) {
        // Write the string payload to the byte buffer
        buf.writeString(sanitize(value.stringPayload));
    }
    public static StringPayload read(RegistryByteBuf buf) {
        // Read the string payload from the byte buffer
        String raw = buf.readString(ModConstants.MAX_PACKET_STRING_LENGTH);
        return new StringPayload(raw);
    }
    @Override
    public String toString() {
        // Start building the string representation with the class name
        String result = "StringPayload{";

        // Include the string payload, surrounded by single quotes
        // This is done to clearly distinguish the payload from other parts of the string
        result += "stringPayload='" + stringPayload + "'";

        // Close the string representation
        result += "}";

        // Return the complete string representation
        return result;
    }

    @Override
    public CustomPayload.Id<StringPayload> getId() {
        return PACKET_ID;
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!ModConstants.SAFE_STRING_PAYLOAD.matcher(normalized).matches()) {
            return "";
        }
        return normalized;
    }
}