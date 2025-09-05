package com.example.lib;

import com.example.lib.internals.CallbackHandler;
import com.example.lib.menu.MenuInfoSupplier;
import com.example.lib.model.CheckoutInfo;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.response.BaseResponse;
import org.apache.commons.lang3.function.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.example.lib.menu.CallbackReference.TAG_PAYLOAD_SPLIT_STRING;

public class TgBot {
    private static final Logger log = LoggerFactory.getLogger(TgBot.class);

    private final TelegramBot telegramBot;
    private final Map<Long, TgChat> chats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CallbackHandler> callbackDataHandlers = new ConcurrentHashMap<>();

    private String helpMessage = "core.helpMessage";

    private MenuInfoSupplier startMenu;
    private Consumer<TgChat> onChatInitialized;
    private BiConsumer<Long, Checkout> onCheckout;
    private TriConsumer<Long, CheckoutInfo, String> onSuccessfulCheckout;
    private BiConsumer<TgChat, Throwable> onUpdateHandleError;

    public TgBot(String apiKey) {
        telegramBot = new TelegramBot(apiKey);
    }

    public String getHelpMessage() {
        return helpMessage;
    }

    public void setHelpMessage(String helpMessage) {
        this.helpMessage = helpMessage;
    }

    public MenuInfoSupplier getStartMenu() {
        return startMenu;
    }

    public void setStartMenu(MenuInfoSupplier startMenu) {
        this.startMenu = startMenu;
    }

    public void setOnChatInitialized(Consumer<TgChat> onChatInitialized) {
        this.onChatInitialized = onChatInitialized;
    }

    public void setOnCheckout(BiConsumer<Long, Checkout> onCheckout) {
        this.onCheckout = onCheckout;
    }

    public BiConsumer<TgChat, Throwable> getOnUpdateHandleError() {
        return onUpdateHandleError;
    }

    public void setOnUpdateHandleError(BiConsumer<TgChat, Throwable> onUpdateHandleError) {
        this.onUpdateHandleError = onUpdateHandleError;
    }

    public void setOnSuccessfulCheckout(TriConsumer<Long, CheckoutInfo, String> onSuccessfulCheckout) {
        this.onSuccessfulCheckout = onSuccessfulCheckout;
    }

