package com.ordersystem.infra.log;

import ch.qos.logback.classic.Level;

final class OmsAnsiColors {

    private static final char ESC = (char) 27;

    static final String RESET    = ESC + "[0m";
    static final String BOLD_RED = ESC + "[1;31m";
    static final String YELLOW   = ESC + "[0;33m";
    static final String GREEN    = ESC + "[0;32m";
    static final String CYAN     = ESC + "[0;36m";
    static final String ORANGE   = ESC + "[38;5;208m";

    private OmsAnsiColors() {}

    static String forLevel(Level level) {
        return switch (level.toInt()) {
            case Level.ERROR_INT -> BOLD_RED;
            case Level.WARN_INT  -> YELLOW;
            case Level.INFO_INT  -> GREEN;
            case Level.DEBUG_INT -> CYAN;
            default              -> "";
        };
    }
}
