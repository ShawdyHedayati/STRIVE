package com.cobra.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Registry of all valid spending categories in STRIVE.
 * Single source for category names and associated color.
 * No other class should hardcode category strings or color values.
 * So this is basically acting as a one stop shop reference.
 *
 * Categories map directly to the dropdown options in the UI and
 * are used through the BLL for grouping and chart data generation.
 */

public enum CategoryRegistry {
    HOUSING("Housing" , "#5DCAA5"),
    UTILITIES("Utilities", "#7F77DD"),
    TRANSPORTATION("Transportation", "#EF9F27"),
    FOOD("Food", "#D85A30"),
    HEALTH("Health", "#1D9E75"),
    FUN("Fun", "#D4537E"),
    PERSONAL("Personal", "#378ADD"),
    SUBSCRIPTIONS("Subscriptions", "#639922"),
    MISCELLANEOUS("Miscellaneous", "#888780");

    // readable name shown in UI dropdown and all displays
    private final String displayName;

    // hex color code used for pie chart slices and limit bar color coding
    private final String color;

    CategoryRegistry(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    //return display name for category
    public String displayName() { return displayName; }

    // return hex color assigned to category
    public String color(){ return color; }

    // return all cat as ordered list, match UI dropdown order
    public static List<CategoryRegistry> getALL() { return Arrays.asList(values()); }

    // look up category by display name, not case-sensitive
    public static Optional<CategoryRegistry> fromDisplayName(String name) {
        return Arrays.stream(values())
                .filter(c -> c.displayName.equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Returns {@code true} if the given name matches a registered category
     * Use by controllers to validate user input before applying commands.
     *
     * @param name category name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String name) { return fromDisplayName(name).isPresent(); }

    /**
     * Returns the hex color for given cat name
     * Falls back to misc gray if cat not found, rather than throw
     *
     * @param name category display name
     * @return hex color string
     */
    public static String colorFor(String name) {
        return fromDisplayName(name)
                .map(CategoryRegistry::color)
                .orElse("#888780"); // default gray if category not recognized
    }
}
