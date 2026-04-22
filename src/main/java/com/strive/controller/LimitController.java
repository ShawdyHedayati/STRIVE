package com.strive.controller;

import com.strive.model.CategoryRegistry;
import com.strive.model.SpendingLimit;
import com.strive.session.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Handles all user action related to spending limits: adding, editing, deleting, and the weekly reset
 *
 * This controller enforces the business rule that only on limit per
 * category may exist at a time. Duplicate category validation is done
 * here rather than in the BLL because it guards the write path, not
 * a calculation
 */

public class LimitController {
    private final SessionManager session;

    /**
     * Constructs a LimitController backed by the shared session manager
     *
     * @param session the session manager shared across all controllers
     */
    public LimitController(SessionManager session) { this.session = session; }

    /**
     * Validates and adds a new spending limit to the session
     * Rejects the action if the category is invalid or a limit for that
     * category already exists (per the spec: one limit per category)
     *
     * @param category the category to limit, must be a valid CategoryRegistry entry
     * @param amount the weekly spending limit amount in dollars
     */
    public void addLimit(String category, double amount) {
        if (!CategoryRegistry.isValid(category)) {
            System.err.println("[LimitController] Invalid category: " + category);
            return;
        }

        // enforce on limit per category; spec requires an error if duplicate exists
        if (categoryLimitExists(category)) {
            System.err.println("[LimitController] A limit for " + category + " already exists. Please select that limit to edit.");
            return;
        }

        SpendingLimit limit = new SpendingLimit(
                session.nextId(), amount, category, LocalDate.now());
        session.apply(new AddCommand<>(limit));
        System.out.println("[LimitController] Added limit: " + limit);
    }

    /**
     * Validates and edits an existing spending limit in the session
     * Rejects if the new category is already taken by a different limit
     * or if the category name is not in {@link CategoryRegistry}
     *
     * @param id the ID of the limit to edit
     * @param newCategory the new category name
     * @param newAmount the new limit amount in dollars
     */
    public void editLimit(int id, String newCategory, double newAmount) {
        SpendingLimit previous = session.getLimits().stream()
                .filter(l -> l.id() == id)
                .findFirst()
                .orElse(null);

        if (previous == null) {
            System.err.println("[LimitController] Limit not found: " + id);
            return;
        }

        // only block duplicate if the cat is actually changing to a taken one
        if (!newCategory.equalsIgnoreCase(previous.category())
            && categoryLimitExists(newCategory)) {
            System.err.println("[LimitController] A limit for " + newCategory
                    + " already exists. Please select that limit to edit.");
            return;
        }

        // preserve the orgininal createdAt date; only category and amt change
        SpendingLimit updated = new SpendingLimit(id, newAmount, newCategory, previous.createdAt());
        session.apply(new EditCommand<>(previous, updated));
        System.out.println("[LimitController] Edited limit id=" + id);
    }

    /**
     * Deletes a spending limit from the session by id
     *
     * @param id the id of the limit to delete
     */
    public void deleteLimit(int id) {
        SpendingLimit target = session.getLimits().stream()
                .filter(l -> l.id() == id)
                .findFirst()
                .orElse(null);

        if (target == null) {
            System.err.println("[LimitController] Limit not found: " + id);
            return;
        }

        session.apply(new DeleteCommand<>(target));
        System.out.println("[LimitController] Deleted limit id=" + id);
    }

    /**
     * Performs the weekly reset by deleting all current spending limits
     * and flushing immediately to the db
     * Called at the beginning of ea week per the spec
     */
    public void weeklyReset() {
        // copy the list first to avoid ConcurrentModificationException while iter
        List<SpendingLimit> all = List.copyOf(session.getLimits());
        all.forEach(l -> session.apply(new DeleteCommand<>(l)));
        // flush immediately; the reset if not part of the normal save workflow
        session.flush();
        System.out.println("[LimitController] Weekly reset complete.");
    }

    /**
     * Returns all active spending limits from the session
     *
     * @return list of current spending limits
     */
    public List<SpendingLimit> getAllLimits() { return session.getLimits(); }

    /**
     * REturns {@code true} if a limit for the given category already exists in the curren session state
     * Not case sensitive
     *
     * @param category the category name to check
     * @return true if a limit for this category already exists
     */
    public boolean categoryLimitExists(String category) {
        return session.getLimits().stream().anyMatch(l -> l.category().equalsIgnoreCase(category));
    }

    /**
     * Returns the spending limit for the given category, if one exists
     *
     * @param category the category name to look up
     * @return optional containing the matching limit, or empty of none exists
     */
    public Optional<SpendingLimit> getLimitForCategory(String category) {
        return session.getLimits().stream().filter(l -> l.category().equalsIgnoreCase(category)).findFirst();
    }

    /**
     * Check for any active limits that were created before start of
     * current ISO week (mon). If yes, perform reset
     *
     * Should be called once on app start
     */
    public void checkAndApplyWeeklyReset() {
        // calc mon of current week as reset bound
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);

        boolean resetNeeded = session.getLimits().stream()
                .anyMatch(l -> l.createdAt().isBefore(startOfWeek));

        if (resetNeeded) {
            weeklyReset();
            System.out.println("[LimitController] Automatic weekly reset applied on startup.");
        } else {
            System.out.println("[LimitController] No weekly reset needed.");
        }
    }
}