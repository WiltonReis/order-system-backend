package com.ordersystem.infra.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;

import java.io.IOException;
import java.util.regex.Pattern;

public class MaskingMessageJsonProvider extends MessageJsonProvider {

    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(password|passwd|token|secret|authorization|credential)[=:\\s]+\\S+"
    );

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        String masked = SENSITIVE.matcher(event.getFormattedMessage()).replaceAll("$1=[MASKED]");
        generator.writeStringField(getFieldName(), masked);
    }
}
