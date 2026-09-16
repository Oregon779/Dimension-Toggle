package net.dimensiontoggle.model;

public enum MessageDisplayType {
    CHAT,
    ACTIONBAR,
    BOSSBAR,
    TITLE,
    NONE;

    public static MessageDisplayType fromConfig(String value, MessageDisplayType fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return MessageDisplayType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
