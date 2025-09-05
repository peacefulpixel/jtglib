package com.example.lib;

import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove;
import com.example.lib.TgChat;
import com.example.lib.context.InteractiveMessageContext;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

public class Utils {
    private static final DecimalFormat bigNumberFormat = new DecimalFormat("#,###");
    private static final DecimalFormat regularNumberFormat = new DecimalFormat("#,###.00");
    private static final DecimalFormat partNumberFormat = new DecimalFormat("#.000");
    private static final DecimalFormat smallestNumberFormat = new DecimalFormat("#.0000000");

    public static Integer getIntFromPayload(InteractiveMessageContext context) {
        if (!context.getPayload().hasRemaining()) {
            throw new RuntimeException("No payload in the button");
        }

        return context.getPayload().getInt();
    }

    public static String getStringFromPayload(InteractiveMessageContext context, int len) {
        if (!context.getPayload().hasRemaining()) {
            throw new RuntimeException("No payload in the button");
        }

        final var buf = new byte[len];
        context.getPayload().get(buf);

        return new String(buf, StandardCharsets.UTF_8);
    }

    public static Integer parseIntNoThrow(String str) {
        try {
            return Integer.parseInt(str);
        } catch (Throwable t) {
            return null;
        }
    }

    public static InputHandler fuckUp(TgChat chat) {
        chat.sendLocalizedMessage("spy.generic.fuckUp", new ReplyKeyboardRemove());
        return null;
    }

    public static String tgMd2Format(String message) {
        if (message == null)
            return "";

        final var toEscape = List.of('_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|',
                '{', '}', '.', '!' );

        for (char ch : toEscape)
            message = message.replace(ch + "", "\\" + ch);

        return message;
    }

    /**
     * Optimizing number readability, e.g.:
     * 1032412.54512232 > 1032412
     *    4003.45512    > 4003.45
     *      32.551666   > 32.552
     *       0.03551255 > 0.035513
     * @param number number to optimize
     * @return readable number
     */
    public static String optimizeReadability(Double number) {
        final var absNumber = Math.abs(number);

        if (absNumber >= 10_000)
            return bigNumberFormat.format(number);

        if (absNumber >= 100)
            return regularNumberFormat.format(number);

        if (absNumber >= 1)
            return partNumberFormat.format(number);

        if (absNumber >= 0.001)
            return smallestNumberFormat.format(number);

        return "%f".formatted(number);
    }

    public static String dateToString(LocalDateTime ts) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(ts);
    }
}
