package com.example.tgs_dev.util;

import com.example.tgs_dev.entity.enums.ShiftDayType;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class DateUtils {

    private DateUtils() {}

    public static ShiftDayType getTypeofDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
                ? ShiftDayType.HOLIDAYS
                : ShiftDayType.BUSINESS_DAYS;
    }
}