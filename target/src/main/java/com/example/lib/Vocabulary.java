package org.example.lib;


import java.util.Locale;
import java.util.ResourceBundle;

public final class Vocabulary {
    public static String bundleName = "locale";

    private final Locale locale;
    private final ResourceBundle rb;

    private Vocabulary(Locale locale) {
        this.locale = locale;
        rb = ResourceBundle.getBundle(bundleName, locale);
    }

    public Locale getLocale() {
        return locale;
    }

    public String get(String key) {
        return rb.getString(key);
    }

    public static String default_(String key) {
        return ResourceBundle.getBundle(bundleName, Locale.US).getString(key);
    }

    public static Vocabulary make(Locale locale) {
        return new Vocabulary(locale);
    }
}
