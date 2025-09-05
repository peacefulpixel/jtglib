package com.example.lib.model;

import com.pengrad.telegrambot.model.request.LabeledPrice;
import java.util.Collection;
import java.util.LinkedList;

public class Invoice {
    public String title;
    public String description;
    public String payload;
    public String providerToken;
    public String currency;
    public Collection<LabeledPrice> prices = new LinkedList<>();

    public Invoice(String title, String description, String payload, String providerToken, String currency,
                   Collection<LabeledPrice> prices) {
        this.title = title;
        this.description = description;
        this.payload = payload;
        this.providerToken = providerToken;
        this.currency = currency;
        this.prices = prices;
    }

    public Invoice() {

    }
}
