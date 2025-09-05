package com.example.lib.model;

import com.pengrad.telegrambot.model.OrderInfo;
import com.pengrad.telegrambot.model.PreCheckoutQuery;
import com.pengrad.telegrambot.model.SuccessfulPayment;

public class CheckoutInfo {
    public final String payload;
    public final Integer amount;
    public final OrderInfo orderInfo;
    public final String currency;

    public CheckoutInfo(String payload, Integer amount, OrderInfo orderInfo, String currency) {
        this.payload = payload;
        this.amount = amount;
        this.orderInfo = orderInfo;
        this.currency = currency;
    }

    public static CheckoutInfo of(PreCheckoutQuery preCheckoutQuery) {
        return new CheckoutInfo(preCheckoutQuery.invoicePayload(), preCheckoutQuery.totalAmount(),
                preCheckoutQuery.orderInfo(), preCheckoutQuery.currency());
    }

    public static CheckoutInfo of(SuccessfulPayment successfulPayment) {
        return new CheckoutInfo(successfulPayment.invoicePayload(), successfulPayment.totalAmount(),
                successfulPayment.orderInfo(), successfulPayment.currency());
    }
}
