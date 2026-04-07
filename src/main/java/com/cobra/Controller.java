package com.cobra;

import com.cobra.types.Statement;

import java.util.ArrayDeque;

public class Controller {
	private final DBModel dbModel;
	private final ArrayDeque<Statement> actionQueue = new ArrayDeque<>();
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

	private void flushQueue() {
		while (!actionQueue.isEmpty()) {
			dbModel.ingestActions(actionQueue.poll());
		}
	}
}