    public void startListen() {
        telegramBot.setUpdatesListener(updates -> {
            processUpdatesNoThrow(updates);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    public <T extends BaseResponse> T sendRequest(BaseRequest<?, T> request) {
        log.debug("Sending TG bot request, {}: {}", request.getClass(), request.getParameters().get("text"));
        final T response = telegramBot.execute(request);
        if (!response.isOk()) {
            log.error("FAIL RESPONSE FOR {}: {}: {}", request.getClass(),
                    response.getClass(), response.description());
            throw new RuntimeException("Request failed: " + response);
        }

        return response;
    }

    public InputHandler getDefaultInputHandler() {
        return (chat, msg) -> {
            log.debug("Input: {}", msg);
            switch (msg) {
                case "/start" -> {
                    if (getStartMenu() == null) {
                        chat.sendLocalizedMessage("core.noMenuAvailable");
                    } else {
                        chat.sendMenu(getStartMenu());
                    }
                }
                case "/help" -> chat.sendLocalizedMessage(getHelpMessage());
                default ->
                        chat.sendLocalizedMessage("core.unknownCommand", new ReplyKeyboardRemove());
            }

            return null;
        };
    }

    /**
     * Scanning package and looking for child types of ${@link CallbackHandler}. If these classes have no-args
     * constructor, they're being instanced and registered with this method.
     * @param packageToScan package to scan recursively
     */
    public void registerCallbackHandlers(String packageToScan) {
        final var ref = new Reflections(packageToScan, Scanners.SubTypes);
        final var set = ref.getSubTypesOf(CallbackHandler.class);

        for (var callbackClass : set) {
            log.info("Registering {}", callbackClass.toString());
            try {
                final var instance = callbackClass.getConstructor().newInstance();
                registerCallbackHandler(instance);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {

                log.error("Unable to create instance of {} for menu button registration", callbackClass, e);
            }
        }
    }

    public <T extends CallbackHandler> void registerCallbackHandler(T callbackHandler) {
        if (callbackDataHandlers.containsKey(callbackHandler.tag))
            throw new RuntimeException("This callback handler is already registered.");

        callbackDataHandlers.put(callbackHandler.tag, callbackHandler);
    }

    /* Never throws */
    private void processUpdatesNoThrow(List<Update> updates) {
        for (Update u : updates) {
            try {
                processUpdate(u);
            } catch (Throwable t) {
                log.error("An error during processing update {}", u.toString(), t);
            }
        };
    }

    private void processUpdate(Update update) {
        if (update.preCheckoutQuery() != null) {
            processMessage(update.preCheckoutQuery());
        }

        if (update.message() != null) {
            processMessage(update.message());
        }

        if (update.callbackQuery() != null) {
            processMessage(update.callbackQuery());
        }
    }

    @NotNull
    private TgChat getChat(Chat chat, User user) {
        if (user == null)
            throw new RuntimeException("Message user can't be null");

        return getChat(chat.id(), user.id());
    }

    @NotNull
    protected TgChat getChat(Long chatId, Long userId) {
        return chats.computeIfAbsent(chatId, __ -> {
            final var newChat = new TgChat(userId, chatId, this);
            if (onChatInitialized != null)
                onChatInitialized.accept(newChat);

            return newChat;
        });
    }

    protected Optional<TgChat> getChat(Long userId) {
        return chats.values().stream()
                .filter(c -> c.clientId.equals(userId))
                .findAny();
    }

    private void processMessage(PreCheckoutQuery preCheckoutQuery) {
        if (onCheckout == null) {
            log.warn("Unable to handle checkout: Handler isn't initialized");
            return;
        }

        final var userId = preCheckoutQuery.from().id();
        final var checkout = new Checkout(preCheckoutQuery.id(), this, CheckoutInfo.of(preCheckoutQuery));

        onCheckout.accept(userId, checkout);
    }

    private void processMessage(Message message) {
        final var chat = getChat(message.chat(), message.from());

        final var successfulPayment = message.successfulPayment();
        if (message.successfulPayment() != null) {
            if (onSuccessfulCheckout == null) {
                log.warn("Unable to handle successful checkout: Handler isn't initialized");
                return;
            }

            onSuccessfulCheckout.accept(chat.clientId, CheckoutInfo.of(successfulPayment),
                    successfulPayment.providerPaymentChargeId());
        }

        if (message.text() == null) {
            if (successfulPayment == null)
                chat.sendLocalizedMessage("core.notTextError");

            return;
        }

        try {
            chat.handleInput(message.text());
        } catch (Throwable t) {
            if (onUpdateHandleError != null) {
                onUpdateHandleError.accept(chat, t);
            }
        }
    }

    private void processMessage(CallbackQuery callbackQuery) {
        final Chat tgChat;
        final Integer msgId;
        try {
            final Field f = callbackQuery.getClass().getDeclaredField("message");
            if (f.canAccess(callbackQuery) || f.trySetAccessible()) {
                final MaybeInaccessibleMessage message = (MaybeInaccessibleMessage) f.get(callbackQuery);
                tgChat = message.chat();
                msgId = message.messageId();
            } else throw new RuntimeException("Unable to extract chat from query: " + callbackQuery);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final var chat = getChat(tgChat, callbackQuery.from());

        try {
            if (!handleCallbackData(callbackQuery.data(), chat, msgId)) {
                log.error("An issue while processing callback: {}. " +
                        "This may be caused by server restart so message context where reinitialized.", callbackQuery.data());
                chat.editMessage(msgId, chat.vocabulary.get("core.rottenMenu"));
            }

            sendRequest(new AnswerCallbackQuery(callbackQuery.id()));
        }  catch (Throwable t) {
            if (onUpdateHandleError != null) {
                onUpdateHandleError.accept(chat, t);
            }
        }
    }

    private boolean handleCallbackData(String callbackData, TgChat chat, Integer msgId) {
        final var split = callbackData.split(TAG_PAYLOAD_SPLIT_STRING, 2);
        final var tag = split[0];
        final var payload = Base64.getDecoder().decode(
                split[1].getBytes(StandardCharsets.UTF_8)
        );

        if (!callbackDataHandlers.containsKey(tag))
            return false;

        final var handler = callbackDataHandlers.get(tag);
        final var context = chat.getContextFor(msgId);
        context.setPayload(payload);

        handler.accept(context);

        return true;
    }
}
