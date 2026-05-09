package com.strive.util;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Canonical registry of all valid spending categories in CoBRA.
 *
 * This enum is the single source of truth for category names and
 * their associated UI colors. No other class should hardcode category strings
 * or hex color values — all lookups and validations go through this registry.
 *
 * The enum ordinal (declaration order) defines the canonical sort order
 * used by {@link com.strive.bll.SpendingCalculator#pieChartData} when ordering
 * pie chart slices, so the order of the constants below matters and should not
 * be changed without considering chart rendering effects.
 *
 * Categories map directly to:
 *   The dropdown options presented in the UI when adding or editing a
 *       transaction or limit
 *   The {@code category} field stored on {@link com.strive.model.Transaction}
 *       and {@link com.strive.model.SpendingLimit} records
 *   The grouping keys used throughout the BLL for aggregation and chart
 *       data generation
 *
 * @see com.strive.bll.SpendingCalculator
 * @see com.strive.bll.LimitCalculator
 * @see com.strive.bll.ChartCalculator
 */

public enum CategoryRegistry {
    HOUSING("Housing" , "#5DCAA5"), // teal-green
    UTILITIES("Utilities", "#7F77DD"), // purple
    TRANSPORTATION("Transportation", "#EF9F27"), // amber
    FOOD("Food", "#D85A30"), // burnt orange
    HEALTH("Health", "#1D9E75"), // green
    FUN("Fun", "#D4537E"), // pink
    PERSONAL("Personal", "#378ADD"), // blue
    SUBSCRIPTIONS("Subscriptions", "#639922"), // olive
    MISCELLANEOUS("Miscellaneous", "#888780"); // grey — also used as the fallback color

    /** Human-readable name shown in the UI dropdown and all display contexts (e.g. {@code "Food"}). */
    private final String displayName;

    /**
     * Hex color code assigned to this category for pie chart slices and
     * limit bar color coding (e.g. {@code "#D85A30"}).
     */
    private final String color;

    /**
     * Constructs a category constant with its display name and hex color.
     *
     * @param displayName the human-readable category label shown in the UI
     * @param color       the hex color code for this category's chart and bar rendering
     */
    CategoryRegistry(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    /**
     * Returns the human-readable display name for this category.
     *
     * @return the display name (e.g. {@code "Transportation"}); never {@code null}
     */
    public String displayName() { return displayName; }

    /**
     * Returns the hex color code assigned to this category, used for pie chart
     * slices and limit bar color coding.
     *
     * @return a CSS hex colour string (e.g. {@code "#EF9F27"}); never {@code null}
     */
    public String color(){ return color; }

    /**
     * Returns all registered categories as an ordered list, preserving the
     * canonical declaration order defined above.
     *
     * The returned list matches the order categories appear in the UI
     * dropdown and the order pie chart slices are sorted.
     *
     * @return an unmodifiable-in-practice list of all {@link CategoryRegistry} values
     */
    public static List<CategoryRegistry> getAll() {
        return Arrays.asList(values()); // Arrays.asList preserves enum declaration order
    }

    /**
     * Looks up a category by its display name, case-insensitively.
     *
     * Returns an empty {@link Optional} if no category matches, rather than
     * throwing — callers can use {@link #isValid} first if they want a boolean check.
     *
     * @param name the display name to search for (e.g. {@code "food"} or {@code "Food"})
     * @return an {@link Optional} containing the matching category, or empty if not found
     */
    public static Optional<CategoryRegistry> fromDisplayName(String name) {
        return Arrays.stream(values())
                .filter(c -> c.displayName.equalsIgnoreCase(name)) // case-insensitive match
                .findFirst();
    }

    /**
     * Returns {@code true} if the given name matches a registered category
     * (case-insensitive).
     *
     * Used by controllers to validate user input before applying a command,
     * ensuring only known categories are ever stored in the database.
     *
     * @param name the category name to validate; {@code null} will return {@code false}
     * @return {@code true} if {@code name} matches a registered category display name
     */
    public static boolean isValid(String name) { return fromDisplayName(name).isPresent(); }

    /**
     * Returns the hex color code for the category with the given display name.
     *
     * Falls back to the {@link #MISCELLANEOUS} grey ({@code "#888780"}) if
     * the name is not recognized, rather than throwing. This ensures chart
     * rendering never fails due to an unrecognized category string.
     *
     * @param name the category display name to look up; case-insensitive
     * @return the hex color string for the category, or {@code "#888780"} if not found
     */
    public static String colorFor(String name) {
        return fromDisplayName(name)
                .map(CategoryRegistry::color)
                .orElse("#888780"); // fall back to MISCELLANEOUS grey — safe default for rendering
    }
}
