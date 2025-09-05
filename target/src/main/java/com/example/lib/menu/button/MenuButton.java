package com.example.lib.menu.button;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.example.lib.context.InteractiveMessageContext;

public class MenuButton {
    public final String title;

    public MenuButton(String title) {
        this.title = title;
    }

    public InlineKeyboardButton getInlineKeyboardButton(InteractiveMessageContext context) {
        return new InlineKeyboardButton(title);
    }
}
