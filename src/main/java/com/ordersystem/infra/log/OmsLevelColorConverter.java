package com.ordersystem.infra.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

public class OmsLevelColorConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        return switch (event.getLevel().toInt()) {
            case ch.qos.logback.classic.Level.ERROR_INT -> "1;31";
            case ch.qos.logback.classic.Level.WARN_INT  -> "0;33";
            case ch.qos.logback.classic.Level.INFO_INT  -> "0;32";
            case ch.qos.logback.classic.Level.DEBUG_INT -> "0;36";
            default                                      -> "0";
        };
    }
}
