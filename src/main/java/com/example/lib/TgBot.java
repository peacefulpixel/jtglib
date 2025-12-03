package com.example.lib;

import com.example.lib.internals.CallbackHandler;
import com.example.lib.menu.MenuInfoSupplier;
import com.example.lib.model.CheckoutInfo;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.pengrad.telegrambot.model.MessageEntity;
import com.pengrad.telegrambot.model.message.origin.MessageOriginChannel;
import com.pengrad.telegrambot.model.message.origin.MessageOriginChat;
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.response.GetFileResponse;
import com.pengrad.telegrambot.response.BaseResponse;
import org.apache.commons.lang3.function.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    private Consumer<ChatMemberUpdated> onChatMemberUpdated;
    private Consumer<ChatJoinRequest> onChatJoinRequest;

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

    public void setOnChatMemberUpdated(Consumer<ChatMemberUpdated> onChatMemberUpdated) {
        this.onChatMemberUpdated = onChatMemberUpdated;
    }

    public void setOnChatJoinRequest(Consumer<ChatJoinRequest> onChatJoinRequest) {
        this.onChatJoinRequest = onChatJoinRequest;
    }

    public void startListen() {
        final var updateList = new ArrayList<>(List.of("message", "callback_query", "pre_checkout_query"));

        if (onChatMemberUpdated != null)
            updateList.add("chat_member");

        if (onChatJoinRequest != null)
            updateList.add("chat_join_request");

        final var rq = new GetUpdates();
        rq.allowedUpdates(updateList.toArray(new String[]{}));

        telegramBot.setUpdatesListener(updates -> {
            processUpdatesNoThrow(updates);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }, rq);
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

        if (update.chatMember() != null && onChatMemberUpdated != null) {
            onChatMemberUpdated.accept(update.chatMember());
        }

        if (update.chatJoinRequest() != null && onChatJoinRequest != null) {
            onChatJoinRequest.accept(update.chatJoinRequest());
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

        final var msgInfo = extractMessageInfo(message);
        final var inputText = formatWithEntities(message);

        if (inputText.isEmpty() && (msgInfo.attachments() == null || msgInfo.attachments().isEmpty()) && !msgInfo.isForward()) {
            if (successfulPayment == null)
                chat.sendLocalizedMessage("core.notTextError");

            return;
        }

        try {
            chat.handleInput(msgInfo, inputText);
        } catch (Throwable t) {
            if (onUpdateHandleError != null) {
                onUpdateHandleError.accept(chat, t);
            }
        }
    }

    private InputHandler.MessageInfo extractMessageInfo(Message message) {
        final var attachments = extractAttachments(message);
        final var origin = message.forwardOrigin();

        if (origin instanceof MessageOriginChat chat)
            return new InputHandler.MessageInfo(true, chat.senderChat().id(), attachments);

        if (origin instanceof MessageOriginChannel channel)
            return new InputHandler.MessageInfo(true, channel.chat().id(), attachments);

        return new InputHandler.MessageInfo(false, null, attachments);
    }

    private List<InputHandler.Attachment> extractAttachments(Message message) {
        final var list = new ArrayList<InputHandler.Attachment>();

        if (message.animation() != null) {
            final var data = downloadFile(message.animation().fileId());
            list.add(new InputHandler.Attachment(false, true, data, mimeFromPath(message.animation().fileName())));
            return list;
        }

        if (message.video() != null) {
            final var data = downloadFile(message.video().fileId());
            list.add(new InputHandler.Attachment(true, false, data, mimeFromPath(message.video().fileName())));
            return list;
        }

        if (message.photo() != null && message.photo().length > 0) {
            final var sizes = message.photo();
            final var best = sizes[sizes.length - 1];
            final var data = downloadFile(best.fileId());
            list.add(new InputHandler.Attachment(false, false, data, mimeFromPath(best.fileId())));
            return list;
        }

        if (message.document() != null && message.document().mimeType() != null &&
                message.document().mimeType().startsWith("image")) {
            final var data = downloadFile(message.document().fileId());
            list.add(new InputHandler.Attachment(false, false, data, message.document().mimeType()));
        }

        return list;
    }

    private byte[] downloadFile(String fileId) {
        final GetFileResponse rs = telegramBot.execute(new GetFile(fileId));
        final var file = rs.file();
        try {
            return telegramBot.getFileContent(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String mimeFromPath(String path) {
        if (path == null) return "application/octet-stream";
        final var lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        return "application/octet-stream";
    }

    private String formatWithEntities(Message message) {
        final var text = message.text() != null ? message.text() :
                (message.caption() != null ? message.caption() : "");
        if (text == null)
            return "";

        final var entities = message.entities() != null ? message.entities() : message.captionEntities();
        if (entities == null || entities.length == 0)
            return escapeHtml(text);

        final var events = new TreeMap<Integer, Boundary>();
        for (MessageEntity e : entities) {
            final int start = Math.min(e.offset(), text.length());
            final int end = Math.min(e.offset() + e.length(), text.length());
            events.computeIfAbsent(start, __ -> new Boundary()).starts.add(e);
            events.computeIfAbsent(end, __ -> new Boundary()).ends.add(e);
        }

        final var sb = new StringBuilder();
        final var stack = new ArrayList<MessageEntity>();
        int cursor = 0;
        for (var entry : events.entrySet()) {
            final int pos = entry.getKey();
            if (cursor < pos) {
                sb.append(escapeHtml(text.substring(cursor, pos)));
            }

            // Close entities that end here (from inner to outer)
            final var toClose = entry.getValue().ends;
            if (!toClose.isEmpty()) {
                final var closeSet = new HashSet<>(toClose);
                for (int i = stack.size() - 1; i >= 0; i--) {
                    if (closeSet.contains(stack.get(i))) {
                        sb.append(closeTag(stack.remove(i)));
                    }
                }
            }

            // Open entities that start here (outer first -> longer first)
            final var toOpen = entry.getValue().starts.stream()
                    .sorted(Comparator.<MessageEntity>comparingInt(e -> e.length()).reversed())
                    .toList();
            for (MessageEntity e : toOpen) {
                stack.add(e);
                sb.append(openTag(e));
            }

            cursor = pos;
        }

        if (cursor < text.length()) {
            sb.append(escapeHtml(text.substring(cursor)));
        }

        // Close any remaining entities
        for (int i = stack.size() - 1; i >= 0; i--) {
            sb.append(closeTag(stack.get(i)));
        }

        return sb.toString();
    }

    private String openTag(MessageEntity e) {
        return switch (e.type()) {
            case bold -> "<b>";
            case italic -> "<i>";
            case underline -> "<u>";
            case strikethrough -> "<s>";
            case spoiler -> "<span class=\"tg-spoiler\">";
            case code -> "<code>";
            case pre -> "<pre>";
            case text_link -> "<a href=\"" + escapeHtml(e.url()) + "\">";
            default -> "";
        };
    }

    private String closeTag(MessageEntity e) {
        return switch (e.type()) {
            case bold -> "</b>";
            case italic -> "</i>";
            case underline -> "</u>";
            case strikethrough -> "</s>";
            case spoiler -> "</span>";
            case code -> "</code>";
            case pre -> "</pre>";
            case text_link -> "</a>";
            default -> "";
        };
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static class Boundary {
        List<MessageEntity> starts = new ArrayList<>();
        List<MessageEntity> ends = new ArrayList<>();
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
