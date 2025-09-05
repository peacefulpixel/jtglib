package com.example.lib.menu;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CallbackReference {
    public static final String TAG_PAYLOAD_SPLIT_STRING = "%";
    public static final int TG_CALLBACK_DATA_SIZE_LIMIT = 64;

    public final String tag;

    private final ByteBuffer payload = ByteBuffer.allocate(TG_CALLBACK_DATA_SIZE_LIMIT);

    public CallbackReference(String tag) {
        this.tag = tag;
    }

    public String toTelegramCallbackData() {
        final var callbackData =
                tag + TAG_PAYLOAD_SPLIT_STRING +
                new String(
                        Base64.getEncoder().encode(
                                // Changing bytebuffer size to amount of filled bytes
                                ByteBuffer.allocate(payload.position())
                                        .put(payload.array(), 0, payload.position())
                                        .array()
                        ),
                        StandardCharsets.UTF_8
                );

        if (callbackData.length() >= TG_CALLBACK_DATA_SIZE_LIMIT)
            throw new RuntimeException("This callback reference couldn't be applied to an inline button due to " +
                    "telegram callback data limits. Use less payload or either make tag shorter");

        return callbackData;
    }

    @SafeVarargs
    public final <T extends Number> CallbackReference withPayload(T... payload) {
        final var newRef = new CallbackReference(tag);

        for (T t : payload) {
            switch (t) {
                case Integer i -> newRef.payload.putInt(i);
                case Long l    -> newRef.payload.putLong(l);
                case Double d  -> newRef.payload.putDouble(d);
                case Float f   -> newRef.payload.putFloat(f);
                case Short s   -> newRef.payload.putShort(s);
                case Byte b    -> newRef.payload.put(b);
                case null, default -> throw new RuntimeException("Current type couldn't be written to a ByteBuffer");
            }
        }

        return newRef;
    }

    public final CallbackReference withPayload(String payload) {
        final var newRef = new CallbackReference(tag);
        newRef.payload.put(payload.getBytes(StandardCharsets.UTF_8));
        return newRef;
    }
}
