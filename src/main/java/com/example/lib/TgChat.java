package com.example.lib;

import com.example.lib.context.ExistingMessageContext;
import com.example.lib.context.InteractiveMessageContext;
import com.example.lib.menu.MenuInfoSupplier;
import com.example.lib.menu.button.MenuButton;
import com.example.lib.model.Invoice;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.*;
import com.pengrad.telegrambot.response.SendResponse;
import org.example.lib.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TgChat {
    private static final Logger log = LoggerFactory.getLogger(TgChat.class);

    public final Long clientId;
    public final Long chatId;
    public final TgBot tgBot;

    private final ConcurrentHashMap<Integer, InteractiveMessageContext> messageContexts = new ConcurrentHashMap<>();

    public InputHandler inputHandler = null;
    public Vocabulary vocabulary = Vocabulary.make(Locale.US);

    public TgChat(Long clientId, Long chatId, TgBot tgBot) {
        this.clientId = clientId;
        this.chatId = chatId;
        this.tgBot = tgBot;
    }

    protected SendResponse sendMessageWithInlineMarkup(String message, InlineKeyboardMarkup markup) {
        final SendMessage request = new SendMessage(chatId, message);
        request.parseMode(ParseMode.MarkdownV2);
        request.replyMarkup(markup);
        request.disableWebPagePreview(true);
        return tgBot.sendRequest(request);
    }

    public void sendMessage(String message) {
        final SendMessage request = new SendMessage(chatId, message);
        request.parseMode(ParseMode.MarkdownV2);
        request.disableWebPagePreview(true);
        tgBot.sendRequest(request);
    }

    public void sendLocalizedMessage(String key) {
        sendMessage(vocabulary.get(key));
    }

    public void sendMessage(String message, Keyboard markup) {
        final SendMessage request = new SendMessage(chatId, message);
        request.replyMarkup(markup);
        request.parseMode(ParseMode.MarkdownV2);
        request.disableWebPagePreview(true);
        tgBot.sendRequest(request);
    }

    public void sendLocalizedMessage(String key, Keyboard markup) {
        sendMessage(vocabulary.get(key), markup);
    }

    public void sendImage(byte[] data, String caption) {
        final SendPhoto request = new SendPhoto(chatId, data);
        request.caption(caption);
        request.parseMode(ParseMode.MarkdownV2);
        tgBot.sendRequest(request);
    }

    public void sendMenu(MenuInfoSupplier menu) {
        try {
            final var ctx = new InteractiveMessageContext(this);
            final var future = menu.create(this, ctx);
            ctx.latestMenuSupplier = menu;
            final var menuData = future.get(10, TimeUnit.SECONDS);
            final var keyboardMarkup = createMarkupFromMenu(null, menuData.buttons);
            final var rs = sendMessageWithInlineMarkup(menuData.message, keyboardMarkup);
            messageContexts.put(rs.message().messageId(), ctx);
        } catch (Exception e) {
            log.error("", e);
            sendLocalizedMessage("core.internalError");
        }
    }

    public void sendInvoice(Invoice invoice) {
        final var rq = new SendInvoice(chatId, invoice.title, invoice.description,
                invoice.payload, invoice.providerToken, invoice.currency, invoice.prices.toArray(LabeledPrice[]::new));
        rq.startParameter("start"); // TODO It works weird but at least nobody lose money

        tgBot.sendRequest(rq);
    }

    protected void editMessage(Integer msgId, String message, InlineKeyboardMarkup markup) {
        final EditMessageText request = new EditMessageText(chatId, msgId, message);
        request.disableWebPagePreview(true);
        request.parseMode(ParseMode.MarkdownV2);
        if (markup != null) {
            request.replyMarkup(markup);
        }

        tgBot.sendRequest(request);
    }

    public void editMessage(Integer msgId, String message) {
        editMessage(msgId, message, null);
    }

    public void editImage(Integer msgId, byte[] data) {
        final EditMessageMedia request = new EditMessageMedia(chatId, msgId, new InputMediaPhoto(data));
        tgBot.sendRequest(request);
    }

    public void editMenu(Integer msgId, MenuInfoSupplier newMenu) {
        try {
            final var ctx = getContextFor(msgId);
            final var future = newMenu.create(this, ctx);
            ctx.latestMenuSupplier = newMenu;
            final var menuData = future.get(10, TimeUnit.SECONDS);
            final var keyboardMarkup = createMarkupFromMenu(ctx, menuData.buttons);
            editMessage(msgId, menuData.message, keyboardMarkup);
        } catch (Exception e) {
            log.error("", e);
            sendLocalizedMessage("core.internalError");
        }
    }

    public void removeButtonsFromMessage(Integer msgId) {
        final var request = new EditMessageReplyMarkup(chatId, msgId);
        tgBot.sendRequest(request);
    }

    public synchronized void handleInput(String msg) {
        final InputHandler nextHandler;
        if (inputHandler == null) {
            nextHandler = tgBot.getDefaultInputHandler().handleInput(this, msg);
        } else {
            nextHandler = inputHandler.handleInput(this, msg);
        }

        inputHandler = nextHandler;
    }

    public InteractiveMessageContext getContextFor(Integer msgId) {
        final var ctx = messageContexts.computeIfAbsent(msgId, __ -> new ExistingMessageContext(this, msgId));

        // Upgrading context to an ExistingMessage
        if (!(ctx instanceof ExistingMessageContext)) {
            messageContexts.put(msgId, ExistingMessageContext.of(ctx, msgId));
            return messageContexts.get(msgId);
        }

        return ctx;
    }

    private InlineKeyboardMarkup createMarkupFromMenu(InteractiveMessageContext ctx,
                                                      List<List<MenuButton>> layout) {
        final var mappedRows = layout.stream()
                .map(row -> row.stream()
                        .map(btn -> btn.getInlineKeyboardButton(ctx)).toList()
                        .toArray(new InlineKeyboardButton[] {})
                ).toList();

        final var markup = new InlineKeyboardMarkup();
        for (var row : mappedRows) {
            markup.addRow(row);
        }

        return markup;
    }
}
