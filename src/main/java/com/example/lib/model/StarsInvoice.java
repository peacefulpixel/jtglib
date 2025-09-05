package com.example.lib.model;

import com.pengrad.telegrambot.model.request.LabeledPrice;

import java.util.List;

public class StarsInvoice extends Invoice {

    public StarsInvoice(String title, String description, String payload, Integer price) {
        super(title, description, payload, "", "XTR", List.of(new LabeledPrice(title, price)));
    }
}
