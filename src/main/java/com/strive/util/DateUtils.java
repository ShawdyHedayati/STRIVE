package com.strive.util;

import java.time.LocalDate;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Utility class providing date helper methods used throughout the BLL layer.
 *
 * All methods are static. This class cannot be instantiated.
 *
 * @see com.strive.bll.SpendingCalculator
 * @see com.strive.bll.LimitCalculator
 * @see com.strive.bll.ChartCalculator
 */

public class DateUtils {
    /** Prevent instantiation — this is a static utility class. */
    private DateUtils() {}

    /**
     * Returns the date of the Monday that starts the current calendar week.
     *
     * CoBRA defines a week as Monday–Sunday, consistent with ISO-8601.
     * All weekly spending calculations in the BLL layer use this value as
     * their lower date boundary.
     *
     * The calculation subtracts {@code (dayOfWeek - 1)} days from today,
     * where {@link java.time.DayOfWeek#getValue()} returns {@code 1} for Monday
     * through {@code 7} for Sunday. Examples:
     *
     *   If today is Wednesday (value = 3): subtract 2 days → Monday
     *   If today is Monday   (value = 1): subtract 0 days → Monday (today)
     *   If today is Sunday   (value = 7): subtract 6 days → Monday
     *
     * @return the {@link LocalDate} of Monday of the current week; never {@code null}
     */
    public static LocalDate startOfCurrentWeek() {
        LocalDate today = LocalDate.now();

        // DayOfWeek.getValue() → Monday=1, Tuesday=2, ... Sunday=7
        // Subtracting (value - 1) always lands on the preceding (or current) Monday
        return today.minusDays(today.getDayOfWeek().getValue() - 1);
    }
}
