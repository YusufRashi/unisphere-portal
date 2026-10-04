package com.unisphere.offering;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;

/** Stores {@link DayOfWeek} as ISO-8601 numbers (1 = Monday … 7 = Sunday). */
@Converter
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Integer> {

    @Override
    public Integer convertToDatabaseColumn(DayOfWeek day) {
        return day == null ? null : day.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Integer value) {
        return value == null ? null : DayOfWeek.of(value);
    }
}
