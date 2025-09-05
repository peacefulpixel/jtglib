package com.example.lib.context;

import com.example.lib.menu.MenuInfoSupplier;
import com.example.lib.TgChat;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class InteractiveMessageContext extends HashMap<String, Object> {
    public final TgChat chat;

    public MenuInfoSupplier latestMenuSupplier;

    private byte[] payload;

    public InteractiveMessageContext(TgChat chat) {
        this.chat = chat;
    }


    public ByteBuffer getPayload() {
        if (payload == null)
            return ByteBuffer.allocate(0);

        return ByteBuffer.wrap(payload);
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
}
