package com.example.lib;

import com.example.lib.TgChat;

@FunctionalInterface
public interface InputHandler {
    InputHandler handleInput(TgChat chat, String input);
}
