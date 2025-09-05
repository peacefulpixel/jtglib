package com.example.lib.context;

public class ExistingMessageContext extends InteractiveMessageContext {
    public final Integer messageId;

    public ExistingMessageContext(com.example.lib.TgChat chat, Integer messageId) {
        super(chat);
        this.messageId = messageId;
    }

    public static ExistingMessageContext of(InteractiveMessageContext oldContext, Integer messageId) {
        final var context = new ExistingMessageContext(oldContext.chat, messageId);
        context.putAll(oldContext);
        return context;
    }
}
