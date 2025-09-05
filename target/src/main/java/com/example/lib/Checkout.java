package com.example.lib;

import com.pengrad.telegrambot.request.AnswerPreCheckoutQuery;
import com.example.lib.model.CheckoutInfo;

public class Checkout {
    private final String id;
    private final TgBot bot;
    public final CheckoutInfo info;

    public Checkout(String id, TgBot bot, CheckoutInfo info) {
        this.id = id;
        this.bot = bot;
        this.info = info;
    }

    public void approve() {
        bot.sendRequest(new AnswerPreCheckoutQuery(id));
    }

    public void deny() {
        deny("");
    }

    public void deny(String reason) {
        bot.sendRequest(new AnswerPreCheckoutQuery(id, reason));
    }
}
