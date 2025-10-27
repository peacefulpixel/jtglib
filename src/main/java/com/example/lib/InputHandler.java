package com.example.lib;

@FunctionalInterface
public interface InputHandler {
    InputHandler handleInput(TgChat chat, String input);

    static InputHandler handle(InputHandler handler, TgChat chat, String input, MessageInfo messageInfo) {
        if (handler instanceof ExtendedInputHandler ext) {
            if (messageInfo != null)
                return ext.handleInput(chat, messageInfo, input);

            return ext.handleInput(chat, input);
        }

        return handler.handleInput(chat, input);
    }

    interface ExtendedInputHandler extends InputHandler {
        InputHandler handleInput(TgChat chat, MessageInfo messageInfo, String input);
    }

    record MessageInfo(boolean isForward, Long forwardChatId) { }
}
