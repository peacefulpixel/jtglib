package com.example.lib.menu;

import com.example.lib.menu.button.MenuButton;

import java.util.List;

public class MenuInfo {
    public final String message;
    public final List<List<MenuButton>> buttons;

    public MenuInfo(String message, List<List<MenuButton>> buttons) {
        this.message = message;
        this.buttons = buttons;
    }

    public String getMessage() {
        return message;
    }

    public List<List<MenuButton>> getButtons() {
        return buttons;
    }

    public static MenuInfo of(String message, List<List<MenuButton>> buttons) {
        return new MenuInfo(message, buttons);
    }
}
