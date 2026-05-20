package com.ordersystem.infra.log;

import ch.qos.logback.classic.pattern.LoggerConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class OmsLoggerColorConverter extends LoggerConverter {

    private static final char ESC = (char) 27;
    private static final String BOLD_WHITE = ESC + "[1;37m";
    private static final String DARK_GRAY  = ESC + "[0;90m";
    private static final String RESET      = ESC + "[0m";

    @Override
    public String convert(ILoggingEvent event) {
        String abbreviated = super.convert(event);
        String color = event.getLoggerName().startsWith("com.ordersystem") ? BOLD_WHITE : DARK_GRAY;
        return color + abbreviated + RESET;
    }
}
