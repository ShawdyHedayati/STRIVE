package com.strive.controller;

import com.strive.util.CategoryRegistry;
import com.strive.model.SpendingLimit;
import com.strive.session.*;
import com.strive.session.command.AddCommand;
import com.strive.session.command.DeleteCommand;
import com.strive.session.command.EditCommand;
import com.strive.util.DateUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Handles all user actions related to spending limits: adding, editing,
 * deleting, and the weekly reset.
 *
 * Permanent operations: All mutations in this controller route through
 * {@link SessionManager#applyPermanent(com.strive.session.command.Command)} rather
 * than {@link SessionManager#apply(com.strive.session.command.Command)}. This means
 * limit changes bypass the undo stack entirely and are never reversed by
 * {@link SessionManager#undo()} — this is intentional and by design.
 *
 * One limit per category: This controller enforces the business rule
 * that only one limit per category may exist at a time. Duplicate-category
 * validation is done here rather than in the BLL because it guards the write
 * path, not a calculation.
 *
 * Weekly reset: {@link #checkAndApplyWeeklyReset()} is called once
 * on application startup. If any active limit was created before the current
 * Monday, all limits are deleted (via {@link #weeklyReset()}) so the user
 * starts the new week with a clean slate.
 *
 * @see SessionManager#applyPermanent(com.strive.session.command.Command)
 * @see com.strive.config.AppContext
 */

public class LimitController {
    /** The shared session manager; all limit mutations route through this. */
    private final SessionManager session;

    /**
     * Constructs a {@code LimitController} backed by the shared session manager.
     *
     * @param session the session manager shared across all controllers;
     *                must not be {@code null}
     */
    public LimitController(SessionManager session) { this.session = session; }

    /**
     * Validates and adds a new spending limit to the session.
     *
     * The operation is rejected (logged to stderr, no state change) if:
     *
     *   The category is not a valid {@link CategoryRegistry} entry, or
     *   A limit for that category already exists in the current session
     *       (per the spec: one limit per category at a time).
     *
     * The new limit is assigned the next available ID from the session and
     * stamped with today's date, which is used by the weekly reset logic to
     * detect stale limits from prior weeks.
     *
     * Routes through {@link SessionManager#applyPermanent} — this change
     * is not undoable.
     *
     * @param category the category to limit; must be a valid {@link CategoryRegistry}
     *                 display name (case-insensitive)
     * @param amount   the weekly spending cap in dollars; should be &gt; 0
     */
    public void addLimit(String category, double amount) {
        // Validate category against the registry before doing anything else
        if (!CategoryRegistry.isValid(category)) {
            System.err.println("[LimitController] Invalid category: " + category);
            return;
        }

        // Enforce one-limit-per-category rule; reject if a limit already exists for this category
        if (categoryLimitExists(category)) {
            System.err.println("[LimitController] A limit for " + category + " already exists. Please select that limit to edit.");
            return;
        }

        // Build the new limit record with the next available ID and today's creation date
        SpendingLimit limit = new SpendingLimit(
                session.nextId(), // globally unique ID across transactions and limits
                amount, category, LocalDate.now()); // creation date used by weekly reset to detect stale limits

        session.applyPermanent(new AddCommand<>(limit)); // permanent — not undoable
        System.out.println("[LimitController] Added limit: " + limit);
    }

    /**
     * Validates and replaces an existing spending limit with updated values.
     *
     * The operation is rejected (logged to stderr, no state change) if:
     * No limit with the given {@code id} exists in the current session, or
     *   The new category is different from the existing one and a limit
     *   for that new category already exists (would violate the one-per-category rule).
     *
     * The original {@code createdAt} date is preserved on the updated record —
     * only the category and amount change. This prevents a mid-week edit from
     * accidentally marking the limit as "new" and deferring the weekly reset.
     *
     * Routes through {@link SessionManager#applyPermanent} — this change
     * is not undoable.
     *
     * @param id          the primary key of the limit to edit
     * @param newCategory the replacement category name; must be a valid
     *                    {@link CategoryRegistry} entry (case-insensitive)
     * @param newAmount   the replacement weekly spending cap in dollars
     */
    public void editLimit(int id, String newCategory, double newAmount) {
        // Locate the existing limit — needed for both validation and as the EditCommand's "previous"
        SpendingLimit previous = session.getLimits().stream()
                .filter(l -> l.id() == id)
                .findFirst()
                .orElse(null);

        if (previous == null) {
            System.err.println("[LimitController] Limit not found: " + id);
            return;
        }

        // Only block a duplicate-category edit if the category is actually changing to one
        // already taken by a different limit; editing in place (same category) is always allowed
        if (!newCategory.equalsIgnoreCase(previous.category())
            && categoryLimitExists(newCategory)) {
            System.err.println("[LimitController] A limit for " + newCategory
                    + " already exists. Please select that limit to edit.");
            return;
        }

        // Preserve the original createdAt date — only category and amount are user-editable
        SpendingLimit updated = new SpendingLimit(id, newAmount, newCategory, previous.createdAt());
        session.applyPermanent(new EditCommand<>(previous, updated)); // permanent — not undoable
        System.out.println("[LimitController] Edited limit id=" + id);
    }

    /**
     * Removes a spending limit from the session by its primary key.
     *
     * If no limit with the given {@code id} exists, the operation is a
     * no-op (logged to stderr, no state change).
     *
     * Routes through {@link SessionManager#applyPermanent} — this change
     * is not undoable.
     *
     * @param id the primary key of the limit to delete
     */
    public void deleteLimit(int id) {
        // Retrieve the full record first — DeleteCommand needs it to restore on undo (if ever used)
        SpendingLimit target = session.getLimits().stream()
                .filter(l -> l.id() == id)
                .findFirst()
                .orElse(null);

        if (target == null) {
            System.err.println("[LimitController] Limit not found: " + id);
            return;
        }

        session.applyPermanent(new DeleteCommand<>(target)); // permanent — not undoable
        System.out.println("[LimitController] Deleted limit id=" + id);
    }

    /**
     * Deletes all active spending limits from the session, clearing the slate
     * for the new week.
     *
     * Each limit is removed via a separate {@link DeleteCommand} routed
     * through {@link SessionManager#applyPermanent}, so all deletions are
     * captured in the permanent log and will be persisted on the next
     * {@link SessionManager#flush()} call.
     *
     * This method does not flush to the database itself — the caller
     * ({@link #checkAndApplyWeeklyReset()} or the UI save flow) is responsible
     * for persisting the reset.
     */
    public void weeklyReset() {
        // Snapshot the list before iterating — applyPermanent mutates the live list
        List<SpendingLimit> all = List.copyOf(session.getLimits());

        // Delete each limit individually so each removal is logged in the permanent command log
        all.forEach(l -> session.applyPermanent(new DeleteCommand<>(l)));

        System.out.println("[LimitController] Weekly reset complete.");
    }

    /**
     * Returns all active spending limits from the current session state.
     *
     * @return the live list of current spending limits; never {@code null}
     */
    public List<SpendingLimit> getAllLimits() { return session.getLimits(); }

    /**
     * Returns {@code true} if a limit for the given category already exists
     * in the current session state (case-insensitive).
     *
     * Used internally to enforce the one-limit-per-category rule during
     * {@link #addLimit} and {@link #editLimit}.
     *
     * @param category the category name to check; case-insensitive
     * @return {@code true} if a limit with a matching category exists
     */
    public boolean categoryLimitExists(String category) {
        return session.getLimits().stream().anyMatch(l -> l.category().equalsIgnoreCase(category));
    }

    /**
     * Returns the spending limit for the given category, if one exists.
     *
     * @param category the category display name to look up; case-insensitive
     * @return an {@link Optional} containing the matching limit, or empty if
     *         no limit exists for that category
     */
    public Optional<SpendingLimit> getLimitForCategory(String category) {
        return session.getLimits().stream().filter(l -> l.category().equalsIgnoreCase(category)).findFirst();
    }

    /**
     * Checks whether a weekly reset is needed on application startup and
     * applies it automatically if so.
     *
     * A reset is needed if any active limit has a {@code createdAt} date
     * that falls before the Monday of the current week — meaning it was created
     * in a prior week and is now stale. If the check finds at least one stale
     * limit, all limits are deleted via {@link #weeklyReset()}.
     *
     * Should be called exactly once, during {@link com.strive.STRIVEApp#start},
     * before the UI is shown to the user.
     */
    public void checkAndApplyWeeklyReset() {
        LocalDate startOfWeek = DateUtils.startOfCurrentWeek(); // Monday of the current week

        // A reset is needed if any limit was created before the current Monday
        boolean resetNeeded = session.getLimits().stream()
                .anyMatch(l -> l.createdAt().isBefore(startOfWeek));

        if (resetNeeded) {
            weeklyReset(); // clear all stale limits from the prior week
            System.out.println("[LimitController] Automatic weekly reset applied on startup.");
        } else {
            System.out.println("[LimitController] No weekly reset needed.");
        }
    }
}