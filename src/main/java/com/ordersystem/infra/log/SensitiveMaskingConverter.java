package com.ordersystem.infra.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

public class SensitiveMaskingConverter extends MessageConverter {

    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(password|passwd|token|secret|authorization|credential)[=:\\s]+\\S+",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String convert(ILoggingEvent event) {
        String msg = super.convert(event);
        return SENSITIVE.matcher(msg).replaceAll("$1=[MASKED]");
    }
}
