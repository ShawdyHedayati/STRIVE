package com.cobra;

import com.cobra.cache.CacheManager;
import com.cobra.types.Limit;
import com.cobra.types.Statement;
import com.cobra.types.Transaction;

import java.util.ArrayDeque;
import java.util.ArrayList;

public class Controller {
	private final DBModel dbModel;
	private final CacheManager cache;

	private DashboardView view;

	private final ArrayDeque<Statement> actionQueue = new ArrayDeque<>();

	private int nextId= 1;

	public Controller() {
		this.dbModel = DBModel.getInstance();
		this.cache = CacheManager.getInstance();
		updateCache();
	}

	public void setView(DashboardView view) {this.view = view;}

	// event listener

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

	// queue ->->-> model

	private void flushQueue() {
		while (!actionQueue.isEmpty()) {
			Statement stmt = actionQueue.poll();
			dbModel.ingestStatement(stmt);
		}
		cache.setTransactionList(new ArrayList<>(dbModel.fetchTransactions()));
		cache.setLimitList(new ArrayList<>(dbModel.fetchLimits()));
		notifyView();
	}

	public void updateCache() {
		cache.setTransactionList(dbModel.fetchTransactions());
		cache.setLimitList(dbModel.fetchLimits());
		System.out.println("[Controller] Cache updated - " + cache.getTransactionList().size() + " transactions, " + cache.getLimitList().size() + " limits");
	}

	private void notifyView() {
		if (view != null) {
			view.refresh(cache.getTransactionList(), cache.getLimitList());
		}
	}

	public ArrayList<Transaction> getTransactions() {
		return cache.getTransactionList();
	}

	public ArrayList<Limit> getLimits() { return cache.getLimitList();}
}