/**
 * This package contains classes related to payload handling in the network protocol.
 */
package com.tabletmc.echo_summon.net.payload;

import com.tabletmc.echo_summon.ModConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * This class represents a custom payload for string data in the network protocol.
 * It is responsible for encoding and decoding string payloads.
 */
public record StringPayload(String stringPayload) implements CustomPayload {

    /**
     * This field stores the unique identifier for this payload type.
     * It is used to identify the type of payload being sent or received.
     */
    public static final Id<StringPayload> PACKET_ID = new Id<>(ModConstants.Id("string_payload"));

    /**
     * This field stores the codec for encoding and decoding this payload type.
     * It is used to convert the string payload to and from a byte buffer.
     */
    public static final PacketCodec<RegistryByteBuf, StringPayload> PACKET_CODEC = PacketCodec.of(
            StringPayload::write,
            StringPayload::read
    );
    /**
     * This method is responsible for encoding the string payload into a byte buffer.
     *
     * @param value The string payload to be encoded
     * @param buf   The byte buffer to write the encoded payload to
     */
    public static void write(StringPayload value, RegistryByteBuf buf) {
        // Write the string payload to the byte buffer
        buf.writeString(value.stringPayload);
    }
    /**
     * This method is responsible for decoding the byte buffer into a string payload.
     *
     * @param buf The byte buffer to read the encoded payload from
     * @return The decoded string payload
     */
    public static StringPayload read(RegistryByteBuf buf) {
        // Read the string payload from the byte buffer
        return new StringPayload(buf.readString());
    }
/**
 * Returns a string representation of the StringPayload object.
 * The string representation includes the class name and the string payload,
 * surrounded by single quotes.
 *
 * @return The string representation of the object.
 */
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

    /**
     * This method returns the unique identifier for this payload type.
     *
     * @return the packet ID
     */
    @Override
    public CustomPayload.Id<StringPayload> getId() {
        return PACKET_ID;
    }
}