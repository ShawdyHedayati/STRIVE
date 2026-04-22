package com.strive.util;

import java.time.LocalDate;

public class DateUtils {
    private DateUtils() {} // prevent instantiation

    public static LocalDate startOfCurrentWeek() {
        LocalDate today = LocalDate.now();
        return today.minusDays(today.getDayOfWeek().getValue() - 1);
    }
}
