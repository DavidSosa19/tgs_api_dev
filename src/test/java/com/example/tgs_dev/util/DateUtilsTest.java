package com.example.tgs_dev.util;

import com.example.tgs_dev.entity.enums.ShiftDayType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DateUtils")
class DateUtilsTest {

    // 2024-01-15 = Monday … 2024-01-19 = Friday
    @ParameterizedTest(name = "{0} is a business day")
    @ValueSource(strings = {"2024-01-15","2024-01-16","2024-01-17","2024-01-18","2024-01-19"})
    @DisplayName("weekdays → BUSINESS_DAYS")
    void weekday_isBusinessDays(String date) {
        assertThat(DateUtils.getTypeofDay(LocalDate.parse(date)))
                .isEqualTo(ShiftDayType.BUSINESS_DAYS);
    }

    @Test @DisplayName("Saturday → HOLIDAYS")
    void saturday_isHoliday() {
        assertThat(DateUtils.getTypeofDay(LocalDate.of(2024, 1, 20)))
                .isEqualTo(ShiftDayType.HOLIDAYS);
    }

    @Test @DisplayName("Sunday → HOLIDAYS")
    void sunday_isHoliday() {
        assertThat(DateUtils.getTypeofDay(LocalDate.of(2024, 1, 21)))
                .isEqualTo(ShiftDayType.HOLIDAYS);
    }
}
