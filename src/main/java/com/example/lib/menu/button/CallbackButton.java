package com.example.lib.menu.button;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.example.lib.context.InteractiveMessageContext;
import com.example.lib.menu.CallbackReference;

public class CallbackButton<T extends CallbackReference> extends MenuButton {
    public final T reference;

    public CallbackButton(String title, T reference) {
        super(title);
        this.reference = reference;
    }

    @Override
    public InlineKeyboardButton getInlineKeyboardButton(InteractiveMessageContext context) {
        return super.getInlineKeyboardButton(context)
                .callbackData(
                        reference.toTelegramCallbackData()
                );
    }

    public static CallbackButton<CallbackReference> simple(String title, String tag) {
        return new CallbackButton<>(title, new CallbackReference(tag));
    }

    public static CallbackButton<CallbackReference> payload(String title, String tag, Number payload) {
        return new CallbackButton<>(title, new CallbackReference(tag).withPayload(payload));
    }

    public static CallbackButton<CallbackReference> payload(String title, String tag, String payload) {
        return new CallbackButton<>(title, new CallbackReference(tag).withPayload(payload));
    }
}
