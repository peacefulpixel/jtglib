package com.example.lib.menu;

import com.example.lib.TgChat;
import com.example.lib.context.InteractiveMessageContext;
import com.example.lib.menu.button.MenuButton;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class MenuInfoSupplier {
    public abstract CompletableFuture<MenuInfo> create(
            TgChat targetChat, InteractiveMessageContext context);

    protected static final class ButtonLayoutBuilder {
        private final LinkedList<LinkedList<MenuButton>> layout = new LinkedList<>();

        public static ButtonLayoutBuilder create() {
            return new ButtonLayoutBuilder();
        }

        public ButtonLayoutBuilder row(MenuButton... buttons) {
            return row(List.of(buttons));
        }

        public ButtonLayoutBuilder row(Collection<? extends MenuButton> buttons) {
            layout.add(new LinkedList<>(buttons));
            return this;
        }

        public List<List<MenuButton>> build() {
            return layout.stream()
                    .filter(i -> i != null && !i.isEmpty())
                    .map(i -> (List<MenuButton>) i)
                    .toList();
        }
    }
}
