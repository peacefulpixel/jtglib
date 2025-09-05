package com.example.lib.internals;

import com.example.lib.context.ExistingMessageContext;
import com.example.lib.context.InteractiveMessageContext;
import com.example.lib.menu.CallbackReference;
import com.example.lib.menu.MenuInfoSupplier;

import java.util.function.Consumer;

public abstract class CallbackHandler implements Consumer<InteractiveMessageContext> {
    public final String tag;

    protected CallbackHandler(String tag) {
        this.tag = tag;
    }

    public CallbackReference createReference() {
        return new CallbackReference(tag);
    }

    /**
     * sending a new menu or editing already existing one
     */
    protected static void respondWithMenu(InteractiveMessageContext context, MenuInfoSupplier menuInfoSupplier) {
        if (context instanceof ExistingMessageContext ctx) {
            ctx.chat.editMenu(ctx.messageId, menuInfoSupplier);
        } else {
            context.chat.sendMenu(menuInfoSupplier);
        }
    }
}
