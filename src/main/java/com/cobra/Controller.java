package com.cobra;

import com.cobra.types.Statement;
import com.cobra.types.Transaction;

import java.util.ArrayDeque;

public class Controller {
	private final DBModel dbModel;
	private final ArrayDeque<Statement> actionQueue = new ArrayDeque<>();
	private final ArrayDeque<Statement> undoStack = new ArrayDeque<>();
	private int nextId;

	public Controller() {
		this.dbModel = DBModel.getInstance();
		this.nextId = dbModel.getNextID();
	}

	public void onInsertTransactionRequested(double amount, String category) {
		System.out.println("[Controller] INSERT requested: " + amount + " / " + category);

		Statement stmt = new Statement.Builder(
				Statement.QueryT.INSERT_Q,
				Statement.TableT.TRANSACTIONS_TB)
				.id(nextId++)
				.amount(amount)
				.category(category)
				.date(java.time.LocalDate.now().toString())
				.build();

		actionQueue.add(stmt);
		flushQueue();
	}

	public void onDeleteTransactionRequested(int id) {
		System.out.println("[Controller] DELETE requested for id: " + id);

		Statement stmt = new Statement.Builder(
				Statement.QueryT.DELETE_Q,
				Statement.TableT.TRANSACTIONS_TB)
				.id(id)
				.build();

		actionQueue.add(stmt);
		flushQueue();
	}

	public void onUndoRequested() {
		if (undoStack.isEmpty())
			return;
		dbModel.ingestActions(undoStack.pop());
	}

	private Statement inverse(Statement s) {
		return switch (s.getQueryType()) {
			case INSERT_Q -> new Statement.Builder(
					Statement.QueryT.DELETE_Q, s.getTableType())
					.id(s.getID())
					.build();
			case DELETE_Q -> new Statement.Builder(
					Statement.QueryT.INSERT_Q, s.getTableType())
					.id(s.getID())
					.amount(s.getAmount())
					.category(s.getCategory())
					.date(s.getDate())
					.build();
			case UPDATE_Q ->
				null;
		};
	}

	private void flushQueue() {
		while (!actionQueue.isEmpty()) {
			Statement stmt = actionQueue.poll();

			if (stmt.getQueryType() == Statement.QueryT.UPDATE_Q) {
				Transaction old = dbModel.getTransactions().stream()
						.filter(t -> t.id() == stmt.getID())
						.findFirst().orElse(null);
				if (old != null) {
					Statement rollback = new Statement.Builder(
							Statement.QueryT.UPDATE_Q, stmt.getTableType())
							.id(old.id())
							.amount(old.amount())
							.category(old.category())
							.date(old.date())
							.build();
					undoStack.push(rollback);
				}
			} else {
				undoStack.push(inverse(stmt));
			}
			dbModel.ingestActions(stmt);
		}
	}
}