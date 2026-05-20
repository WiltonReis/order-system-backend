package com.ordersystem.infra.log;

import ch.qos.logback.classic.spi.ILoggingEvent;

public class OmsMaskedColorConverter extends SensitiveMaskingConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String masked = super.convert(event);
        String color = masked.startsWith("[OMS]")
                ? OmsAnsiColors.ORANGE
                : OmsAnsiColors.forLevel(event.getLevel());
        return color + masked + OmsAnsiColors.RESET;
    }
}
